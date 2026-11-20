package com.Reaten.book.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Testa o adaptador contra um servidor HTTP local com respostas no formato real da Open Library. */
class OpenLibraryCatalogTest {

	private HttpServer server;
	private final Map<String, String> responses = new ConcurrentHashMap<>();
	private final List<String> requested = new java.util.concurrent.CopyOnWriteArrayList<>();
	private OpenLibraryCatalog catalog;
	private volatile int status = 200;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			requested.add(exchange.getRequestURI().toString());
			String body = responses.getOrDefault(path, "{}");
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(responses.containsKey(path) ? status : 404, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		catalog = new OpenLibraryCatalog(new BooksProperties(null, "unused", base, "https://covers.test", 20));
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void searchPrefersThePortugueseEdition() {
		responses.put("/search.json", """
				{"docs":[{"key":"/works/OL8479867W","title":"The Name of the Wind","author_name":["Patrick Rothfuss","Patrick Rothfuss"],
				"first_publish_year":2007,"cover_i":11480483,"subject":["Fantasy","nyt:hardcover-fiction=2011","Magic"],
				"editions":{"docs":[{"key":"/books/OL46535585M","title":"O nome do vento","cover_i":13234856,
				"language":["por"],"publish_date":["Feb 03, 2019"],"isbn":["9786555651850","6555651857"]}]}}]}""");

		List<CatalogBook> results = catalog.search(BookQuery.of("o nome do vento", BookQuery.Field.ALL));

		assertThat(results).singleElement().satisfies(book -> {
			assertThat(book.catalogId()).isEqualTo("openlibrary:OL8479867W:OL46535585M");
			assertThat(book.title()).isEqualTo("O nome do vento");
			assertThat(book.authors()).containsExactly("Patrick Rothfuss");
			assertThat(book.categories()).containsExactly("Fantasy", "Magic");
			assertThat(book.publishedDate()).isEqualTo("2019");
			assertThat(book.isbn13()).isEqualTo("9786555651850");
			assertThat(book.coverUrl()).isEqualTo("https://covers.test/b/id/13234856-M.jpg");
		});
		assertThat(requested.getFirst()).contains("lang=por").contains("q=o%20nome%20do%20vento");
	}

	@Test
	void searchByIsbnUsesTheIsbnParameter() {
		responses.put("/search.json", "{\"docs\":[]}");
		catalog.search(BookQuery.of("978-6555651850", BookQuery.Field.ALL));
		assertThat(requested.getFirst()).contains("isbn=9786555651850");
	}

	@Test
	void findLoadsDescriptionAuthorsAndEditionData() {
		responses.put("/works/OL8479867W.json", """
				{"title":"The Name of the Wind","covers":[11480483],
				"authors":[{"author":{"key":"/authors/OL1394865A"}}],
				"description":{"type":"/type/text","value":"Told in Kvothe's own voice.\\r\\n\\r\\n----------\\r\\nAlso contained in: box"},
				"subjects":["Fantasy fiction"]}""");
		responses.put("/books/OL46535585M.json", """
				{"title":"O nome do vento","isbn_13":["9786555651850"],"publish_date":"Feb 03, 2019",
				"covers":[13234856],"number_of_pages":656,"languages":[{"key":"/languages/por"}]}""");
		responses.put("/authors/OL1394865A.json", "{\"name\":\"Patrick Rothfuss\"}");

		CatalogBook book = catalog.find("OL8479867W:OL46535585M").orElseThrow();

		assertThat(book.title()).isEqualTo("O nome do vento");
		assertThat(book.authors()).containsExactly("Patrick Rothfuss");
		assertThat(book.description()).isEqualTo("Told in Kvothe's own voice.");
		assertThat(book.pageCount()).isEqualTo(656);
		assertThat(book.language()).isEqualTo("por");
		assertThat(book.coverUrl()).isEqualTo("https://covers.test/b/id/13234856-L.jpg");
	}

	@Test
	void findRejectsMalformedIdsWithoutCallingTheApi() {
		assertThat(catalog.find("../../etc")).isEmpty();
		assertThat(requested).isEmpty();
	}

	@Test
	void findReturnsEmptyForUnknownWorks() {
		assertThat(catalog.find("OL1W")).isEmpty();
	}

	@Test
	void rateLimitIsReportedAsSuch() {
		responses.put("/search.json", "{}");
		status = 429;
		assertThatThrownBy(() -> catalog.search(BookQuery.of("duna", BookQuery.Field.ALL)))
				.isInstanceOf(CatalogUnavailableException.class)
				.matches(ex -> ((CatalogUnavailableException) ex).isRateLimited());
	}
}
