package com.Reaten.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.Reaten.auth.dto.AuthDtos.ChangeEmailRequest;
import com.Reaten.auth.dto.AuthDtos.CurrentUserResponse;
import com.Reaten.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.Reaten.auth.dto.AuthDtos.LoginRequest;
import com.Reaten.auth.dto.AuthDtos.RegisterRequest;
import com.Reaten.auth.dto.AuthDtos.ResetPasswordRequest;
import com.Reaten.auth.dto.AuthDtos.VerifyEmailRequest;
import com.Reaten.auth.passwordreset.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Autenticação por sessão.
 * <pre>
 * GET  /api/auth/csrf              garante o cookie XSRF-TOKEN (204)
 * POST /api/auth/register          cria a conta e inicia a sessão (201)
 * POST /api/auth/login             inicia a sessão (200)
 * POST /api/auth/logout            encerra a sessão (204) — tratado pelo Spring Security
 * GET  /api/auth/me                usuário da sessão (200) ou 401
 * POST /api/auth/password/forgot   envia link de redefinição, sempre 202
 * POST /api/auth/password/reset    redefine a senha com o token do e-mail (204)
 * POST /api/auth/email/verify      confirma o e-mail com o código de 6 dígitos (200)
 * POST /api/auth/email/resend      envia um novo código (202; 429 se pedir cedo demais)
 * PUT  /api/auth/email             troca o e-mail ainda não confirmado e envia um código para ele (200)
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final AuthService authService;
	private final PasswordResetService passwordResetService;

	AuthController(AuthService authService, PasswordResetService passwordResetService) {
		this.authService = authService;
		this.passwordResetService = passwordResetService;
	}

	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token) {
		// Ler o token força a geração do cookie XSRF-TOKEN.
		token.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	CurrentUserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http,
			HttpServletResponse response) {
		return authService.register(request, http, response);
	}

	@PostMapping("/login")
	CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
			HttpServletResponse response) {
		return authService.login(request, http, response);
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return authService.toResponse(user);
	}

	@PostMapping("/email/verify")
	CurrentUserResponse verifyEmail(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody VerifyEmailRequest request) {
		return authService.verifyEmail(user, request.code());
	}

	@PostMapping("/email/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void resendVerificationCode(@AuthenticationPrincipal AuthenticatedUser user) {
		authService.resendVerificationCode(user);
	}

	@PutMapping("/email")
	CurrentUserResponse changeEmail(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ChangeEmailRequest request,
			HttpServletRequest http, HttpServletResponse response) {
		return authService.changeEmail(user, request.email(), http, response);
	}

	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		passwordResetService.requestReset(request.email());
	}

	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request.token(), request.password());
	}
}
