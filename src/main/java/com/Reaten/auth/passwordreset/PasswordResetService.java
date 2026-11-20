package com.Reaten.auth.passwordreset;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.Reaten.config.RetenProperties;
import com.Reaten.shared.exception.ApiException;
import com.Reaten.shared.security.Hashing;
import com.Reaten.user.Emails;
import com.Reaten.user.User;
import com.Reaten.user.UserRepository;

@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private final UserRepository users;
	private final PasswordResetTokenRepository tokens;
	private final PasswordResetMailer mailer;
	private final PasswordEncoder passwordEncoder;
	private final FindByIndexNameSessionRepository<? extends Session> sessions;
	private final RetenProperties properties;

	PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordResetMailer mailer,
			PasswordEncoder passwordEncoder, FindByIndexNameSessionRepository<? extends Session> sessions,
			RetenProperties properties) {
		this.users = users;
		this.tokens = tokens;
		this.mailer = mailer;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.properties = properties;
	}

	/** Sempre termina sem erro, exista ou não a conta — a resposta não revela quais e-mails têm cadastro. */
	@Transactional
	public void requestReset(String email) {
		users.findByEmail(Emails.normalize(email)).ifPresent(user -> {
			Instant now = Instant.now();
			tokens.invalidatePending(user.getId(), now);
			String token = Hashing.randomUrlToken();
			tokens.save(new PasswordResetToken(user.getId(), Hashing.sha256Hex(token),
					now.plus(properties.passwordReset().ttl())));
			mailer.sendResetLink(user.getEmail(), user.getName(), resetUrl(token));
			log.info("Link de redefinição de senha gerado para o usuário {}", user.getId());
		});
	}

	/** Troca a senha e encerra todas as sessões abertas da conta. */
	@Transactional
	public void resetPassword(String token, String newPassword) {
		Instant now = Instant.now();
		PasswordResetToken resetToken = tokens.findByTokenHash(Hashing.sha256Hex(token))
				.filter(t -> t.isUsable(now))
				.orElseThrow(() -> ApiException.badRequest("invalid_reset_token",
						"Este link de redefinição expirou ou já foi usado. Peça um novo."));

		User user = users.findById(resetToken.getUserId())
				.orElseThrow(() -> ApiException.badRequest("invalid_reset_token", "Conta não encontrada."));
		user.changePassword(passwordEncoder.encode(newPassword));
		resetToken.markUsed(now);
		tokens.invalidatePending(user.getId(), now);

		sessions.findByPrincipalName(user.getEmail()).keySet().forEach(sessions::deleteById);
		log.info("Senha redefinida para o usuário {}; sessões abertas encerradas", user.getId());
	}

	private String resetUrl(String token) {
		return UriComponentsBuilder.fromUriString(properties.frontendUrl())
				.path("/redefinir-senha")
				.queryParam("token", token)
				.build()
				.toUriString();
	}
}
