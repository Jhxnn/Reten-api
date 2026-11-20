package com.Reaten.book.catalog;

import java.util.List;
import java.util.Optional;

/**
 * Porta para catálogos externos de livros. O resto do sistema não sabe se os dados vêm
 * do Open Library ou do Google Books.
 */
public interface BookCatalog {

	/** Identificador da fonte, usado como prefixo do {@link CatalogBook#catalogId()}. */
	String source();

	List<CatalogBook> search(BookQuery query);

	/** Detalhes completos (com sinopse) a partir do id devolvido na busca. */
	Optional<CatalogBook> find(String externalId);
}
