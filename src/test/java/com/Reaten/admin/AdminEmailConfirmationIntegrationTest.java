package com.Reaten.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.Reaten.SessionCookies;
import com.Reaten.TestcontainersConfiguration;
import com.Reaten.auth.emailverification.EmailVerificationMailer;
import com.Reaten.user.UserRepository;

import jakarta.servlet.http.Cookie;

/** O administrador confirma o e-mail de um leitor sem código, e o leitor passa a usar o app na hora. */
@SpringBootTest(properties = {
		"reten.email-verification.required=true",
		"reten.admin.email=admin.confirma@exemplo.com",
		"reten.admin.password=Admin-teste-1" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminEmailConfirmationIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	UserRepository users;

	@MockitoBean
	EmailVerificationMailer mailer;

	@Test
	void adminConfirmsTheEmailAndTheReaderIsUnblockedWithoutTheCode() throws Exception {
		Cookie reader = register("sem.codigo@exemplo.com");
		mvc.perform(get("/api/library").cookie(reader)).andExpect(status().isForbidden());
		UUID readerId = users.findByEmail("sem.codigo@exemplo.com").orElseThrow().getId();

		Cookie admin = login("admin.confirma@exemplo.com", "Admin-teste-1");
		mvc.perform(post("/api/admin/users/" + readerId + "/email-verification").with(csrf()).cookie(admin))
				.andExpect(status().isNoContent());

		// Mesma sessão do leitor, sem digitar código nem entrar de novo.
		mvc.perform(get("/api/library").cookie(reader)).andExpect(status().isOk());
		mvc.perform(get("/api/auth/me").cookie(reader)).andExpect(jsonPath("$.emailVerified").value(true));

		// O código que tinha sido enviado deixa de valer.
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mailer).sendCode(eq("sem.codigo@exemplo.com"), any(), code.capture());
		mvc.perform(post("/api/auth/email/verify").with(csrf()).cookie(reader).contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + code.getValue() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailVerified").value(true));

		// Repetir não dá erro nem manda as boas-vindas de novo.
		mvc.perform(post("/api/admin/users/" + readerId + "/email-verification").with(csrf()).cookie(admin))
				.andExpect(status().isNoContent());
		verify(mailer, times(1)).sendWelcome(eq("sem.codigo@exemplo.com"), any());
	}

	@Test
	void onlyAdminsCanConfirmAndUnknownUsersAreNotFound() throws Exception {
		Cookie reader = register("tentou.sozinho@exemplo.com");
		UUID readerId = users.findByEmail("tentou.sozinho@exemplo.com").orElseThrow().getId();

		mvc.perform(post("/api/admin/users/" + readerId + "/email-verification").with(csrf()).cookie(reader))
				.andExpect(status().isForbidden());

		Cookie admin = login("admin.confirma@exemplo.com", "Admin-teste-1");
		mvc.perform(post("/api/admin/users/" + UUID.randomUUID() + "/email-verification").with(csrf()).cookie(admin))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("user_not_found"));
		verify(mailer, never()).sendWelcome(any(), any());
	}

	private Cookie login(String email, String password) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse());
	}

	private Cookie register(String email) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"Senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}
}
