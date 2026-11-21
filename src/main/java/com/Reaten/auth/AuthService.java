package com.Reaten.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.auth.dto.AuthDtos.CurrentUserResponse;
import com.Reaten.auth.dto.AuthDtos.LoginRequest;
import com.Reaten.auth.dto.AuthDtos.RegisterRequest;
import com.Reaten.auth.emailverification.EmailVerificationService;
import com.Reaten.shared.exception.ApiException;
import com.Reaten.user.Emails;
import com.Reaten.user.User;
import com.Reaten.user.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final SessionAuthenticator sessionAuthenticator;
	private final EmailVerificationService emailVerification;

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
			SessionAuthenticator sessionAuthenticator, EmailVerificationService emailVerification) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.sessionAuthenticator = sessionAuthenticator;
		this.emailVerification = emailVerification;
	}

	/**
	 * Cria a conta, envia o código de confirmação e já inicia a sessão — o usuário não precisa entrar
	 * de novo logo após se cadastrar. Até confirmar o e-mail, o app fica bloqueado.
	 */
	@Transactional
	public CurrentUserResponse register(RegisterRequest request, HttpServletRequest http, HttpServletResponse response) {
		String email = Emails.normalize(request.email());
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("email_taken", "Já existe uma conta com este e-mail. Tente entrar.");
		}
		User user = users.saveAndFlush(new User(request.name().strip(), email, passwordEncoder.encode(request.password())));
		log.info("Nova conta criada: {}", user.getId());
		emailVerification.sendInitialCode(user);
		return login(new LoginRequest(email, request.password()), http, response);
	}

	public CurrentUserResponse login(LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(Emails.normalize(request.email()), request.password()));
		} catch (AuthenticationException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "E-mail ou senha incorretos.");
		}
		sessionAuthenticator.establish(authentication, http, response);
		return toResponse((AuthenticatedUser) authentication.getPrincipal());
	}

	/** Lido do banco: nome, e-mail e confirmação mudam depois que a sessão começou. */
	@Transactional(readOnly = true)
	public CurrentUserResponse toResponse(AuthenticatedUser principal) {
		return users.findById(principal.id())
				.map(AuthService::toResponse)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Entre na sua conta para continuar."));
	}

	public CurrentUserResponse verifyEmail(AuthenticatedUser principal, String code) {
		return toResponse(emailVerification.verify(principal.id(), code));
	}

	public void resendVerificationCode(AuthenticatedUser principal) {
		emailVerification.resend(principal.id());
	}

	/**
	 * Troca o e-mail ainda não confirmado. O principal da sessão é refeito, porque o e-mail é o nome
	 * pelo qual as sessões da conta são encontradas (ex.: para encerrá-las ao redefinir a senha).
	 */
	public CurrentUserResponse changeEmail(AuthenticatedUser principal, String email, HttpServletRequest http,
			HttpServletResponse response) {
		User user = emailVerification.changeEmail(principal.id(), email);
		AuthenticatedUser updated = new AuthenticatedUser(user);
		updated.eraseCredentials();
		sessionAuthenticator.establish(
				UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()), http, response);
		return toResponse(user);
	}

	private static CurrentUserResponse toResponse(User user) {
		return new CurrentUserResponse(user.getId(), user.getName(), user.getEmail(), user.isEmailVerified());
	}
}
