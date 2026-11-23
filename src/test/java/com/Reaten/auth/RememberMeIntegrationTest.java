package com.Reaten.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.Reaten.SessionCookies;
import com.Reaten.TestcontainersConfiguration;

/** "Manter conectado": decide se o cookie sobrevive ao navegador e quanto tempo a sessão dura sem uso. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RememberMeIntegrationTest {

	static final int THIRTY_DAYS = 30 * 24 * 60 * 60;
	static final int TWELVE_HOURS = 12 * 60 * 60;

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void keepSignedInGivesTheCookieALifetimeAndTheSessionThirtyDays() throws Exception {
		register("lembrar@exemplo.com");

		MockHttpServletResponse response = login("lembrar@exemplo.com", true);

		assertThat(sessionCookieHeader(response)).contains("Max-Age=");
		assertThat(maxInactiveInterval(response)).isEqualTo(THIRTY_DAYS);
	}

	@Test
	void withoutKeepSignedInTheCookieEndsWithTheBrowser() throws Exception {
		register("esquecer@exemplo.com");

		MockHttpServletResponse response = login("esquecer@exemplo.com", false);

		assertThat(sessionCookieHeader(response)).doesNotContain("Max-Age=");
		assertThat(maxInactiveInterval(response)).isEqualTo(TWELVE_HOURS);
	}

	@Test
	void newAccountsStaySignedIn() throws Exception {
		MockHttpServletResponse response = register("recem@exemplo.com");

		assertThat(sessionCookieHeader(response)).contains("Max-Age=");
		assertThat(maxInactiveInterval(response)).isEqualTo(THIRTY_DAYS);
	}

	private int maxInactiveInterval(MockHttpServletResponse response) {
		String sessionId = new String(java.util.Base64.getDecoder().decode(SessionCookies.from(response).getValue()));
		return jdbc.sql("SELECT max_inactive_interval FROM spring_session WHERE session_id = :id")
				.param("id", sessionId)
				.query(Integer.class)
				.single();
	}

	private static String sessionCookieHeader(MockHttpServletResponse response) {
		return response.getHeaders("Set-Cookie").stream()
				.filter(header -> header.startsWith(SessionCookies.NAME + "="))
				.findFirst()
				.orElseThrow();
	}

	private MockHttpServletResponse login(String email, boolean remember) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"Senha-segura-1\",\"rememberMe\":" + remember + "}"))
				.andExpect(status().isOk())
				.andReturn().getResponse();
	}

	private MockHttpServletResponse register(String email) throws Exception {
		return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"Senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse();
	}
}
