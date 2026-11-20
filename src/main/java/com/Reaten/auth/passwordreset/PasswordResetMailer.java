package com.Reaten.auth.passwordreset;

/** Porta de envio do e-mail de redefinição. A implementação padrão usa SMTP. */
public interface PasswordResetMailer {

	void sendResetLink(String toEmail, String recipientName, String resetUrl);
}
