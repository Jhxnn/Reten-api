package com.Reaten.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configurações gerais do Resten, vindas de variáveis de ambiente (ver application.yaml).
 *
 * @param frontendUrl  origem pública do frontend; usada em links de e-mail e redirecionamentos do OAuth
 * @param security     segredos de criptografia
 * @param passwordReset política de recuperação de senha
 * @param mail         remetente dos e-mails transacionais
 * @param admin        conta de administrador criada ao subir a aplicação
 */
@Validated
@ConfigurationProperties(prefix = "reten")
public record RetenProperties(
		@NotBlank String frontendUrl,
		Security security,
		PasswordReset passwordReset,
		Mail mail,
		EmailVerification emailVerification,
		Admin admin) {

	/**
	 * @param required    exige o código antes de liberar o app (desligado só nos testes que não tratam disso)
	 * @param codeTtl     validade de cada código
	 * @param maxAttempts tentativas erradas antes de o código ser invalidado
	 * @param resendAfter espera mínima entre dois envios
	 * @param maxPerHour  envios por conta em uma hora
	 */
	public record EmailVerification(
			@DefaultValue("true") boolean required,
			@DefaultValue("15m") Duration codeTtl,
			@DefaultValue("5") int maxAttempts,
			@DefaultValue("60s") Duration resendAfter,
			@DefaultValue("5") int maxPerHour) {
	}

	/**
	 * Conta de administrador garantida a cada inicialização. Sem e-mail ou senha, nenhuma conta é criada.
	 *
	 * @param email    e-mail de login do administrador
	 * @param password senha usada só na criação; trocar aqui não altera uma conta que já existe
	 */
	public record Admin(String email, String password) {

		public boolean configured() {
			return email != null && !email.isBlank() && password != null && !password.isBlank();
		}
	}

	/** @param tokenEncryptionKey chave AES-256 em Base64 (32 bytes) usada para cifrar tokens de terceiros */
	public record Security(@NotBlank String tokenEncryptionKey) {
	}

	public record PasswordReset(@DefaultValue("30m") Duration ttl) {
	}

	public record Mail(@DefaultValue("Resten <nao-responda@reten.local>") String from) {
	}
}
