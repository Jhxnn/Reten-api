package com.Reaten.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
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
import com.Reaten.ai.MusicRecommender;
import com.Reaten.ai.RecommendationResult;
import com.Reaten.book.catalog.BookCatalogs;
import com.Reaten.book.catalog.CatalogBook;
import com.Reaten.music.CatalogTrack;
import com.Reaten.music.MusicCatalog;
import com.Reaten.music.PlaylistPublisher;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fluxo completo da Fase 5 com IA e Spotify simulados nas portas:
 * verificação das sugestões, edição, substituição e publicação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlaylistSpotifyIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	MusicRecommender recommender;

	@MockitoBean
	BookCatalogs books;

	@MockitoBean
	MusicCatalog catalog;

	@MockitoBean
	PlaylistPublisher publisher;

	static String spotifyId(int i) {
		return String.format("track%017d", i);
	}

	static CatalogTrack catalogTrack(int i) {
		return new CatalogTrack(spotifyId(i), "Faixa " + i, List.of("Artista " + i), "Álbum " + i, "https://i.scdn.co/" + i,
				180_000 + i, "https://open.spotify.com/track/" + spotifyId(i));
	}

	@BeforeEach
	void mocks() {
		when(recommender.isConfigured()).thenReturn(true);
		when(recommender.modelName()).thenReturn("modelo-teste");
		when(books.find(any())).thenReturn(Optional.of(new CatalogBook("openlibrary", "OL1W", "O nome do vento", null,
				List.of("Patrick Rothfuss"), "Sinopse.", List.of(), "2019", null, null, null, null, "por")));
		// 12 sugestões: 1..10 existem; 11 não existe; 12 aponta para a mesma faixa da 1 (duplicata).
		List<RecommendationResult.Recommendation> suggestions = new java.util.ArrayList<>(IntStream.rangeClosed(1, 11)
				.mapToObj(i -> new RecommendationResult.Recommendation("Faixa " + i, "Artista " + i, "Motivo " + i, "q"))
				.toList());
		suggestions.add(new RecommendationResult.Recommendation("Faixa 1", "Artista 1 & Convidado", "Motivo repetido", "q"));
		when(recommender.recommend(any())).thenReturn(new RecommendationResult("Silêncio", "Descrição", "Orquestral",
				new RecommendationResult.Atmosphere("Fantasia", List.of("melancolia"), "Pousada", "Medieval",
						List.of("memória"), "MEDIA", "Mundo"),
				suggestions));
		when(catalog.isConfigured()).thenReturn(true);
		when(catalog.searchTracks(anyString(), anyInt())).thenAnswer(inv -> {
			String query = inv.getArgument(0);
			for (int i = 10; i >= 1; i--) {
				if (query.contains("Faixa " + i + "\"") || query.startsWith("Faixa " + i + " ")) {
					return List.of(catalogTrack(i));
				}
			}
			return List.of();
		});
		when(publisher.isConfigured()).thenReturn(true);
	}

	@Test
	void onlyTracksFoundInTheCatalogBecomePlaylistTracks() throws Exception {
		Cookie session = register("verifica@exemplo.com");
		JsonNode ready = generate(session);

		assertThat(ready.path("verificationStatus").asString()).isEqualTo("VERIFIED");
		assertThat(ready.path("tracks")).hasSize(10);
		assertThat(ready.path("tracks").path(0).path("spotifyUrl").asString()).startsWith("https://open.spotify.com/track/");
		assertThat(ready.path("tracks").path(0).path("reason").asString()).isEqualTo("Motivo 1");
		assertThat(ready.path("suggestions").path(10).path("matchStatus").asString()).isEqualTo("NOT_FOUND");
		assertThat(ready.path("suggestions").path(11).path("matchStatus").asString()).isEqualTo("DUPLICATE");
	}

	@Test
	void catalogFailureKeepsThePlaylistAndCanBeRetried() throws Exception {
		when(catalog.searchTracks(anyString(), anyInt())).thenThrow(new IllegalStateException("Spotify fora do ar"));
		Cookie session = register("falha.spotify@exemplo.com");
		JsonNode ready = generate(session);
		assertThat(ready.path("verificationStatus").asString()).isEqualTo("FAILED");
		assertThat(ready.path("tracks")).isEmpty();

		org.mockito.Mockito.reset(catalog);
		mocks();
		mvc.perform(post("/api/playlists/" + ready.path("id").asString() + "/verify").with(csrf()).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
				.andExpect(jsonPath("$.tracks.length()").value(10));
	}

	@Test
	void reverifyingAnAlreadyVerifiedPlaylistReplacesItsTracks() throws Exception {
		// Regressão: as mesmas faixas voltando violavam (playlist, spotify_track_id), porque o Hibernate
		// inseria as novas antes de apagar as antigas.
		Cookie session = register("reverifica@exemplo.com");
		JsonNode ready = generate(session);
		String id = ready.path("id").asString();

		for (int round = 0; round < 2; round++) {
			mvc.perform(post("/api/playlists/" + id + "/verify").with(csrf()).cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
					.andExpect(jsonPath("$.tracks.length()").value(10));
		}
	}

	@Test
	void editingRemovingAndReplacingTracks() throws Exception {
		Cookie session = register("edita@exemplo.com");
		JsonNode ready = generate(session);
		String id = ready.path("id").asString();
		String firstTrack = ready.path("tracks").path(0).path("id").asString();

		mvc.perform(patch("/api/playlists/" + id).with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Noites na pousada\",\"description\":\"Nova descrição\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Noites na pousada"));

		mvc.perform(delete("/api/playlists/" + id + "/tracks/" + firstTrack).with(csrf()).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tracks.length()").value(9))
				.andExpect(jsonPath("$.tracks[0].position").value(1))
				.andExpect(jsonPath("$.tracks[0].name").value("Faixa 2"));

		// A busca de substituições esconde o que já está na playlist.
		when(catalog.searchTracks(eq("faixa"), anyInt())).thenReturn(List.of(catalogTrack(2), catalogTrack(50)));
		mvc.perform(get("/api/playlists/" + id + "/replacements").param("q", "faixa").cookie(session))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].spotifyTrackId").value(spotifyId(50)));

		when(catalog.findTrack(spotifyId(50))).thenReturn(Optional.of(catalogTrack(50)));
		mvc.perform(post("/api/playlists/" + id + "/tracks").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"spotifyTrackId\":\"" + spotifyId(50) + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tracks.length()").value(10))
				.andExpect(jsonPath("$.tracks[9].source").value("MANUAL"));

		mvc.perform(post("/api/playlists/" + id + "/tracks").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"spotifyTrackId\":\"" + spotifyId(50) + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("track_already_added"));
	}

	@Test
	void publishingCreatesThenUpdatesTheSameSpotifyPlaylist() throws Exception {
		Cookie session = register("publica@exemplo.com");
		JsonNode ready = generate(session);
		String id = ready.path("id").asString();
		when(publisher.publish(any(UUID.class), isNull(), anyString(), any(), any()))
				.thenReturn(new PlaylistPublisher.PublishedPlaylist("sp123", "https://open.spotify.com/playlist/sp123"));

		mvc.perform(post("/api/playlists/" + id + "/spotify").with(csrf()).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spotify.playlistUrl").value("https://open.spotify.com/playlist/sp123"))
				.andExpect(jsonPath("$.spotify.hasUnsavedChanges").value(false));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass(List.class);
		verify(publisher).publish(any(UUID.class), isNull(), eq("Silêncio"), eq("Descrição"), ids.capture());
		assertThat(ids.getValue()).hasSize(10).first().isEqualTo(spotifyId(1));

		mvc.perform(patch("/api/playlists/" + id).with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Outro nome\"}"))
				.andExpect(jsonPath("$.spotify.hasUnsavedChanges").value(true));

		when(publisher.publish(any(UUID.class), eq("sp123"), anyString(), any(), any()))
				.thenReturn(new PlaylistPublisher.PublishedPlaylist("sp123", "https://open.spotify.com/playlist/sp123"));
		mvc.perform(post("/api/playlists/" + id + "/spotify").with(csrf()).cookie(session))
				.andExpect(jsonPath("$.spotify.hasUnsavedChanges").value(false));
		verify(publisher).publish(any(UUID.class), eq("sp123"), eq("Outro nome"), isNull(), any());
	}

	@Test
	void unverifiedPlaylistsCannotBeEditedOrPublished() throws Exception {
		when(catalog.isConfigured()).thenReturn(false);
		Cookie session = register("naoverificada@exemplo.com");
		String id = generate(session).path("id").asString();

		mvc.perform(post("/api/playlists/" + id + "/spotify").with(csrf()).cookie(session))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("playlist_not_verified"));
	}

	private JsonNode generate(Cookie session) throws Exception {
		String bookId = json.readTree(mvc.perform(post("/api/books/select").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"catalogId\":\"openlibrary:OL1W\"}"))
				.andReturn().getResponse().getContentAsString()).path("id").asString();
		String id = json.readTree(mvc.perform(post("/api/playlists").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":\"" + bookId + "\",\"mode\":\"VOCAL\"}"))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString()).path("id").asString();
		for (int i = 0; i < 50; i++) {
			JsonNode current = json.readTree(mvc.perform(get("/api/playlists/" + id).cookie(session))
					.andReturn().getResponse().getContentAsString());
			if ("READY".equals(current.path("status").asString())) {
				return current;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Geração não terminou");
	}

	private Cookie register(String email) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"Senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}
}
