package com.Reaten.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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
import com.Reaten.shared.exception.ApiException;
import com.Reaten.spotify.client.SpotifyApiException;
import com.Reaten.spotify.client.SpotifyAuthClient;
import com.Reaten.spotify.client.SpotifyAuthClient.SpotifyProfile;
import com.Reaten.spotify.client.SpotifyAuthClient.TokenGrant;
import com.Reaten.user.UserRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SpotifyConnectionIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	SpotifyAccountService accountService;

	@Autowired
	SpotifyAccountRepository accounts;

	@Autowired
	UserRepository users;

	@MockitoBean
	SpotifyAuthClient spotify;

	@Test
	void fullAuthorizationCodeFlowStoresEncryptedTokens() throws Exception {
		Cookie session = register("fluxo@exemplo.com");
		when(spotify.authorizeUrl(anyString())).thenAnswer(inv -> "https://accounts.spotify.test/authorize?state=" + inv.getArgument(0));
		when(spotify.exchangeCode("codigo-ok")).thenReturn(new TokenGrant("access-1", "refresh-1", "playlist-modify-private", Duration.ofHours(1)));
		when(spotify.currentProfile("access-1")).thenReturn(new SpotifyProfile("spotify-user-1", "Leitora"));

		mvc.perform(get("/api/spotify/connection").cookie(session))
				.andExpect(jsonPath("$.configured").value(true))
				.andExpect(jsonPath("$.connected").value(false));

		String state = startAuthorization(session);

		mvc.perform(get("/api/spotify/callback").param("code", "codigo-ok").param("state", state).cookie(session))
				.andExpect(status().isFound())
				.andExpect(result -> assertThat(result.getResponse().getHeader("Location"))
						.isEqualTo("http://127.0.0.1:5173/inicio?spotify=conectado"));

		mvc.perform(get("/api/spotify/connection").cookie(session))
				.andExpect(jsonPath("$.connected").value(true))
				.andExpect(jsonPath("$.displayName").value("Leitora"));

		SpotifyAccount stored = accounts.findByUserId(userId("fluxo@exemplo.com")).orElseThrow();
		assertThat(stored.getAccessTokenEncrypted()).startsWith("v1:").doesNotContain("access-1");
		assertThat(stored.getRefreshTokenEncrypted()).startsWith("v1:").doesNotContain("refresh-1");
	}

	@Test
	void callbackWithWrongStateIsRejected() throws Exception {
		Cookie session = register("estado@exemplo.com");
		when(spotify.authorizeUrl(anyString())).thenAnswer(inv -> "https://accounts.spotify.test/authorize?state=" + inv.getArgument(0));
		startAuthorization(session);

		mvc.perform(get("/api/spotify/callback").param("code", "x").param("state", "forjado").cookie(session))
				.andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("motivo=estado_invalido"));
	}

	@Test
	void userOutsideTheDevelopmentModeAllowlistGetsAClearReason() throws Exception {
		Cookie session = register("fora@exemplo.com");
		when(spotify.authorizeUrl(anyString())).thenAnswer(inv -> "https://accounts.spotify.test/authorize?state=" + inv.getArgument(0));
		when(spotify.exchangeCode("codigo")).thenReturn(new TokenGrant("a", "r", "s", Duration.ofHours(1)));
		when(spotify.currentProfile("a")).thenThrow(new SpotifyApiException(SpotifyApiException.Kind.USER_NOT_ALLOWED, "403", null, null));

		String state = startAuthorization(session);
		mvc.perform(get("/api/spotify/callback").param("code", "codigo").param("state", state).cookie(session))
				.andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("motivo=nao_autorizado"));
	}

	@Test
	void expiredAccessTokenIsRefreshedAndRotatedRefreshTokenIsKept() throws Exception {
		Cookie session = register("renova@exemplo.com");
		connect(session, "renova", Duration.ZERO);
		when(spotify.refresh("refresh-renova")).thenReturn(new TokenGrant("access-novo", "refresh-novo", null, Duration.ofHours(1)));

		UUID userId = userId("renova@exemplo.com");
		assertThat(accountService.validAccessToken(userId)).isEqualTo("access-novo");
		// Agora válido: não deve renovar de novo.
		assertThat(accountService.validAccessToken(userId)).isEqualTo("access-novo");
	}

	@Test
	void revokedRefreshTokenRemovesTheConnection() throws Exception {
		Cookie session = register("revogado@exemplo.com");
		connect(session, "revogado", Duration.ZERO);
		when(spotify.refresh("refresh-revogado"))
				.thenThrow(new SpotifyApiException(SpotifyApiException.Kind.INVALID_GRANT, "invalid_grant", null, null));

		UUID userId = userId("revogado@exemplo.com");
		assertThatThrownBy(() -> accountService.validAccessToken(userId))
				.isInstanceOf(ApiException.class)
				.extracting("code").isEqualTo("spotify_reauthorization_required");
		assertThat(accounts.findByUserId(userId)).isEmpty();
	}

	@Test
	void disconnectDeletesTokens() throws Exception {
		Cookie session = register("sair@exemplo.com");
		connect(session, "sair", Duration.ofHours(1));

		mvc.perform(delete("/api/spotify/connection").with(csrf()).cookie(session)).andExpect(status().isNoContent());
		mvc.perform(get("/api/spotify/connection").cookie(session)).andExpect(jsonPath("$.connected").value(false));
	}

	private void connect(Cookie session, String id, Duration expiresIn) throws Exception {
		when(spotify.authorizeUrl(anyString())).thenAnswer(inv -> "https://accounts.spotify.test/authorize?state=" + inv.getArgument(0));
		when(spotify.exchangeCode("code-" + id)).thenReturn(new TokenGrant("access-" + id, "refresh-" + id, "scope", expiresIn));
		when(spotify.currentProfile("access-" + id)).thenReturn(new SpotifyProfile("sp-" + id, id));
		String state = startAuthorization(session);
		mvc.perform(get("/api/spotify/callback").param("code", "code-" + id).param("state", state).cookie(session))
				.andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("spotify=conectado"));
	}

	private String startAuthorization(Cookie session) throws Exception {
		MvcResult result = mvc.perform(get("/api/spotify/authorize").cookie(session))
				.andExpect(status().isFound())
				.andReturn();
		String location = result.getResponse().getHeader("Location");
		return location.substring(location.indexOf("state=") + 6);
	}

	private Cookie register(String email) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Teste\",\"email\":\"" + email + "\",\"password\":\"senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return com.Reaten.SessionCookies.from(result.getResponse());
	}

	private UUID userId(String email) {
		return users.findByEmail(email).orElseThrow().getId();
	}
}
