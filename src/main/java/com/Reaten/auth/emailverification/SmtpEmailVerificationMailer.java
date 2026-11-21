package com.Reaten.auth.emailverification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.Reaten.config.RetenProperties;
import com.Reaten.shared.mail.BrandedMailSender;
import com.Reaten.shared.mail.BrandedMailSender.Code;
import com.Reaten.shared.mail.BrandedMailSender.Message;

/** Envio assíncrono: o cadastro não espera o SMTP responder. */
@Component
class SmtpEmailVerificationMailer implements EmailVerificationMailer {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailVerificationMailer.class);

	private final BrandedMailSender mail;
	private final long minutes;

	SmtpEmailVerificationMailer(BrandedMailSender mail, RetenProperties properties) {
		this.mail = mail;
		this.minutes = properties.emailVerification().codeTtl().toMinutes();
	}

	@Async
	@Override
	public void sendCode(String toEmail, String recipientName, String code) {
		try {
			mail.send(new Message(
					toEmail,
					code + " é seu código do Reten",
					"Digite este código para confirmar seu e-mail e começar a ouvir seus livros.",
					recipientName,
					"Para confirmar seu e-mail e começar a transformar livros em trilhas sonoras, digite este código no Reten:",
					new Code(code),
					"O código vale por " + minutes + " minutos e só pode ser usado uma vez.",
					"Se você não criou uma conta no Reten, ignore este e-mail. Nenhuma conta será ativada sem o código."));
		} catch (MailException ex) {
			log.error("Falha ao enviar o código de confirmação de e-mail: {}", ex.getMessage());
		}
	}
}
