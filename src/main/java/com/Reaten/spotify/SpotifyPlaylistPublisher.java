package com.Reaten.spotify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.Reaten.music.PlaylistPublisher;
import com.Reaten.spotify.client.SpotifyApiException;
import com.Reaten.spotify.client.SpotifyApiException.Kind;
import com.Reaten.spotify.client.SpotifyHttp;

import tools.jackson.databind.JsonNode;

/**
 * Publica playlists na conta Spotify do usuário (endpoints de fev/2026):
 * {@code POST /me/playlists}, {@code PUT /playlists/{id}}, {@code PUT|POST /playlists/{id}/items} (até 100 por chamada).
 * As playlists são criadas como privadas.
 */
@Component
class SpotifyPlaylistPublisher implements PlaylistPublisher {

	private static final Logger log = LoggerFactory.getLogger(SpotifyPlaylistPublisher.class);
	private static final int BATCH = 100;
	private static final int MAX_DESCRIPTION = 300;

	private final SpotifyProperties properties;
	private final SpotifyAccountService accounts;
	private final RestClient api;

	SpotifyPlaylistPublisher(SpotifyProperties properties, SpotifyAccountService accounts) {
		this.properties = properties;
		this.accounts = accounts;
		this.api = SpotifyHttp.client(properties.apiBaseUrl());
	}

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public PublishedPlaylist publish(UUID userId, String existingId, String name, String description, List<String> trackIds) {
		String token = accounts.validAccessToken(userId);
		String cleanDescription = spotifyDescription(description);
		List<String> uris = trackIds.stream().map(id -> "spotify:track:" + id).toList();
		try {
			if (existingId != null) {
				try {
					SpotifyHttp.call(() -> api.put().uri("/playlists/{id}", existingId).headers(h -> auth(h, token))
							.contentType(MediaType.APPLICATION_JSON)
							.body(Map.of("name", name, "description", cleanDescription))
							.retrieve().toBodilessEntity());
					replaceItems(token, existingId, uris);
					log.info("Playlist Spotify {} atualizada para o usuário {}", existingId, userId);
					return new PublishedPlaylist(existingId, "https://open.spotify.com/playlist/" + existingId);
				} catch (SpotifyApiException ex) {
					if (ex.getKind() != Kind.NOT_FOUND) {
						throw ex;
					}
					log.info("Playlist Spotify {} não existe mais; criando outra", existingId);
				}
			}
			JsonNode created = SpotifyHttp.call(() -> api.post().uri("/me/playlists").headers(h -> auth(h, token))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("name", name, "description", cleanDescription, "public", false))
					.retrieve().body(JsonNode.class));
			String id = created.path("id").asString();
			String url = created.path("external_urls").path("spotify").asString("https://open.spotify.com/playlist/" + id);
			replaceItems(token, id, uris);
			log.info("Playlist Spotify {} criada para o usuário {} com {} faixas", id, userId, uris.size());
			return new PublishedPlaylist(id, url);
		} catch (SpotifyApiException ex) {
			throw SpotifyErrors.toApiException(ex);
		}
	}

	/** Substitui o conteúdo (PUT com até 100) e acrescenta o restante em lotes (POST). */
	private void replaceItems(String token, String playlistId, List<String> uris) {
		List<String> first = uris.subList(0, Math.min(BATCH, uris.size()));
		SpotifyHttp.call(() -> api.put().uri("/playlists/{id}/items", playlistId).headers(h -> auth(h, token))
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("uris", first))
				.retrieve().toBodilessEntity());
		for (int start = BATCH; start < uris.size(); start += BATCH) {
			List<String> batch = uris.subList(start, Math.min(start + BATCH, uris.size()));
			SpotifyHttp.call(() -> api.post().uri("/playlists/{id}/items", playlistId).headers(h -> auth(h, token))
					.contentType(MediaType.APPLICATION_JSON).body(Map.of("uris", batch))
					.retrieve().toBodilessEntity());
		}
	}

	/** O Spotify aceita descrições curtas e de uma linha só. */
	static String spotifyDescription(String description) {
		String text = description == null ? "" : description.replaceAll("\\s+", " ").strip();
		String withCredit = text.isEmpty() ? "Criada com o Resten." : text + " — Criada com o Resten.";
		return withCredit.length() <= MAX_DESCRIPTION ? withCredit : withCredit.substring(0, MAX_DESCRIPTION - 1) + "…";
	}

	private static void auth(HttpHeaders headers, String token) {
		headers.setBearerAuth(token);
	}

}
