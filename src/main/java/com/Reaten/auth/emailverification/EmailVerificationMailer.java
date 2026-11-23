package com.Reaten.auth.emailverification;

/** Porta de envio dos e-mails da confirmação de conta. A implementação padrão usa SMTP. */
public interface EmailVerificationMailer {

	void sendCode(String toEmail, String recipientName, String code);

	/** Boas-vindas: a conta foi confirmada (pelo código ou por um administrador) e está pronta para uso. */
	void sendWelcome(String toEmail, String recipientName);
}
