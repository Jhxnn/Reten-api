package com.Reaten.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.Reaten.SessionCookies;
import com.Reaten.TestcontainersConfiguration;
import com.Reaten.ai.MusicRecommender;
import com.Reaten.ai.RecommendationResult;
import com.Reaten.book.catalog.BookCatalogs;
import com.Reaten.book.catalog.CatalogBook;
import com.Reaten.music.MusicCatalog;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LibraryIntegrationTest {

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
		when(books.find("openlibrary:OLAW")).thenReturn(Optional.of(book("OLAW", "Duna")));
		when(books.find("openlibrary:OLBW")).thenReturn(Optional.of(book("OLBW", "Frankenstein")));
	}

	static CatalogBook book(String id, String title) {
		return new CatalogBook("openlibrary", id, title, null, List.of("Autoria"), null, List.of(), "1965", null, null, null, null, null);
	}

	@Test
	void libraryGroupsPlaylistsByBookAndIncludesBooksWithoutPlaylists() throws Exception {
		Cookie session = register("estante.biblioteca@exemplo.com");
		String frankenstein = select(session, "openlibrary:OLBW");
		String duna = select(session, "openlibrary:OLAW");
		generate(session, duna, "THEMATIC");
		generate(session, duna, "FOCUS");

		JsonNode library = getJson(session, "/api/library");
		assertThat(library.path("bookCount").asInt()).isEqualTo(2);
		assertThat(library.path("playlistCount").asInt()).isEqualTo(2);
		JsonNode first = library.path("books").path(0);
		assertThat(first.path("book").path("title").asString()).isEqualTo("Duna");
		assertThat(first.path("playlists")).hasSize(2);
		assertThat(first.path("playlists").path(0).path("mode").asString()).isEqualTo("FOCUS");
		assertThat(first.path("playlists").path(0).path("trackCount").asLong()).isZero();
		JsonNode second = library.path("books").path(1);
		assertThat(second.path("book").path("id").asString()).isEqualTo(frankenstein);
		assertThat(second.path("playlists")).isEmpty();
	}

	@Test
	void playlistListCanBeFilteredByBookAndIsPrivate() throws Exception {
		Cookie owner = register("dono.lista@exemplo.com");
		Cookie other = register("outro.lista@exemplo.com");
		String duna = select(owner, "openlibrary:OLAW");
		String frankenstein = select(owner, "openlibrary:OLBW");
		generate(owner, duna, "VOCAL");
		generate(owner, frankenstein, "THEMATIC");

		assertThat(getJson(owner, "/api/playlists")).hasSize(2);
		JsonNode filtered = getJson(owner, "/api/playlists?bookId=" + duna);
		assertThat(filtered).hasSize(1);
		assertThat(filtered.path(0).path("book").path("title").asString()).isEqualTo("Duna");
		assertThat(getJson(other, "/api/playlists")).isEmpty();
		assertThat(getJson(other, "/api/library").path("books")).isEmpty();
	}

	private void generate(Cookie session, String bookId, String mode) throws Exception {
		String id = json.readTree(mvc.perform(post("/api/playlists").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":\"" + bookId + "\",\"mode\":\"" + mode + "\"}"))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString()).path("id").asString();
		for (int i = 0; i < 50; i++) {
			if ("READY".equals(getJson(session, "/api/playlists/" + id).path("status").asString())) {
				return;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Geração não terminou");
	}

	private String select(Cookie session, String catalogId) throws Exception {
		return json.readTree(mvc.perform(post("/api/books/select").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"catalogId\":\"" + catalogId + "\"}"))
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
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"Senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}
}
