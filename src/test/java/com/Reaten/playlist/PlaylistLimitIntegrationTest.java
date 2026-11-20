package com.Reaten.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.Reaten.SessionCookies;
import com.Reaten.TestcontainersConfiguration;
import com.Reaten.ai.MusicRecommender;
import com.Reaten.ai.RecommendationResult;
import com.Reaten.ai.RecommenderException;
import com.Reaten.book.catalog.BookCatalogs;
import com.Reaten.book.catalog.CatalogBook;
import com.Reaten.music.MusicCatalog;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Limite total de playlists guardadas e exclusão (que libera vaga sem contornar o limite diário). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "reten.generation.max-playlists=2", "reten.generation.daily-limit=10" })
class PlaylistLimitIntegrationTest {

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

	@BeforeEach
	void mocks() {
		when(recommender.isConfigured()).thenReturn(true);
		when(recommender.modelName()).thenReturn("modelo-teste");
		when(recommender.recommend(any())).thenReturn(new RecommendationResult("Trilha", "Descrição", "Estilo",
				new RecommendationResult.Atmosphere("Gênero", List.of("calma"), "Lugar", "Época", List.of("tema"), "BAIXA", "Mundo"),
				IntStream.rangeClosed(1, 12).mapToObj(i -> new RecommendationResult.Recommendation("F" + i, "A" + i, "M", "q")).toList()));
		when(books.find(any())).thenReturn(Optional.of(new CatalogBook("openlibrary", "OLLW", "Livro", null, List.of("Autoria"),
				null, List.of(), "2000", null, null, null, null, null)));
	}

	@Test
	void limitBlocksNewPlaylistsUntilOneIsDeleted() throws Exception {
		Cookie session = register("limite.total@exemplo.com");
		String book = selectBook(session);
		String first = generate(session, book);
		generate(session, book);

		start(session, book).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("playlist_limit_reached"));
		JsonNode usage = getJson(session, "/api/playlists/usage");
		assertThat(usage.path("maxPlaylists").asInt()).isEqualTo(2);
		assertThat(usage.path("playlistCount").asInt()).isEqualTo(2);

		mvc.perform(delete("/api/playlists/" + first).with(csrf()).cookie(session)).andExpect(status().isNoContent());

		mvc.perform(get("/api/playlists/" + first).cookie(session)).andExpect(status().isNotFound());
		assertThat(getJson(session, "/api/library").path("playlistCount").asInt()).isEqualTo(1);
		usage = getJson(session, "/api/playlists/usage");
		assertThat(usage.path("playlistCount").asInt()).isEqualTo(1);
		// A tentativa apagada continua contando no limite diário: apagar não devolve gerações.
		assertThat(usage.path("used").asInt()).isEqualTo(2);

		start(session, book).andExpect(status().isAccepted());
	}

	@Test
	void failedGenerationsDoNotOccupyASlot() throws Exception {
		when(recommender.recommend(any())).thenThrow(new RecommenderException(RecommenderException.Reason.UNAVAILABLE, "fora", null));
		Cookie session = register("falhas.limite@exemplo.com");
		String book = selectBook(session);
		for (int i = 0; i < 3; i++) {
			String id = json.readTree(start(session, book).andExpect(status().isAccepted())
					.andReturn().getResponse().getContentAsString()).path("id").asString();
			waitFor(session, id, "FAILED");
		}
		assertThat(getJson(session, "/api/playlists/usage").path("playlistCount").asInt()).isZero();
	}

	@Test
	void onlyTheOwnerCanDelete() throws Exception {
		Cookie owner = register("dona.apagar@exemplo.com");
		Cookie other = register("outra.apagar@exemplo.com");
		String id = generate(owner, selectBook(owner));

		mvc.perform(delete("/api/playlists/" + id).with(csrf()).cookie(other)).andExpect(status().isNotFound());
		mvc.perform(get("/api/playlists/" + id).cookie(owner)).andExpect(status().isOk());
	}

	private String generate(Cookie session, String bookId) throws Exception {
		String id = json.readTree(start(session, bookId).andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString()).path("id").asString();
		waitFor(session, id, "READY");
		return id;
	}

	private org.springframework.test.web.servlet.ResultActions start(Cookie session, String bookId) throws Exception {
		return mvc.perform(post("/api/playlists").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + bookId + "\",\"mode\":\"FOCUS\"}"));
	}

	private void waitFor(Cookie session, String id, String expected) throws Exception {
		for (int i = 0; i < 50; i++) {
			if (expected.equals(getJson(session, "/api/playlists/" + id).path("status").asString())) {
				return;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Status " + expected + " não alcançado");
	}

	private String selectBook(Cookie session) throws Exception {
		return json.readTree(mvc.perform(post("/api/books/select").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"catalogId\":\"openlibrary:OLLW\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString()).path("id").asString();
	}

	private JsonNode getJson(Cookie session, String path) throws Exception {
		return json.readTree(mvc.perform(get(path).cookie(session)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private Cookie register(String email) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}
}
