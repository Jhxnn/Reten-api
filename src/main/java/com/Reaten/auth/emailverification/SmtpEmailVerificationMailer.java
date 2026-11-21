package com.Reaten.auth.emailverification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.Reaten.config.RetenProperties;

/** Envio assíncrono: o cadastro não espera o SMTP responder. */
@Component
class SmtpEmailVerificationMailer implements EmailVerificationMailer {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailVerificationMailer.class);

	private final JavaMailSender mailSender;
	private final String from;
	private final long minutes;

	SmtpEmailVerificationMailer(JavaMailSender mailSender, RetenProperties properties) {
		this.mailSender = mailSender;
		this.from = properties.mail().from();
		this.minutes = properties.emailVerification().codeTtl().toMinutes();
	}

	@Async
	@Override
	public void sendCode(String toEmail, String recipientName, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(toEmail);
		message.setSubject(code + " é seu código do Reten");
		message.setText("""
				Olá, %s.

				Para confirmar seu e-mail e começar a usar o Reten, digite este código:

				%s

				Ele vale por %d minutos. Se você não criou uma conta no Reten, ignore este e-mail.

				Reten
				""".formatted(recipientName, code, minutes));
		try {
			mailSender.send(message);
		} catch (MailException ex) {
			log.error("Falha ao enviar o código de confirmação de e-mail: {}", ex.getMessage());
		}
	}
}
