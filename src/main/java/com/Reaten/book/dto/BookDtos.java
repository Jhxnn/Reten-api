package com.Reaten.book.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.Reaten.book.Book;
import com.Reaten.book.catalog.CatalogBook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class BookDtos {

	private BookDtos() {
	}

	/** Livro vindo do catálogo externo, ainda não escolhido. */
	public record CatalogBookResponse(
			String catalogId,
			String title,
			String subtitle,
			List<String> authors,
			String description,
			List<String> categories,
			String publishedDate,
			String isbn13,
			String coverUrl,
			Integer pageCount) {

		public static CatalogBookResponse of(CatalogBook book) {
			return new CatalogBookResponse(book.catalogId(), book.title(), book.subtitle(), book.authors(),
					book.description(), book.categories(), book.publishedDate(), book.isbn13(), book.coverUrl(),
					book.pageCount());
		}
	}

	/** @param source fonte usada na busca: {@code openlibrary} ou {@code google} */
	public record SearchResponse(String source, List<CatalogBookResponse> results) {
	}

	public record SelectBookRequest(@NotBlank @Size(max = 140) String catalogId) {
	}

	/** Livro já guardado no Resten. */
	public record BookResponse(
			UUID id,
			String catalogId,
			String title,
			String subtitle,
			List<String> authors,
			String description,
			List<String> categories,
			String publishedDate,
			String isbn13,
			String coverUrl,
			Integer pageCount,
			Instant lastSelectedAt) {

		public static BookResponse of(Book book, Instant lastSelectedAt) {
			return new BookResponse(book.getId(), book.getSource() + ":" + book.getExternalId(), book.getTitle(),
					book.getSubtitle(), book.getAuthors(), book.getDescription(), book.getCategories(),
					book.getPublishedDate(), book.getIsbn13(), book.getCoverUrl(), book.getPageCount(), lastSelectedAt);
		}
	}
}
