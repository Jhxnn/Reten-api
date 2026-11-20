package com.Reaten.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configurações gerais do Reten, vindas de variáveis de ambiente (ver application.yaml).
 *
 * @param frontendUrl  origem pública do frontend; usada em links de e-mail e redirecionamentos do OAuth
 * @param security     segredos de criptografia
 * @param passwordReset política de recuperação de senha
 * @param mail         remetente dos e-mails transacionais
 */
@Validated
@ConfigurationProperties(prefix = "reten")
public record RetenProperties(
		@NotBlank String frontendUrl,
		Security security,
		PasswordReset passwordReset,
		Mail mail) {

	/** @param tokenEncryptionKey chave AES-256 em Base64 (32 bytes) usada para cifrar tokens de terceiros */
	public record Security(@NotBlank String tokenEncryptionKey) {
	}

	public record PasswordReset(@DefaultValue("30m") Duration ttl) {
	}

	public record Mail(@DefaultValue("Reten <nao-responda@reten.local>") String from) {
	}
}
