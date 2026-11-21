package com.Reaten.shared.mail;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.Reaten.config.RetenProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * E-mails transacionais com a identidade do Reten: HTML em tabelas e estilos inline (o que os clientes de
 * e-mail aceitam), cabeçalho com a marca como imagem embutida (SVG e fontes próprias não funcionam no Gmail)
 * e uma versão em texto puro para quem não exibe HTML.
 */
@Component
public class BrandedMailSender {

	private static final ClassPathResource HEADER = new ClassPathResource("mail/reten-header.png");

	// Paleta "verde profundo" do app
	private static final String NIGHT = "#10241e";
	private static final String PAPER = "#fafaf7";
	private static final String MINT_PAPER = "#eef4f0";
	private static final String MINT = "#d3e6da";
	private static final String FOREST = "#1f5c4a";
	private static final String MUTED = "#5b6b65";
	private static final String SERIF = "Georgia, 'Times New Roman', serif";
	private static final String SANS = "'Segoe UI', Helvetica, Arial, sans-serif";

	private final JavaMailSender mailSender;
	private final String from;

	BrandedMailSender(JavaMailSender mailSender, RetenProperties properties) {
		this.mailSender = mailSender;
		this.from = properties.mail().from();
	}

	/** O destaque do e-mail: um código para digitar ou um botão com link. */
	public sealed interface Highlight {
	}

	public record Code(String value) implements Highlight {
	}

	public record Button(String label, String url) implements Highlight {
	}

	/**
	 * @param preheader texto curto que os clientes mostram ao lado do assunto
	 * @param intro     parágrafo antes do destaque
	 * @param note      linha discreta logo depois do destaque (ex.: validade)
	 * @param footnote  rodapé (ex.: "se não foi você, ignore")
	 */
	public record Message(String to, String subject, String preheader, String recipientName, String intro,
			Highlight highlight, String note, String footnote) {
	}

	public void send(Message message) throws MailException {
		MimeMessage mime = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
			helper.setFrom(from);
			helper.setTo(message.to());
			helper.setSubject(message.subject());
			helper.setText(plainText(message), html(message));
			helper.addInline("reten-header", HEADER, "image/png");
		} catch (MessagingException ex) {
			throw new MailPreparationException("Não foi possível montar o e-mail", ex);
		}
		mailSender.send(mime);
	}

	static String plainText(Message m) {
		String highlight = switch (m.highlight()) {
			case Code code -> code.value();
			case Button button -> button.label() + ": " + button.url();
		};
		return """
				Olá, %s.

				%s

				%s

				%s

				%s

				Reten · Cada história tem uma trilha sonora.
				""".formatted(m.recipientName(), m.intro(), highlight, m.note(), m.footnote());
	}

	static String html(Message m) {
		String highlight = switch (m.highlight()) {
			case Code code -> """
					<table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%">
					  <tr><td align="center" style="background:%s;border-radius:6px;padding:22px 12px;">
					    <span style="font-family:%s;font-size:38px;font-weight:600;line-height:1;letter-spacing:12px;\
					color:%s;font-variant-numeric:lining-nums tabular-nums;padding-left:12px;">%s</span>
					  </td></tr>
					</table>""".formatted(MINT, SANS, NIGHT, esc(code.value()));
			case Button button -> """
					<table role="presentation" cellpadding="0" cellspacing="0" border="0">
					  <tr><td style="background:%s;border-radius:4px;">
					    <a href="%s" style="display:inline-block;padding:15px 26px;font-family:%s;font-size:16px;\
					font-weight:600;color:%s;text-decoration:none;">%s</a>
					  </td></tr>
					</table>
					<p style="margin:16px 0 0;font-family:%s;font-size:13px;line-height:1.5;color:%s;">
					  Se o botão não abrir, copie este endereço no navegador:<br>
					  <a href="%s" style="color:%s;word-break:break-all;">%s</a>
					</p>""".formatted(FOREST, esc(button.url()), SANS, PAPER, esc(button.label()),
					SANS, MUTED, esc(button.url()), FOREST, esc(button.url()));
		};
		return """
				<!doctype html>
				<html lang="pt-BR">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width,initial-scale=1">
				<meta name="color-scheme" content="light">
				<title>%s</title>
				</head>
				<body style="margin:0;padding:0;background:%s;">
				<div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
				<table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="background:%s;">
				  <tr><td align="center" style="padding:32px 16px;">
				    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="max-width:520px;">
				      <tr><td style="background:%s;border-radius:8px 8px 0 0;padding:26px 32px;">
				        <img src="cid:reten-header" width="150" height="48" alt="Reten" style="display:block;border:0;">
				      </td></tr>
				      <tr><td style="background:%s;border-radius:0 0 8px 8px;padding:36px 32px 32px;">
				        <h1 style="margin:0 0 14px;font-family:%s;font-size:28px;line-height:1.2;font-weight:normal;color:%s;">Olá, %s.</h1>
				        <p style="margin:0 0 26px;font-family:%s;font-size:16px;line-height:1.6;color:%s;">%s</p>
				        %s
				        <p style="margin:18px 0 0;font-family:%s;font-size:14px;line-height:1.5;color:%s;">%s</p>
				        <hr style="border:0;border-top:1px solid #dfe6e2;margin:30px 0 20px;">
				        <p style="margin:0;font-family:%s;font-size:13px;line-height:1.6;color:%s;">%s</p>
				      </td></tr>
				      <tr><td align="center" style="padding:22px 16px 0;font-family:%s;font-size:13px;font-style:italic;color:%s;">
				        Cada história tem uma trilha sonora.
				      </td></tr>
				    </table>
				  </td></tr>
				</table>
				</body>
				</html>
				""".formatted(esc(m.subject()), MINT_PAPER, esc(m.preheader()), MINT_PAPER,
				NIGHT, PAPER,
				SERIF, NIGHT, esc(m.recipientName()),
				SANS, NIGHT, esc(m.intro()),
				highlight,
				SANS, MUTED, esc(m.note()),
				SANS, MUTED, esc(m.footnote()),
				SERIF, MUTED);
	}

	private static String esc(String value) {
		return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
	}
}
