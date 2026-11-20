package com.Reaten.book.catalog;

import static com.Reaten.book.catalog.CatalogHttp.integer;
import static com.Reaten.book.catalog.CatalogHttp.text;
import static com.Reaten.book.catalog.CatalogHttp.texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import tools.jackson.databind.JsonNode;

/**
 * Open Library (https://openlibrary.org/developers/api). Não exige chave.
 * <p>
 * A busca usa {@code lang=por}, que faz a Open Library devolver, para cada obra, a edição em
 * português quando ela existe — assim título, ISBN e capa são os da edição brasileira/portuguesa.
 * O id externo combina obra e edição: {@code OL8479867W:OL46535585M}.
 */
@Component
class OpenLibraryCatalog implements BookCatalog {

	static final String SOURCE = "openlibrary";

	private static final String SEARCH_FIELDS = String.join(",",
			"key", "title", "subtitle", "author_name", "first_publish_year", "isbn", "cover_i", "subject",
			"language", "number_of_pages_median", "editions", "editions.key", "editions.title",
			"editions.subtitle", "editions.isbn", "editions.cover_i", "editions.language", "editions.publish_date");
	private static final Pattern YEAR = Pattern.compile("(1[5-9]\\d{2}|20\\d{2})");
	private static final Pattern WORK_ID = Pattern.compile("OL\\d+W");
	private static final Pattern EDITION_ID = Pattern.compile("OL\\d+M");

	private final RestClient client;
	private final BooksProperties properties;

	OpenLibraryCatalog(BooksProperties properties) {
		this.properties = properties;
		this.client = CatalogHttp.client(properties.openLibraryBaseUrl());
	}

	@Override
	public String source() {
		return SOURCE;
	}

	@Override
	public List<CatalogBook> search(BookQuery query) {
		JsonNode body = CatalogHttp.call("Open Library", () -> client.get()
				.uri(uri -> searchUri(uri, query))
				.retrieve()
				.body(JsonNode.class));
		List<CatalogBook> books = new ArrayList<>();
		for (JsonNode doc : body.path("docs")) {
			toSummary(doc).ifPresent(books::add);
		}
		return books;
	}

	private java.net.URI searchUri(UriBuilder uri, BookQuery query) {
		uri.path("/search.json");
		switch (query.field()) {
			case TITLE -> uri.queryParam("title", query.text());
			case AUTHOR -> uri.queryParam("author", query.text());
			case ISBN -> uri.queryParam("isbn", query.text());
			case ALL -> uri.queryParam("q", query.text());
		}
		return uri.queryParam("lang", "por")
				.queryParam("limit", properties.maxResults())
				.queryParam("fields", SEARCH_FIELDS)
				.build();
	}

	private Optional<CatalogBook> toSummary(JsonNode doc) {
		String workKey = text(doc.path("key"));
		if (workKey == null) {
			return Optional.empty();
		}
		String workId = workKey.replace("/works/", "");
		JsonNode edition = doc.path("editions").path("docs").path(0);
		String editionKey = text(edition.path("key"));
		String externalId = editionKey == null ? workId : workId + ":" + editionKey.replace("/books/", "");

		String title = firstNonNull(text(edition.path("title")), text(doc.path("title")));
		if (title == null) {
			return Optional.empty();
		}
		List<String> isbns = edition.path("isbn").isArray() ? texts(edition.path("isbn")) : texts(doc.path("isbn"));
		Integer coverId = edition.path("cover_i").isNumber() ? integer(edition.path("cover_i")) : integer(doc.path("cover_i"));
		String year = firstNonNull(year(texts(edition.path("publish_date"))), text(doc.path("first_publish_year")));

		return Optional.of(new CatalogBook(SOURCE, externalId,
				title,
				firstNonNull(text(edition.path("subtitle")), text(doc.path("subtitle"))),
				CatalogText.distinct(texts(doc.path("author_name")), 4),
				null,
				categories(texts(doc.path("subject"))),
				year,
				CatalogText.isbn13(isbns),
				CatalogText.isbn10(isbns),
				coverUrl(coverId, "M"),
				integer(doc.path("number_of_pages_median")),
				firstNonNull(texts(edition.path("language")).stream().findFirst().orElse(null), null)));
	}

