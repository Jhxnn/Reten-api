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

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
			SessionAuthenticator sessionAuthenticator) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.sessionAuthenticator = sessionAuthenticator;
	}

	/** Cria a conta e já inicia a sessão — o usuário não precisa entrar de novo logo após se cadastrar. */
	@Transactional
	public CurrentUserResponse register(RegisterRequest request, HttpServletRequest http, HttpServletResponse response) {
		String email = Emails.normalize(request.email());
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("email_taken", "Já existe uma conta com este e-mail. Tente entrar.");
		}
		User user = users.saveAndFlush(new User(request.name().strip(), email, passwordEncoder.encode(request.password())));
		log.info("Nova conta criada: {}", user.getId());
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

	public CurrentUserResponse toResponse(AuthenticatedUser user) {
		return new CurrentUserResponse(user.id(), user.name(), user.email());
	}
}
