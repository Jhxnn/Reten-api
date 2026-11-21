package com.Reaten.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.Reaten.SessionCookies;
import com.Reaten.TestcontainersConfiguration;
import com.Reaten.auth.emailverification.EmailVerificationMailer;

import jakarta.servlet.http.Cookie;

/** Cadastro com confirmação de e-mail por código: bloqueio, tentativas, reenvio e troca de e-mail. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "reten.email-verification.required=true")
class EmailVerificationIntegrationTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	EmailVerificationMailer mailer;

	@Test
	void appStaysBlockedUntilTheCodeIsConfirmed() throws Exception {
		Cookie session = register("confirmar@exemplo.com");
		String code = lastCodeSentTo("confirmar@exemplo.com");

		mvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailVerified").value(false));
		mvc.perform(get("/api/library").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("email_not_verified"));

		verifyCode(session, wrong(code))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("invalid_verification_code"))
				.andExpect(jsonPath("$.detail").value("Código incorreto. Restam 4 tentativas."));

		verifyCode(session, code)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailVerified").value(true));
		mvc.perform(get("/api/library").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void tooManyWrongAttemptsInvalidateTheCode() throws Exception {
		Cookie session = register("tentativas@exemplo.com");
		String code = lastCodeSentTo("tentativas@exemplo.com");

		for (int i = 0; i < 4; i++) {
			verifyCode(session, wrong(code)).andExpect(jsonPath("$.code").value("invalid_verification_code"));
		}
		verifyCode(session, wrong(code)).andExpect(jsonPath("$.code").value("verification_code_expired"));
		// Nem o código certo vale depois disso: é preciso pedir outro.
		verifyCode(session, code)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("verification_code_expired"));
	}

	@Test
	void resendWaitsBetweenCodes() throws Exception {
		Cookie session = register("reenvio@exemplo.com");

		mvc.perform(post("/api/auth/email/resend").with(csrf()).cookie(session))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("verification_resend_too_soon"));
		verify(mailer, times(1)).sendCode(eq("reenvio@exemplo.com"), anyString(), anyString());
	}

	@Test
	void unconfirmedEmailCanBeCorrected() throws Exception {
		Cookie session = register("digitado.errado@exemplo.com");
		String oldCode = lastCodeSentTo("digitado.errado@exemplo.com");

		ResultActions changed = mvc.perform(put("/api/auth/email").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"Certo@Exemplo.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("certo@exemplo.com"))
				.andExpect(jsonPath("$.emailVerified").value(false));
		session = SessionCookies.from(changed.andReturn().getResponse());

		verifyCode(session, oldCode).andExpect(jsonPath("$.code").value("invalid_verification_code"));
		verifyCode(session, lastCodeSentTo("certo@exemplo.com")).andExpect(status().isOk());

		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"certo@exemplo.com\",\"password\":\"senha-segura-1\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailVerified").value(true));
	}

	@Test
	void emailOfAnotherAccountCannotBeTaken() throws Exception {
		register("ja.existe@exemplo.com");
		Cookie session = register("quer.trocar@exemplo.com");

		mvc.perform(put("/api/auth/email").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ja.existe@exemplo.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("email_taken"));
	}

	@Test
	void codeMustHaveSixDigits() throws Exception {
		Cookie session = register("formato@exemplo.com");
		verifyCode(session, "12ab").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation_failed"));
	}

	private ResultActions verifyCode(Cookie session, String code) throws Exception {
		return mvc.perform(post("/api/auth/email/verify").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
	}

	private String lastCodeSentTo(String email) {
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mailer, org.mockito.Mockito.atLeastOnce()).sendCode(eq(email), anyString(), code.capture());
		String value = code.getValue();
		assertThat(value).matches("\\d{6}");
		return value;
	}

	private static String wrong(String code) {
		return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
	}

	private Cookie register(String email) throws Exception {
		clearInvocations(mailer);
		return SessionCookies.from(mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitora\",\"email\":\"" + email + "\",\"password\":\"senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andReturn().getResponse());
	}
}
