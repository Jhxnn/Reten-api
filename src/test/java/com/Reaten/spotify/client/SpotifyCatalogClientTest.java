package com.Reaten.spotify.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.Reaten.music.CatalogTrack;
import com.Reaten.spotify.SpotifyProperties;

class SpotifyCatalogClientTest {

	private static final String TOKEN = "{\"access_token\":\"app-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
	private static final String SEARCH = """
			{"tracks":{"items":[{"id":"4fbvXwMTXPWaFyaMWUm9CR","name":"Holocene","duration_ms":336613,
			"external_urls":{"spotify":"https://open.spotify.com/track/4fbvXwMTXPWaFyaMWUm9CR"},
			"artists":[{"name":"Bon Iver"}],
			"album":{"name":"Bon Iver","images":[{"url":"https://i.scdn.co/640"},{"url":"https://i.scdn.co/300"},{"url":"https://i.scdn.co/64"}]}},
			{"name":"sem id"}]}}""";

	private SpotifyStubServer stub;
	private SpotifyCatalogClient client;

	@BeforeEach
	void start() throws Exception {
		stub = new SpotifyStubServer();
		SpotifyProperties properties = new SpotifyProperties("client", "secret", "http://127.0.0.1/cb",
				"playlist-modify-private", stub.baseUrl(), stub.baseUrl() + "/v1");
		client = new SpotifyCatalogClient(properties, new HttpSpotifyAuthClient(properties));
	}

	@AfterEach
	void stop() {
		stub.close();
	}

	@Test
	void searchUsesAnAppTokenCachedAcrossCallsAndParsesTracks() {
		stub.on("POST /api/token", 200, TOKEN).on("GET /v1/search", 200, SEARCH);

		List<CatalogTrack> first = client.searchTracks("track:\"Holocene\" artist:\"Bon Iver\"", 5);
		client.searchTracks("outra busca", 5);

		assertThat(first).singleElement().satisfies(track -> {
			assertThat(track.id()).isEqualTo("4fbvXwMTXPWaFyaMWUm9CR");
			assertThat(track.artists()).containsExactly("Bon Iver");
			assertThat(track.imageUrl()).isEqualTo("https://i.scdn.co/300");
			assertThat(track.durationMs()).isEqualTo(336613);
			assertThat(track.uri()).isEqualTo("spotify:track:4fbvXwMTXPWaFyaMWUm9CR");
		});
		assertThat(stub.requestsTo("POST /api/token")).hasSize(1);
		assertThat(stub.requestsTo("POST /api/token").getFirst().body()).contains("grant_type=client_credentials");
		assertThat(stub.requestsTo("GET /v1/search").getFirst().authorization()).isEqualTo("Bearer app-token");
	}

	@Test
	void limitIsCappedAtTenForDevelopmentMode() {
		stub.on("POST /api/token", 200, TOKEN).on("GET /v1/search", 200, SEARCH);
		client.searchTracks("duna", 50);
		assertThat(stub.requestsTo("GET /v1/search").getFirst().query()).contains("limit=10").contains("type=track");
	}

	@Test
	void expiredAppTokenIsRenewedOnce() {
		stub.on("POST /api/token", 200, TOKEN)
				.on("GET /v1/search", 401, "{}")
				.on("GET /v1/search", 200, SEARCH);

		assertThat(client.searchTracks("holocene", 5)).hasSize(1);
		assertThat(stub.requestsTo("POST /api/token")).hasSize(2);
	}

	@Test
	void rateLimitIsRetriedOnceThenReported() {
		stub.on("POST /api/token", 200, TOKEN).on("GET /v1/search", 429, "{}");

		assertThatThrownBy(() -> client.searchTracks("holocene", 5))
				.isInstanceOf(SpotifyApiException.class)
				.extracting("kind").isEqualTo(SpotifyApiException.Kind.RATE_LIMITED);
		assertThat(stub.requestsTo("GET /v1/search")).hasSize(2);
	}

	@Test
	void findTrackReturnsEmptyForUnknownIdsAndRejectsMalformedOnes() {
		stub.on("POST /api/token", 200, TOKEN).on("GET /v1/tracks/4fbvXwMTXPWaFyaMWUm9CX", 404, "{}");

		assertThat(client.findTrack("4fbvXwMTXPWaFyaMWUm9CX")).isEmpty();
		assertThat(client.findTrack("../../me")).isEmpty();
		assertThat(stub.requestsTo("GET /v1/tracks/4fbvXwMTXPWaFyaMWUm9CX")).hasSize(1);
	}
}
