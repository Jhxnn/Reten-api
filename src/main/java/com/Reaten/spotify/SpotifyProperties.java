package com.Reaten.spotify;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credenciais e endereços da Spotify Web API. clientId/clientSecret vazios significam
 * "integração não configurada" — os endpoints respondem isso explicitamente em vez de falhar em silêncio.
 *
 * @param redirectUri precisa ser idêntica à cadastrada no Spotify Developer Dashboard. A Spotify não aceita
 *                    {@code localhost}: em desenvolvimento use {@code http://127.0.0.1:5173/api/spotify/callback}.
 */
@ConfigurationProperties(prefix = "reten.spotify")
public record SpotifyProperties(
		String clientId,
		String clientSecret,
		@DefaultValue("http://127.0.0.1:5173/api/spotify/callback") String redirectUri,
		@DefaultValue("playlist-modify-private playlist-modify-public") String scopes,
		@DefaultValue("https://accounts.spotify.com") String accountsBaseUrl,
		@DefaultValue("https://api.spotify.com/v1") String apiBaseUrl) {

	public boolean isConfigured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}
}
