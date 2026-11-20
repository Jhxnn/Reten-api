package com.Reaten.auth.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.Reaten.config.RetenProperties;

/**
 * Envio assíncrono: a resposta HTTP não espera o SMTP, o que também evita que o tempo de resposta
 * revele se o e-mail existe.
 */
@Component
class SmtpPasswordResetMailer implements PasswordResetMailer {

	private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);

	private final JavaMailSender mailSender;
	private final String from;

	SmtpPasswordResetMailer(JavaMailSender mailSender, RetenProperties properties) {
		this.mailSender = mailSender;
		this.from = properties.mail().from();
	}

	@Async
	@Override
	public void sendResetLink(String toEmail, String recipientName, String resetUrl) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(toEmail);
		message.setSubject("Redefinir sua senha do Reten");
		message.setText("""
				Olá, %s.

				Recebemos um pedido para redefinir a senha da sua conta no Reten.
				Para criar uma nova senha, abra o link abaixo. Ele vale por 30 minutos e pode ser usado uma vez.

				%s

				Se você não fez esse pedido, ignore este e-mail. Sua senha continua a mesma.

				Reten
				""".formatted(recipientName, resetUrl));
		try {
			mailSender.send(message);
		} catch (MailException ex) {
			log.error("Falha ao enviar e-mail de redefinição de senha", ex);
		}
	}
}
