package com.Reaten.book.catalog;

import static com.Reaten.book.catalog.CatalogHttp.integer;
import static com.Reaten.book.catalog.CatalogHttp.text;
import static com.Reaten.book.catalog.CatalogHttp.texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

/**
 * Google Books (https://developers.google.com/books). Exige {@code GOOGLE_BOOKS_API_KEY}:
 * a cota anônima da API está zerada.
 */
@Component
class GoogleBooksCatalog implements BookCatalog {

	static final String SOURCE = "google";

	private final RestClient client;
	private final BooksProperties properties;

	GoogleBooksCatalog(BooksProperties properties) {
		this.properties = properties;
		this.client = CatalogHttp.client(properties.googleBaseUrl());
	}

	@Override
	public String source() {
		return SOURCE;
	}

	@Override
	public List<CatalogBook> search(BookQuery query) {
		String q = switch (query.field()) {
			case TITLE -> "intitle:" + query.text();
			case AUTHOR -> "inauthor:" + query.text();
			case ISBN -> "isbn:" + query.text();
			case ALL -> query.text();
		};
		JsonNode body = CatalogHttp.call("Google Books", () -> client.get()
				.uri(uri -> uri.path("/volumes")
						.queryParam("q", q)
						.queryParam("printType", "books")
						.queryParam("maxResults", Math.min(properties.maxResults(), 40))
						.queryParam("key", properties.googleApiKey())
						.build())
				.retrieve()
				.body(JsonNode.class));
		List<CatalogBook> books = new ArrayList<>();
		for (JsonNode item : body.path("items")) {
			toBook(item).ifPresent(books::add);
		}
		return books;
	}

	@Override
	public Optional<CatalogBook> find(String externalId) {
		if (!externalId.matches("[A-Za-z0-9_-]{4,40}")) {
			return Optional.empty();
		}
		try {
			JsonNode body = CatalogHttp.call("Google Books", () -> client.get()
					.uri(uri -> uri.path("/volumes/{id}").queryParam("key", properties.googleApiKey()).build(externalId))
					.retrieve()
					.body(JsonNode.class));
			return toBook(body);
		} catch (CatalogUnavailableException ex) {
			if (ex.getCause() instanceof RestClientResponseException http && http.getStatusCode().value() == 404) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	private Optional<CatalogBook> toBook(JsonNode item) {
		String id = text(item.path("id"));
		JsonNode info = item.path("volumeInfo");
		String title = text(info.path("title"));
		if (id == null || title == null) {
			return Optional.empty();
		}
		List<String> isbns = new ArrayList<>();
		for (JsonNode identifier : info.path("industryIdentifiers")) {
			String value = text(identifier.path("identifier"));
			if (value != null) {
				isbns.add(value);
			}
		}
		// Categorias do Google vêm como "Fiction / Fantasy / Epic": usamos o trecho mais específico.
		List<String> categories = texts(info.path("categories")).stream()
				.map(c -> c.contains("/") ? c.substring(c.lastIndexOf('/') + 1).strip() : c)
				.toList();
		return Optional.of(new CatalogBook(SOURCE, id, title,
				text(info.path("subtitle")),
				CatalogText.distinct(texts(info.path("authors")), 4),
				CatalogText.cleanDescription(text(info.path("description"))),
				CatalogText.distinct(categories, 5),
				text(info.path("publishedDate")),
				CatalogText.isbn13(isbns),
				CatalogText.isbn10(isbns),
				cover(info.path("imageLinks")),
				integer(info.path("pageCount")),
				text(info.path("language"))));
	}

	private static String cover(JsonNode imageLinks) {
		String url = text(imageLinks.path("thumbnail"));
		if (url == null) {
			url = text(imageLinks.path("smallThumbnail"));
		}
		return url == null ? null : url.replace("http://", "https://").replace("&edge=curl", "");
	}
}