	@Override
	public Optional<CatalogBook> find(String externalId) {
		String[] parts = externalId.split(":");
		if (!WORK_ID.matcher(parts[0]).matches() || (parts.length > 1 && !EDITION_ID.matcher(parts[1]).matches())) {
			return Optional.empty();
		}
		// Cada chamada à Open Library leva ~1–2 s: obra e edição vão em paralelo, depois os autores em paralelo.
		JsonNode work;
		JsonNode edition;
		List<String> authors;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<JsonNode> workCall = executor.submit(() -> get("/works/" + parts[0] + ".json"));
			Future<JsonNode> editionCall = parts.length > 1 ? executor.submit(() -> get("/books/" + parts[1] + ".json")) : null;
			try {
				work = await(workCall);
			} catch (CatalogUnavailableException ex) {
				if (ex.getCause() instanceof RestClientResponseException http && http.getStatusCode().value() == 404) {
					return Optional.empty();
				}
				throw ex;
			}
			List<Future<JsonNode>> authorCalls = new ArrayList<>();
			for (JsonNode author : work.path("authors")) {
				String key = text(author.path("author").path("key"));
				if (key != null && key.matches("/authors/OL\\d+A") && authorCalls.size() < 3) {
					authorCalls.add(executor.submit(() -> get(key + ".json")));
				}
			}
			edition = editionCall == null ? null : await(editionCall);
			authors = new ArrayList<>();
			for (Future<JsonNode> call : authorCalls) {
				String name = text(await(call).path("name"));
				if (name != null) {
					authors.add(name);
				}
			}
		}

		JsonNode description = work.path("description");
		String descriptionText = description.isObject() ? text(description.path("value")) : text(description);
		List<String> isbns = new ArrayList<>();
		Integer coverId = null;
		String title = text(work.path("title"));
		String subtitle = text(work.path("subtitle"));
		String published = null;
		Integer pages = null;
		String language = null;
		if (edition != null) {
			isbns.addAll(texts(edition.path("isbn_13")));
			isbns.addAll(texts(edition.path("isbn_10")));
			coverId = firstPositive(edition.path("covers"));
			title = firstNonNull(text(edition.path("title")), title);
			subtitle = firstNonNull(text(edition.path("subtitle")), subtitle);
			published = year(List.of(firstNonNull(text(edition.path("publish_date")), "")));
			pages = integer(edition.path("number_of_pages"));
			String languageKey = text(edition.path("languages").path(0).path("key"));
			language = languageKey == null ? null : languageKey.replace("/languages/", "");
		}
		if (coverId == null) {
			coverId = firstPositive(work.path("covers"));
		}
		if (published == null) {
			published = year(List.of(firstNonNull(text(work.path("first_publish_date")), "")));
		}

		return Optional.of(new CatalogBook(SOURCE, externalId, title, subtitle,
				CatalogText.distinct(authors, 4),
				CatalogText.cleanDescription(descriptionText),
				categories(texts(work.path("subjects"))),
				published,
				CatalogText.isbn13(isbns),
				CatalogText.isbn10(isbns),
				coverUrl(coverId, "L"),
				pages,
				language));
	}

	private JsonNode get(String path) {
		return CatalogHttp.call("Open Library", () -> client.get().uri(path).retrieve().body(JsonNode.class));
	}

	private static JsonNode await(Future<JsonNode> call) {
		try {
			return call.get();
		} catch (ExecutionException ex) {
			if (ex.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new CatalogUnavailableException("Open Library falhou", false, ex.getCause());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new CatalogUnavailableException("Consulta interrompida", false, ex);
		}
	}

	/** Os assuntos do Open Library são livres e ruidosos; mantemos poucos e legíveis. */
	private static List<String> categories(List<String> subjects) {
		return CatalogText.distinct(subjects.stream()
				.filter(s -> !s.contains(":") && s.length() <= 40 && !s.matches(".*\\d{4}.*"))
				.toList(), 5);
	}

	private String coverUrl(Integer coverId, String size) {
		return coverId == null || coverId <= 0 ? null : properties.openLibraryCoversUrl() + "/b/id/" + coverId + "-" + size + ".jpg";
	}

	private static Integer firstPositive(JsonNode covers) {
		for (JsonNode cover : covers) {
			if (cover.isNumber() && cover.asInt() > 0) {
				return cover.asInt();
			}
		}
		return null;
	}

	private static String year(List<String> dates) {
		for (String date : dates) {
			Matcher matcher = YEAR.matcher(date);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return null;
	}

	private static String firstNonNull(String a, String b) {
		return a != null ? a : b;
	}
}
