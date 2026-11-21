package com.Reaten.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.Reaten.TestcontainersConfiguration;
import com.Reaten.auth.passwordreset.PasswordResetMailer;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	PasswordResetMailer mailer;

	@Test
	void registerStartsSessionAndMeReturnsTheUser() throws Exception {
		Cookie session = register("Ana Leitora", "Ana@Exemplo.com", "Senha-segura-1");

		mvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana Leitora"))
				.andExpect(jsonPath("$.email").value("ana@exemplo.com"));
	}

	@Test
	void privateEndpointsAnswer401InProblemFormat() throws Exception {
		mvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("unauthenticated"));
	}

	@Test
	void mutatingRequestsWithoutCsrfAreRejected() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"x@y.com\",\"password\":\"12345678\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void duplicateEmailIsAConflict() throws Exception {
		register("Bruno", "bruno@exemplo.com", "Senha-segura-1");

		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(json("Outro Bruno", "BRUNO@exemplo.com", "Outra-senha-1")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("email_taken"));
	}

	@Test
	void invalidInputReturnsFieldErrors() throws Exception {
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(json("", "nao-e-email", "curta")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation_failed"))
				.andExpect(jsonPath("$.fields.name").exists())
				.andExpect(jsonPath("$.fields.email").exists())
				.andExpect(jsonPath("$.fields.password").exists());
	}

	@Test
	void wrongPasswordIsRejectedWithoutRevealingWhichFieldFailed() throws Exception {
		register("Carla", "carla@exemplo.com", "Senha-segura-1");

		login("carla@exemplo.com", "senha-errada-1")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("invalid_credentials"));
		login("ninguem@exemplo.com", "senha-errada-1")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		Cookie session = register("Davi", "davi@exemplo.com", "Senha-segura-1");

		mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void passwordResetChangesPasswordInvalidatesSessionsAndIsSingleUse() throws Exception {
		Cookie oldSession = register("Elisa", "elisa@exemplo.com", "Senha-antiga-1");

		mvc.perform(post("/api/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"elisa@exemplo.com\"}"))
				.andExpect(status().isAccepted());

		ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
		verify(mailer).sendResetLink(eq("elisa@exemplo.com"), eq("Elisa"), url.capture());
		assertThat(url.getValue()).startsWith("http://127.0.0.1:5173/redefinir-senha?token=");
		String token = url.getValue().substring(url.getValue().indexOf("token=") + 6);

		String resetBody = "{\"token\":\"" + token + "\",\"password\":\"Senha-nova-123\"}";
		mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(resetBody))
				.andExpect(status().isNoContent());

		mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(resetBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("invalid_reset_token"));

		mvc.perform(get("/api/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
		login("elisa@exemplo.com", "Senha-antiga-1").andExpect(status().isUnauthorized());
		login("elisa@exemplo.com", "Senha-nova-123").andExpect(status().isOk());
	}

	@Test
	void forgotPasswordForUnknownEmailLooksTheSame() throws Exception {
		mvc.perform(post("/api/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"desconhecido@exemplo.com\"}"))
				.andExpect(status().isAccepted());
		verify(mailer, org.mockito.Mockito.never()).sendResetLink(anyString(), anyString(), anyString());
	}

	@Test
	void weakPasswordsSayWhatIsMissing() throws Exception {
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(json("Fraca", "fraca@exemplo.com", "semmaiuscula-1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.password").value("A senha precisa ter uma letra maiúscula."));
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(json("Fraca", "fraca@exemplo.com", "curta")))
				.andExpect(jsonPath("$.fields.password").value(
						"A senha precisa ter pelo menos 8 caracteres, uma letra maiúscula e um caractere especial (ex.: ! @ # -)."));
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(json("Forte", "forte@exemplo.com", "Árvore#lida")))
				.andExpect(status().isCreated());
	}

	Cookie register(String name, String email, String password) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(json(name, email, password)))
				.andExpect(status().isCreated())
				.andReturn();
		Cookie cookie = com.Reaten.SessionCookies.from(result.getResponse());
		assertThat(cookie).isNotNull();
		assertThat(cookie.isHttpOnly()).isTrue();
		return cookie;
	}

	org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	static String json(String name, String email, String password) {
		return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}
}
