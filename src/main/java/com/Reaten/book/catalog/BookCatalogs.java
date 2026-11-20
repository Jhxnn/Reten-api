package com.Reaten.book.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Escolhe o catálogo: busca no Google Books quando há chave, senão no Open Library.
 * Detalhes são sempre buscados na fonte de onde o livro veio (prefixo do catalogId).
 */
@Component
public class BookCatalogs {

	private final Map<String, BookCatalog> bySource;
	private final BookCatalog searchCatalog;

	BookCatalogs(List<BookCatalog> catalogs, BooksProperties properties) {
		this.bySource = catalogs.stream().collect(Collectors.toMap(BookCatalog::source, Function.identity()));
		this.searchCatalog = bySource.get(properties.googleConfigured() ? GoogleBooksCatalog.SOURCE : OpenLibraryCatalog.SOURCE);
	}

	public String searchSource() {
		return searchCatalog.source();
	}

	public List<CatalogBook> search(BookQuery query) {
		return searchCatalog.search(query);
	}

	public Optional<CatalogBook> find(String catalogId) {
		int separator = catalogId == null ? -1 : catalogId.indexOf(':');
		if (separator <= 0) {
			return Optional.empty();
		}
		BookCatalog catalog = bySource.get(catalogId.substring(0, separator));
		return catalog == null ? Optional.empty() : catalog.find(catalogId.substring(separator + 1));
	}
}
