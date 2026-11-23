package com.Reaten.auth.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos HTTP do módulo de autenticação. */
public final class AuthDtos {

	private AuthDtos() {
	}

	public record RegisterRequest(
			@NotBlank(message = "Informe seu nome.")
			@Size(max = 80, message = "Use no máximo 80 caracteres.")
			String name,

			@NotBlank(message = "Informe seu e-mail.")
			@Email(message = "Este e-mail não parece válido.")
			@Size(max = 254)
			String email,

			@NotBlank(message = "Crie uma senha.")
			@StrongPassword
			String password) {
	}

	/** @param rememberMe "manter conectado": a sessão sobrevive ao fechamento do navegador (ausente = não). */
	public record LoginRequest(
			@NotBlank(message = "Informe seu e-mail.") String email,
			@NotBlank(message = "Informe sua senha.") String password,
			Boolean rememberMe) {

		public boolean remember() {
			return Boolean.TRUE.equals(rememberMe);
		}
	}

	public record ForgotPasswordRequest(
			@NotBlank(message = "Informe seu e-mail.")
			@Email(message = "Este e-mail não parece válido.")
			String email) {
	}

	public record ResetPasswordRequest(
			@NotBlank String token,

			@NotBlank(message = "Crie uma nova senha.")
			@StrongPassword
			String password) {
	}

	public record VerifyEmailRequest(
			@NotBlank(message = "Digite o código.")
			@Pattern(regexp = "\\s*\\d{6}\\s*", message = "O código tem 6 números.")
			String code) {
	}

	public record ChangeEmailRequest(
			@NotBlank(message = "Informe seu e-mail.")
			@Email(message = "Este e-mail não parece válido.")
			@Size(max = 254)
			String email) {
	}

	/** @param emailVerified falso até o código enviado por e-mail ser digitado; o app fica bloqueado enquanto isso */
	public record CurrentUserResponse(UUID id, String name, String email, boolean emailVerified, boolean admin) {
	}
}
