package com.Reaten.auth.emailverification;

/** Porta de envio do código de confirmação. A implementação padrão usa SMTP. */
public interface EmailVerificationMailer {

	void sendCode(String toEmail, String recipientName, String code);
}
