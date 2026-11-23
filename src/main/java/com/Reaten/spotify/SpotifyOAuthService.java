package com.Reaten.spotify;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.Reaten.shared.security.Hashing;
import com.Reaten.spotify.client.SpotifyApiException;
import com.Reaten.spotify.client.SpotifyAuthClient;
import com.Reaten.spotify.client.SpotifyAuthClient.SpotifyProfile;
import com.Reaten.spotify.client.SpotifyAuthClient.TokenGrant;

import jakarta.servlet.http.HttpSession;

/**
 * Fluxo Authorization Code do Spotify. O {@code state} é guardado na sessão do usuário
 * e conferido no retorno, protegendo contra CSRF no callback.
 */
@Service
class SpotifyOAuthService {

	private static final Logger log = LoggerFactory.getLogger(SpotifyOAuthService.class);
	static final String STATE_ATTRIBUTE = "reten.spotify.oauthState";

	/** Resultado do callback, traduzido em redirecionamento para o frontend. */
	enum Outcome {
		CONNECTED("conectado"),
		NOT_CONFIGURED("nao_configurado"),
		CANCELLED("cancelado"),
		INVALID_STATE("estado_invalido"),
		USER_NOT_ALLOWED("nao_autorizado"),
		UNAVAILABLE("indisponivel"),
		SESSION_EXPIRED("sessao_expirada");

		final String code;

		Outcome(String code) {
			this.code = code;
		}
	}

	private final SpotifyProperties properties;
	private final SpotifyAuthClient client;
	private final SpotifyAccountService accounts;

	SpotifyOAuthService(SpotifyProperties properties, SpotifyAuthClient client, SpotifyAccountService accounts) {
		this.properties = properties;
		this.client = client;
		this.accounts = accounts;
	}

	boolean isConfigured() {
		return properties.isConfigured();
	}

	String startAuthorization(HttpSession session) {
		String state = Hashing.randomUrlToken();
		session.setAttribute(STATE_ATTRIBUTE, state);
		return client.authorizeUrl(state);
	}

	Outcome completeAuthorization(UUID userId, String code, String state, String error, HttpSession session) {
		String expectedState = session == null ? null : (String) session.getAttribute(STATE_ATTRIBUTE);
		if (session != null) {
			session.removeAttribute(STATE_ATTRIBUTE);
		}
		if (!Hashing.constantTimeEquals(expectedState, state)) {
			log.warn("Callback do Spotify com state inválido para o usuário {}", userId);
			return Outcome.INVALID_STATE;
		}
		if (error != null || code == null) {
			return Outcome.CANCELLED;
		}

		try {
			TokenGrant grant = client.exchangeCode(code);
			SpotifyProfile profile = client.currentProfile(grant.accessToken());
			accounts.saveConnection(userId, grant, profile);
			return Outcome.CONNECTED;
		} catch (SpotifyApiException ex) {
			log.warn("Falha ao concluir OAuth do Spotify para o usuário {}: {} ({})", userId, ex.getMessage(), ex.getKind());
			return ex.getKind() == SpotifyApiException.Kind.USER_NOT_ALLOWED ? Outcome.USER_NOT_ALLOWED : Outcome.UNAVAILABLE;
		}
	}
}
