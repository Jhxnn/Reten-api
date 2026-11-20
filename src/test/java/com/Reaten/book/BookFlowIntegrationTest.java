package com.Reaten.book;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

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
import com.Reaten.book.catalog.BookCatalogs;
import com.Reaten.book.catalog.CatalogBook;
import com.Reaten.book.catalog.CatalogUnavailableException;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BookFlowIntegrationTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	BookCatalogs catalogs;

	static CatalogBook book(String id, String title) {
		return new CatalogBook("openlibrary", id, title, null, List.of("Autora"), "Sinopse de " + title,
				List.of("Fantasia"), "2019", "9786555651850", null, "https://covers.test/" + id + ".jpg", 300, "por");
	}

	@Test
	void searchRequiresASession() throws Exception {
		mvc.perform(get("/api/books/search").param("q", "duna")).andExpect(status().isUnauthorized());
	}

	@Test
	void searchReturnsCatalogResultsAndSource() throws Exception {
		Cookie session = register("busca@exemplo.com");
		when(catalogs.searchSource()).thenReturn("openlibrary");
		when(catalogs.search(any())).thenReturn(List.of(book("OL1W:OL1M", "Duna")));

		mvc.perform(get("/api/books/search").param("q", "duna").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("openlibrary"))
				.andExpect(jsonPath("$.results[0].catalogId").value("openlibrary:OL1W:OL1M"))
				.andExpect(jsonPath("$.results[0].title").value("Duna"));
	}

	@Test
	void tooShortQueriesAreRejected() throws Exception {
		Cookie session = register("curta@exemplo.com");
		mvc.perform(get("/api/books/search").param("q", " a ").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("query_too_short"));
	}

	@Test
	void catalogOutageBecomesAClearError() throws Exception {
		Cookie session = register("fora@exemplo.com");
		when(catalogs.search(any())).thenThrow(new CatalogUnavailableException("429", true, null));

		mvc.perform(get("/api/books/search").param("q", "duna").cookie(session))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("books_rate_limited"));
	}

	@Test
	void selectingStoresTheBookOnceAndKeepsRecentOrder() throws Exception {
		Cookie session = register("estante@exemplo.com");
		when(catalogs.find("openlibrary:OL2W:OL2M")).thenReturn(Optional.of(book("OL2W:OL2M", "O Nome do Vento")));
		when(catalogs.find("openlibrary:OL3W:OL3M")).thenReturn(Optional.of(book("OL3W:OL3M", "Dom Casmurro")));

		String firstId = select(session, "openlibrary:OL2W:OL2M");
		select(session, "openlibrary:OL3W:OL3M");
		// Escolher de novo não duplica: o mesmo livro sobe para o topo.
		String againId = select(session, "openlibrary:OL2W:OL2M");
		org.assertj.core.api.Assertions.assertThat(againId).isEqualTo(firstId);

		mvc.perform(get("/api/books/recent").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].title").value("O Nome do Vento"))
				.andExpect(jsonPath("$[1].title").value("Dom Casmurro"));

		mvc.perform(get("/api/books/" + firstId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Sinopse de O Nome do Vento"))
				.andExpect(jsonPath("$.authors[0]").value("Autora"));
	}

	@Test
	void recentBooksArePerUser() throws Exception {
		Cookie ana = register("ana.livros@exemplo.com");
		Cookie bia = register("bia.livros@exemplo.com");
		when(catalogs.find("openlibrary:OL9W")).thenReturn(Optional.of(book("OL9W", "Frankenstein")));

		select(ana, "openlibrary:OL9W");

		mvc.perform(get("/api/books/recent").cookie(bia)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unknownBookIs404() throws Exception {
		Cookie session = register("nada@exemplo.com");
		when(catalogs.find(any())).thenReturn(Optional.empty());

		mvc.perform(post("/api/books/select").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"catalogId\":\"openlibrary:OL404W\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("book_not_found"));
	}

	private String select(Cookie session, String catalogId) throws Exception {
		String body = mvc.perform(post("/api/books/select").with(csrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"catalogId\":\"" + catalogId + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return body.substring(body.indexOf("\"id\":\"") + 6, body.indexOf("\"", body.indexOf("\"id\":\"") + 6));
	}

	private Cookie register(String email) throws Exception {
		return SessionCookies.from(mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"senha-segura-1\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}
}
