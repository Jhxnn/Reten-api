package com.Reaten.auth.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.Reaten.config.RetenProperties;
import com.Reaten.shared.mail.BrandedMailSender;
import com.Reaten.shared.mail.BrandedMailSender.Button;
import com.Reaten.shared.mail.BrandedMailSender.Message;

/**
 * Envio assíncrono: a resposta HTTP não espera o SMTP, o que também evita que o tempo de resposta
 * revele se o e-mail existe.
 */
@Component
class SmtpPasswordResetMailer implements PasswordResetMailer {

	private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);

	private final BrandedMailSender mail;
	private final long minutes;

	SmtpPasswordResetMailer(BrandedMailSender mail, RetenProperties properties) {
		this.mail = mail;
		this.minutes = properties.passwordReset().ttl().toMinutes();
	}

	@Async
	@Override
	public void sendResetLink(String toEmail, String recipientName, String resetUrl) {
		try {
			mail.send(new Message(
					toEmail,
					"Redefinir sua senha do Reten",
					"Use o link para criar uma nova senha.",
					recipientName,
					"Recebemos um pedido para redefinir a senha da sua conta no Reten. Para criar uma nova senha, use o botão abaixo.",
					new Button("Criar nova senha", resetUrl),
					"O link vale por " + minutes + " minutos e pode ser usado uma vez.",
					"Se você não fez esse pedido, ignore este e-mail. Sua senha continua a mesma."));
		} catch (MailException ex) {
			log.error("Falha ao enviar e-mail de redefinição de senha: {}", ex.getMessage());
		}
	}
}
