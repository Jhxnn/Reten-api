package com.Reaten.auth.emailverification;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.config.RetenProperties;
import com.Reaten.shared.exception.ApiException;
import com.Reaten.shared.security.Hashing;
import com.Reaten.user.Emails;
import com.Reaten.user.User;
import com.Reaten.user.UserRepository;

/**
 * Confirmação de e-mail por código de 6 dígitos.
 * <ul>
 * <li>Só o hash do código é guardado, junto com o id da conta (o mesmo código em contas diferentes gera hashes diferentes).</li>
 * <li>Vale por pouco tempo e aceita poucas tentativas: 6 dígitos não resistem a força bruta sem esses limites.</li>
 * <li>Reenvio com espera mínima e teto por hora, para o endereço não virar alvo de spam.</li>
 * </ul>
 */
@Service
public class EmailVerificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository users;
	private final EmailVerificationCodeRepository codes;
	private final EmailVerificationMailer mailer;
	private final RetenProperties.EmailVerification policy;

	EmailVerificationService(UserRepository users, EmailVerificationCodeRepository codes, EmailVerificationMailer mailer,
			RetenProperties properties) {
		this.users = users;
		this.codes = codes;
		this.mailer = mailer;
		this.policy = properties.emailVerification();
	}

	/** Envia o primeiro código, logo após o cadastro. */
	@Transactional
	public void sendInitialCode(User user) {
		issue(user, Instant.now());
	}

	@Transactional
	public void resend(UUID userId) {
		User user = user(userId);
		if (user.isEmailVerified()) {
			throw ApiException.conflict("email_already_verified", "Seu e-mail já está confirmado.");
		}
		Instant now = Instant.now();
		ensureCanSend(userId, now);
		issue(user, now);
	}

	/**
	 * Confere o código. Tentativas erradas ficam gravadas mesmo com o erro devolvido ao cliente,
	 * por isso a transação não é desfeita por {@link ApiException}.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public User verify(UUID userId, String code) {
		User user = user(userId);
		if (user.isEmailVerified()) {
			return user;
		}
		Instant now = Instant.now();
		EmailVerificationCode current = codes.findFirstByUserIdOrderByCreatedAtDesc(userId)
				.filter(c -> c.isUsable(now, policy.maxAttempts()) && c.getEmail().equals(user.getEmail()))
				.orElseThrow(() -> ApiException.badRequest("verification_code_expired",
						"Este código não vale mais. Peça um novo."));

		if (!Hashing.constantTimeEquals(current.getCodeHash(), hash(userId, code.strip()))) {
			int left = current.registerFailedAttempt(now, policy.maxAttempts());
			throw left == 0
					? ApiException.badRequest("verification_code_expired", "Código incorreto. Peça um novo código.")
					: ApiException.badRequest("invalid_verification_code", "Código incorreto. "
							+ (left == 1 ? "Resta 1 tentativa." : "Restam " + left + " tentativas."));
		}
		current.markUsed(now);
		user.markEmailVerified(now);
		log.info("E-mail confirmado para o usuário {}", userId);
		mailer.sendWelcome(user.getEmail(), user.getName());
		return user;
	}

	/** Troca um e-mail ainda não confirmado (ex.: digitado errado no cadastro) e envia um código para o novo. */
	@Transactional
	public User changeEmail(UUID userId, String email) {
		User user = user(userId);
		if (user.isEmailVerified()) {
			throw ApiException.conflict("email_already_verified", "Seu e-mail já está confirmado.");
		}
		String normalized = Emails.normalize(email);
		if (!normalized.equals(user.getEmail()) && users.existsByEmail(normalized)) {
			throw ApiException.conflict("email_taken", "Já existe uma conta com este e-mail.");
		}
		Instant now = Instant.now();
		ensureUnderHourlyLimit(userId, now);
		user.changeEmail(normalized);
		users.flush();
		issue(user, now);
		return user;
	}

	/**
	 * Confirmação feita pelo administrador, sem código: o leitor passa a usar o app na próxima requisição
	 * (o filtro consulta o banco enquanto o e-mail está pendente). Os códigos ainda abertos deixam de valer.
	 *
	 * @return false se o e-mail já estava confirmado
	 */
	@Transactional
	public boolean confirmByAdmin(UUID userId, UUID adminId) {
		User user = users.findById(userId)
				.orElseThrow(() -> ApiException.notFound("user_not_found", "Usuário não encontrado."));
		if (user.isEmailVerified()) {
			return false;
		}
		Instant now = Instant.now();
		codes.invalidatePending(userId, now);
		user.markEmailVerified(now);
		log.info("E-mail do usuário {} confirmado pelo administrador {}", userId, adminId);
		mailer.sendWelcome(user.getEmail(), user.getName());
		return true;
	}

	public boolean isVerified(UUID userId) {
		return users.existsByIdAndEmailVerifiedAtIsNotNull(userId);
	}

	private void issue(User user, Instant now) {
		codes.invalidatePending(user.getId(), now);
		String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
		codes.save(new EmailVerificationCode(user.getId(), user.getEmail(), hash(user.getId(), code),
				now.plus(policy.codeTtl()), now));
		mailer.sendCode(user.getEmail(), user.getName(), code);
		log.info("Código de confirmação de e-mail enviado para o usuário {}", user.getId());
	}

	private void ensureCanSend(UUID userId, Instant now) {
		codes.findFirstByUserIdOrderByCreatedAtDesc(userId).ifPresent(last -> {
			Instant availableAt = last.getCreatedAt().plus(policy.resendAfter());
			if (now.isBefore(availableAt)) {
				long seconds = Math.max(1, Duration.between(now, availableAt).toSeconds());
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "verification_resend_too_soon",
						"Aguarde " + seconds + " segundos para pedir outro código.");
			}
		});
		ensureUnderHourlyLimit(userId, now);
	}

	private void ensureUnderHourlyLimit(UUID userId, Instant now) {
		if (codes.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1))) >= policy.maxPerHour()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "verification_resend_limit",
					"Você pediu muitos códigos na última hora. Tente de novo mais tarde.");
		}
	}

	private User user(UUID userId) {
		return users.findById(userId)
				.orElseThrow(() -> ApiException.notFound("user_not_found", "Conta não encontrada."));
	}

	private static String hash(UUID userId, String code) {
		return Hashing.sha256Hex(userId + ":" + code);
	}
}
