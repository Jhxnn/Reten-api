package com.Reaten.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.Reaten.music.PlaylistPublisher.PublishedPlaylist;
import com.Reaten.shared.exception.ApiException;
import com.Reaten.spotify.client.SpotifyStubServer;

class SpotifyPlaylistPublisherTest {

	private static final UUID USER = UUID.randomUUID();
	private static final String CREATED = """
			{"id":"37i9dQZF1DX","external_urls":{"spotify":"https://open.spotify.com/playlist/37i9dQZF1DX"}}""";

	private SpotifyStubServer stub;
	private SpotifyPlaylistPublisher publisher;

	@BeforeEach
	void start() throws Exception {
		stub = new SpotifyStubServer();
		SpotifyAccountService accounts = mock(SpotifyAccountService.class);
		when(accounts.validAccessToken(USER)).thenReturn("user-token");
		publisher = new SpotifyPlaylistPublisher(new SpotifyProperties("c", "s", "http://cb", "scope",
				stub.baseUrl(), stub.baseUrl() + "/v1"), accounts);
	}

	@AfterEach
	void stop() {
		stub.close();
	}

	@Test
	void createsAPrivatePlaylistWithTheNewEndpointsAndAddsItems() {
		stub.on("POST /v1/me/playlists", 201, CREATED).on("PUT /v1/playlists/37i9dQZF1DX/items", 200, "{\"snapshot_id\":\"x\"}");

		PublishedPlaylist published = publisher.publish(USER, null, "Silêncio em três partes",
				"Para ler devagar.\nCom chuva.", List.of("aaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbb"));

		assertThat(published.url()).isEqualTo("https://open.spotify.com/playlist/37i9dQZF1DX");
		var create = stub.requestsTo("POST /v1/me/playlists").getFirst();
		assertThat(create.authorization()).isEqualTo("Bearer user-token");
		assertThat(create.body()).contains("\"public\":false").contains("Para ler devagar. Com chuva. — Criada com o Resten.");
		assertThat(stub.requestsTo("PUT /v1/playlists/37i9dQZF1DX/items").getFirst().body())
				.contains("spotify:track:aaaaaaaaaaaaaaaaaaaaaa").contains("spotify:track:bbbbbbbbbbbbbbbbbbbbbb");
	}

	@Test
	void updatesTheExistingPlaylistInsteadOfCreatingAnother() {
		stub.on("PUT /v1/playlists/existente", 200, "").on("PUT /v1/playlists/existente/items", 200, "{}");

		PublishedPlaylist published = publisher.publish(USER, "existente", "Novo nome", null, List.of("aaaaaaaaaaaaaaaaaaaaaa"));

		assertThat(published.id()).isEqualTo("existente");
		assertThat(stub.requestsTo("POST /v1/me/playlists")).isEmpty();
		assertThat(stub.requestsTo("PUT /v1/playlists/existente").getFirst().body()).contains("Novo nome");
	}

	@Test
	void recreatesWhenThePlaylistWasDeletedOnSpotify() {
		stub.on("PUT /v1/playlists/apagada", 404, "{}")
				.on("POST /v1/me/playlists", 201, CREATED)
				.on("PUT /v1/playlists/37i9dQZF1DX/items", 200, "{}");

		assertThat(publisher.publish(USER, "apagada", "Nome", null, List.of("aaaaaaaaaaaaaaaaaaaaaa")).id())
				.isEqualTo("37i9dQZF1DX");
	}

	@Test
	void sendsMoreThanOneHundredTracksInBatches() {
		stub.on("POST /v1/me/playlists", 201, CREATED)
				.on("PUT /v1/playlists/37i9dQZF1DX/items", 200, "{}")
				.on("POST /v1/playlists/37i9dQZF1DX/items", 201, "{}");
		List<String> ids = IntStream.range(0, 150).mapToObj(i -> String.format("%022d", i)).toList();

		publisher.publish(USER, null, "Grande", null, ids);

		assertThat(stub.requestsTo("PUT /v1/playlists/37i9dQZF1DX/items")).hasSize(1);
		assertThat(stub.requestsTo("POST /v1/playlists/37i9dQZF1DX/items")).hasSize(1);
	}

	@Test
	void forbiddenBecomesTheAllowlistMessage() {
		stub.on("POST /v1/me/playlists", 403, "{}");
		assertThatThrownBy(() -> publisher.publish(USER, null, "Nome", null, List.of("aaaaaaaaaaaaaaaaaaaaaa")))
				.isInstanceOf(ApiException.class)
				.extracting("code").isEqualTo("spotify_user_not_allowed");
	}
}
