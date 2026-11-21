package com.Reaten.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "reten.generation.daily-limit=3")
class PlaylistGenerationIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	MusicRecommender recommender;

	@MockitoBean
	BookCatalogs catalogs;

	// Catálogo musical "não configurado": aqui só interessa a geração com IA.
	@MockitoBean
	MusicCatalog musicCatalog;

	@BeforeEach
	void configureMocks() {
		when(recommender.isConfigured()).thenReturn(true);
		when(recommender.modelName()).thenReturn("modelo-teste");
		when(catalogs.find(any())).thenReturn(Optional.of(new CatalogBook("openlibrary", "OL1W", "O nome do vento", null,
				List.of("Patrick Rothfuss"), "Kvothe conta sua história.", List.of("Fantasia"), "2019", null, null, null, 700, "por")));
	}

	static RecommendationResult aiResult() {
		return new RecommendationResult("Silêncio em três partes", "Para ler devagar.", "Trilha orquestral",
				new RecommendationResult.Atmosphere("Fantasia heroica", List.of("melancolia", "mistério"), "Uma pousada",
						"Medieval", List.of("memória", "música"), "MEDIA", "Magia ligada a nomes"),
				IntStream.rangeClosed(1, 24)
						.mapToObj(i -> new RecommendationResult.Recommendation("Faixa " + i, "Artista " + i,
								"Combina com a pousada.", "Faixa " + i + " Artista " + i))
						.toList());
	}

	@Test
	void generationRunsInBackgroundAndReturnsUnverifiedSuggestions() throws Exception {
		when(recommender.recommend(any())).thenReturn(aiResult());
		Cookie session = register("gera@exemplo.com");
		String bookId = selectBook(session);

		JsonNode created = json.readTree(mvc.perform(post("/api/playlists").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + bookId + "\",\"mode\":\"THEMATIC\",\"notes\":\"Me lembrou a infância\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("GENERATING"))
				.andReturn().getResponse().getContentAsString());

		JsonNode ready = waitForStatus(session, created.path("id").asString(), "READY");
		assertThat(ready.path("name").asString()).isEqualTo("Silêncio em três partes");
		assertThat(ready.path("userNotes").asString()).isEqualTo("Me lembrou a infância");
		assertThat(ready.path("atmosphere").path("moods")).hasSize(2);
		assertThat(ready.path("suggestions")).hasSize(24);
		assertThat(ready.path("suggestions").path(0).path("matchStatus").asString()).isEqualTo("PENDING");
		assertThat(ready.path("verificationStatus").asString()).isEqualTo("NOT_CONFIGURED");
		assertThat(ready.path("tracks")).isEmpty();
		assertThat(ready.path("book").path("title").asString()).isEqualTo("O nome do vento");
	}

	@Test
	void aiFailureBecomesAReadableFailedState() throws Exception {
		when(recommender.recommend(any())).thenThrow(
				new RecommenderException(RecommenderException.Reason.REFUSED, "recusou", null));
		Cookie session = register("falha@exemplo.com");
		String id = startGeneration(session, selectBook(session), "VOCAL");

		JsonNode failed = waitForStatus(session, id, "FAILED");
		assertThat(failed.path("failure").path("code").asString()).isEqualTo("ai_refused");
		assertThat(failed.path("failure").path("message").asString()).isNotBlank();
	}

	@Test
	void dailyLimitCountsEveryAttempt() throws Exception {
		when(recommender.recommend(any())).thenReturn(aiResult());
		Cookie session = register("limite@exemplo.com");
		String bookId = selectBook(session);
		for (int i = 0; i < 3; i++) {
			startGeneration(session, bookId, "FOCUS");
		}

		mvc.perform(post("/api/playlists").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + bookId + "\",\"mode\":\"FOCUS\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("generation_limit_reached"));

		mvc.perform(get("/api/playlists/usage").cookie(session))
				.andExpect(jsonPath("$.limit").value(3))
				.andExpect(jsonPath("$.used").value(3))
				.andExpect(jsonPath("$.remaining").value(0))
				.andExpect(jsonPath("$.resetsAt").exists());
	}

	@Test
	void refusesClearlyWhenAiIsNotConfigured() throws Exception {
		when(recommender.isConfigured()).thenReturn(false);
		Cookie session = register("semia@exemplo.com");

		mvc.perform(post("/api/playlists").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + selectBook(session) + "\",\"mode\":\"THEMATIC\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("ai_not_configured"));
		mvc.perform(get("/api/playlists/usage").cookie(session)).andExpect(jsonPath("$.used").value(0));
	}

	@Test
	void validatesNotesLength() throws Exception {
		Cookie session = register("notas@exemplo.com");
		mvc.perform(post("/api/playlists").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + selectBook(session) + "\",\"mode\":\"THEMATIC\",\"notes\":\"" + "a".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.notes").exists());
	}

	@Test
	void playlistsArePrivate() throws Exception {
		when(recommender.recommend(any())).thenReturn(aiResult());
		Cookie owner = register("dona@exemplo.com");
		Cookie other = register("outra@exemplo.com");
		String id = startGeneration(owner, selectBook(owner), "THEMATIC");

		mvc.perform(get("/api/playlists/" + id).cookie(other))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("playlist_not_found"));
	}

	private JsonNode waitForStatus(Cookie session, String id, String expected) throws Exception {
		JsonNode current = null;
		for (int i = 0; i < 50; i++) {
			current = json.readTree(mvc.perform(get("/api/playlists/" + id).cookie(session))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
			if (expected.equals(current.path("status").asString())) {
				return current;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Status esperado " + expected + ", último: " + current);
	}

	private String startGeneration(Cookie session, String bookId, String mode) throws Exception {
		return json.readTree(mvc.perform(post("/api/playlists").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookId\":\"" + bookId + "\",\"mode\":\"" + mode + "\"}"))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString()).path("id").asString();
	}

	private String selectBook(Cookie session) throws Exception {
		return json.readTree(mvc.perform(post("/api/books/select").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"catalogId\":\"openlibrary:OL1W\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString()).path("id").asString();
	}

	private Cookie register(String email) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"Senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}
}
