package com.Reaten.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
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
import com.Reaten.user.UserRepository;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
		"reten.admin.email=Admin.Teste@Exemplo.com",
		"reten.admin.password=Admin-teste-1" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminIntegrationTest {

	static final String ADMIN_EMAIL = "admin.teste@exemplo.com";

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	UserRepository users;

	@Autowired
	AdminAccountInitializer initializer;

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
		when(recommender.recommend(any())).thenReturn(new RecommendationResult("Trilha do admin", "Descrição", "Estilo",
				new RecommendationResult.Atmosphere("Gênero", List.of("calma"), "Lugar", "Época", List.of("tema"), "BAIXA", "Mundo"),
				IntStream.rangeClosed(1, 12).mapToObj(i -> new RecommendationResult.Recommendation("F" + i, "A" + i, "M", "q")).toList()));
		when(books.find("openlibrary:OLADM")).thenReturn(Optional.of(new CatalogBook("openlibrary", "OLADM", "Duna", null,
				List.of("Frank Herbert"), null, List.of(), "1965", null, null, null, null, null)));
	}

	@Test
	void adminAccountIsCreatedOnStartupAndOnlyOnce() throws Exception {
		assertThat(users.findByEmail(ADMIN_EMAIL)).hasValueSatisfying(admin -> {
			assertThat(admin.isAdmin()).isTrue();
			assertThat(admin.isEmailVerified()).isTrue();
		});

		initializer.run(new DefaultApplicationArguments());

		assertThat(users.findAll().stream().filter(u -> u.getEmail().equals(ADMIN_EMAIL))).hasSize(1);
		JsonNode me = getJson(login(ADMIN_EMAIL, "Admin-teste-1"), "/api/auth/me");
		assertThat(me.path("admin").asBoolean()).isTrue();
	}

	@Test
	void adminSeesWhoUsedTheSystemAndEveryPlaylistIncludingDeletedOnes() throws Exception {
		Cookie reader = register("leitor.visto@exemplo.com");
		String book = json.readTree(mvc.perform(post("/api/books/select").with(csrf()).cookie(reader)
				.contentType(MediaType.APPLICATION_JSON).content("{\"catalogId\":\"openlibrary:OLADM\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asString();
		String kept = generate(reader, book);
		String removed = generate(reader, book);
		mvc.perform(delete("/api/playlists/" + removed).with(csrf()).cookie(reader)).andExpect(status().isNoContent());

		Cookie admin = login(ADMIN_EMAIL, "Admin-teste-1");

		JsonNode overview = getJson(admin, "/api/admin/overview");
		assertThat(overview.path("users").asLong()).isGreaterThanOrEqualTo(2);
		assertThat(overview.path("deletedPlaylists").asLong()).isGreaterThanOrEqualTo(1);

		JsonNode readerRow = find(getJson(admin, "/api/admin/users"), "email", "leitor.visto@exemplo.com");
		assertThat(readerRow.path("playlists").asLong()).isEqualTo(2);
		assertThat(readerRow.path("admin").asBoolean()).isFalse();
		assertThat(readerRow.path("lastSeenAt").isNull()).as("último acesso registrado").isFalse();

		JsonNode playlists = getJson(admin, "/api/admin/playlists?userId=" + readerRow.path("id").asString());
		assertThat(playlists).hasSize(2);
		assertThat(find(playlists, "id", removed).path("deletedAt").isNull()).isFalse();
		JsonNode active = find(playlists, "id", kept);
		assertThat(active.path("deletedAt").isNull()).isTrue();
		assertThat(active.path("ownerEmail").asString()).isEqualTo("leitor.visto@exemplo.com");
		assertThat(active.path("bookTitle").asString()).isEqualTo("Duna");

		JsonNode detail = getJson(admin, "/api/admin/playlists/" + kept);
		assertThat(detail.path("playlist").path("name").asString()).isEqualTo("Trilha do admin");
		assertThat(detail.path("tracks").isArray()).isTrue();
	}

	@Test
	void adminAreaIsClosedToReadersAndVisitors() throws Exception {
		Cookie reader = register("curioso.admin@exemplo.com");

		assertThat(getJson(reader, "/api/auth/me").path("admin").asBoolean()).isFalse();
		mvc.perform(get("/api/admin/users").cookie(reader)).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
	}

	private String generate(Cookie session, String bookId) throws Exception {
		String id = json.readTree(mvc.perform(post("/api/playlists").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":\"" + bookId + "\",\"mode\":\"THEMATIC\"}"))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString()).path("id").asString();
		for (int i = 0; i < 50; i++) {
			if ("READY".equals(getJson(session, "/api/playlists/" + id).path("status").asString())) {
				return id;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Geração não terminou");
	}

	private static JsonNode find(JsonNode array, String field, String value) {
		return StreamSupport.stream(array.spliterator(), false)
				.filter(node -> value.equals(node.path(field).asString()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Não encontrado: " + value));
	}

	private JsonNode getJson(Cookie session, String path) throws Exception {
		return json.readTree(mvc.perform(get(path).cookie(session)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private Cookie login(String email, String password) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse());
	}

	private Cookie register(String email) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"Senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}
}
