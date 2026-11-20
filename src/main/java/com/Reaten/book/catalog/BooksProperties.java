package com.Reaten.book.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param googleApiKey quando presente, o Google Books passa a ser a fonte de busca (sinopses melhores em
 *                     português). Sem chave, a busca usa o Open Library, que não exige credenciais.
 */
@ConfigurationProperties(prefix = "reten.books")
public record BooksProperties(
		String googleApiKey,
		@DefaultValue("https://www.googleapis.com/books/v1") String googleBaseUrl,
		@DefaultValue("https://openlibrary.org") String openLibraryBaseUrl,
		@DefaultValue("https://covers.openlibrary.org") String openLibraryCoversUrl,
		@DefaultValue("20") int maxResults) {

	public boolean googleConfigured() {
		return googleApiKey != null && !googleApiKey.isBlank();
	}
}
