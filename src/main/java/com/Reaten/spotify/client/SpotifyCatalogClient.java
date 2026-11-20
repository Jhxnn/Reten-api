package com.Reaten.spotify.client;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.Reaten.music.CatalogTrack;
import com.Reaten.music.MusicCatalog;
import com.Reaten.spotify.SpotifyProperties;
import com.Reaten.spotify.client.SpotifyApiException.Kind;

import tools.jackson.databind.JsonNode;

/**
 * Catálogo musical via Spotify Web API, com o token do próprio app (Client Credentials):
 * a verificação funciona para qualquer usuário, conectado ou não ao Spotify.
 * <p>
 * Limites de Development Mode (fev/2026): {@code limit} máximo 10 na busca; {@code popularity} não existe mais.
 * 429 é respeitado com uma nova tentativa (Retry-After de até 5 s); 401 renova o token do app uma vez.
 */
@Component
public class SpotifyCatalogClient implements MusicCatalog {

	private static final Logger log = LoggerFactory.getLogger(SpotifyCatalogClient.class);
	private static final int MAX_LIMIT = 10;
	private static final Duration MAX_WAIT = Duration.ofSeconds(5);

	private final SpotifyProperties properties;
	private final SpotifyAuthClient auth;
	private final RestClient api;

	private String appToken;
	private Instant appTokenExpiresAt = Instant.EPOCH;

	SpotifyCatalogClient(SpotifyProperties properties, SpotifyAuthClient auth) {
		this.properties = properties;
		this.auth = auth;
		this.api = SpotifyHttp.client(properties.apiBaseUrl());
	}

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public List<CatalogTrack> searchTracks(String query, int limit) {
		int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
		JsonNode body = withAppToken(token -> api.get()
				.uri(uri -> uri.path("/search").queryParam("q", query).queryParam("type", "track")
						.queryParam("limit", bounded).build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.body(JsonNode.class));
		List<CatalogTrack> tracks = new ArrayList<>();
		for (JsonNode item : body.path("tracks").path("items")) {
			toTrack(item).ifPresent(tracks::add);
		}
		return tracks;
	}

	@Override
	public Optional<CatalogTrack> findTrack(String id) {
		if (id == null || !id.matches("[A-Za-z0-9]{10,40}")) {
			return Optional.empty();
		}
		try {
			JsonNode body = withAppToken(token -> api.get()
					.uri("/tracks/{id}", id)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.retrieve()
					.body(JsonNode.class));
			return toTrack(body);
		} catch (SpotifyApiException ex) {
			if (ex.getKind() == Kind.NOT_FOUND) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	static Optional<CatalogTrack> toTrack(JsonNode item) {
		String id = text(item.path("id"));
		String name = text(item.path("name"));
		String url = text(item.path("external_urls").path("spotify"));
		if (id == null || name == null || url == null) {
			return Optional.empty();
		}
		List<String> artists = new ArrayList<>();
		for (JsonNode artist : item.path("artists")) {
			String artistName = text(artist.path("name"));
			if (artistName != null) {
				artists.add(artistName);
			}
		}
		JsonNode album = item.path("album");
		// Imagens vêm da maior para a menor; a do meio (~300px) basta para listas.
		JsonNode images = album.path("images");
		String image = text(images.path(Math.min(1, Math.max(0, images.size() - 1))).path("url"));
		Integer duration = item.path("duration_ms").isNumber() ? item.path("duration_ms").asInt() : null;
		return Optional.of(new CatalogTrack(id, name, artists, text(album.path("name")), image, duration, url));
	}

	private <T> T withAppToken(Function<String, T> request) {
		for (int attempt = 0; ; attempt++) {
			try {
				return SpotifyHttp.call(() -> request.apply(appToken()));
			} catch (SpotifyApiException ex) {
				if (attempt > 0) {
					throw ex;
				}
				if (ex.getKind() == Kind.TOKEN_EXPIRED) {
					invalidateToken();
				} else if (ex.getKind() == Kind.RATE_LIMITED
						&& (ex.getRetryAfter() == null || ex.getRetryAfter().compareTo(MAX_WAIT) <= 0)) {
					sleep(ex.getRetryAfter() == null ? Duration.ofSeconds(1) : ex.getRetryAfter());
				} else {
					throw ex;
				}
			}
		}
	}

	private synchronized String appToken() {
		if (appToken == null || Instant.now().isAfter(appTokenExpiresAt.minusSeconds(60))) {
			SpotifyAuthClient.TokenGrant grant = auth.clientCredentials();
			appToken = grant.accessToken();
			appTokenExpiresAt = Instant.now().plus(grant.expiresIn());
			log.debug("Token de app da Spotify renovado");
		}
		return appToken;
	}

	private synchronized void invalidateToken() {
		appToken = null;
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new SpotifyApiException(Kind.UNAVAILABLE, "Interrompido", null, ex);
		}
	}

	private static String text(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asString();
		return value == null || value.isBlank() ? null : value;
	}
}
