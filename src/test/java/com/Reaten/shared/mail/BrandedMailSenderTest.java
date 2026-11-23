package com.Reaten.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrandedMailSenderTest {

	@Test
	void senderWrappedInQuotesFromTheEnvFileIsUnquoted() {
		assertThat(BrandedMailSender.unquote("\"Resten <a@b.com>\"")).isEqualTo("Resten <a@b.com>");
		assertThat(BrandedMailSender.unquote(" Resten <a@b.com> ")).isEqualTo("Resten <a@b.com>");
		assertThat(BrandedMailSender.unquote("\"Resten\" <a@b.com>")).isEqualTo("\"Resten\" <a@b.com>");
		assertThat(BrandedMailSender.unquote("a@b.com")).isEqualTo("a@b.com");
	}
}
