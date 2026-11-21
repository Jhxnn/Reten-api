package com.Reaten.book.catalog;

import java.util.List;

/**
 * Livro como vem de um catálogo externo, já normalizado.
 *
 * @param source        fonte de origem ({@code openlibrary} ou {@code google})
 * @param externalId    id na fonte
 * @param publishedDate como a fonte informa: {@code 2007}, {@code 2007-03} ou {@code 2007-03-27}
 * @param description   texto puro, sem HTML; pode ser nulo (comum em buscas, preenchido nos detalhes)
 */
public record CatalogBook(
		String source,
		String externalId,
		String title,
		String subtitle,
		List<String> authors,
		String description,
		List<String> categories,
		String publishedDate,
		String isbn13,
		String isbn10,
		String coverUrl,
		Integer pageCount,
		String language) {

	public CatalogBook {
		authors = authors == null ? List.of() : List.copyOf(authors);
		categories = categories == null ? List.of() : List.copyOf(categories);
	}

	/** Id público e estável no Resten: {@code fonte:idExterno}. */
	public String catalogId() {
		return source + ":" + externalId;
	}
}
