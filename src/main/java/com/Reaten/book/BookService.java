package com.Reaten.book;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.book.catalog.BookCatalogs;
import com.Reaten.book.catalog.BookQuery;
import com.Reaten.book.catalog.CatalogBook;
import com.Reaten.book.catalog.CatalogUnavailableException;
import com.Reaten.book.dto.BookDtos.BookResponse;
import com.Reaten.book.dto.BookDtos.CatalogBookResponse;
import com.Reaten.book.dto.BookDtos.SearchResponse;
import com.Reaten.shared.exception.ApiException;

@Service
public class BookService {

	private static final Logger log = LoggerFactory.getLogger(BookService.class);
	private static final int RECENT_LIMIT = 12;

	private final BookCatalogs catalogs;
	private final BookRepository books;
	private final UserBookRepository userBooks;

	BookService(BookCatalogs catalogs, BookRepository books, UserBookRepository userBooks) {
		this.catalogs = catalogs;
		this.books = books;
		this.userBooks = userBooks;
	}

	public SearchResponse search(String text, BookQuery.Field field) {
		BookQuery query = BookQuery.of(text, field);
		if (query.text().length() < 2) {
			throw ApiException.badRequest("query_too_short", "Digite pelo menos 2 caracteres para buscar.");
		}
		List<CatalogBookResponse> results = withCatalog(() -> catalogs.search(query)).stream()
				.map(CatalogBookResponse::of)
				.toList();
		return new SearchResponse(catalogs.searchSource(), results);
	}

	public CatalogBookResponse preview(String catalogId) {
		return CatalogBookResponse.of(fetch(catalogId));
	}

	/**
	 * Guarda (ou atualiza) o livro escolhido e registra a escolha no histórico do usuário.
	 * Os dados são sempre buscados de novo na fonte, nunca aceitos do cliente.
	 */
	@Transactional
	public BookResponse select(UUID userId, String catalogId) {
		CatalogBook fresh = fetch(catalogId);
		Book book = books.findBySourceAndExternalId(fresh.source(), fresh.externalId())
				.map(existing -> {
					existing.refresh(fresh);
					return existing;
				})
				.orElseGet(() -> saveNew(fresh));
		Instant now = Instant.now();
		userBooks.recordSelection(userId, book.getId(), now);
		return BookResponse.of(book, now);
	}

	@Transactional(readOnly = true)
	public List<BookResponse> recent(UUID userId) {
		return userBooks.findRecent(userId, Limit.of(RECENT_LIMIT)).stream()
				.map(ub -> BookResponse.of(ub.getBook(), ub.getLastSelectedAt()))
				.toList();
	}

	@Transactional(readOnly = true)
	public BookResponse get(UUID bookId) {
		return books.findById(bookId)
				.map(book -> BookResponse.of(book, null))
				.orElseThrow(() -> ApiException.notFound("book_not_found", "Livro não encontrado."));
	}

	private Book saveNew(CatalogBook fresh) {
		// A constraint única (source, external_id) protege contra duplicatas se dois usuários
		// escolherem o mesmo livro novo no mesmo instante; o segundo recebe erro e pode tentar de novo.
		return books.saveAndFlush(Book.from(fresh));
	}

	private CatalogBook fetch(String catalogId) {
		return withCatalog(() -> catalogs.find(catalogId))
				.orElseThrow(() -> ApiException.notFound("book_not_found", "Não encontramos este livro no catálogo."));
	}

	private <T> T withCatalog(java.util.function.Supplier<T> call) {
		try {
			return call.get();
		} catch (CatalogUnavailableException ex) {
			log.warn("Catálogo de livros indisponível: {}", ex.getMessage());
			if (ex.isRateLimited()) {
				throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "books_rate_limited",
						"O catálogo de livros atingiu o limite de buscas por agora. Tente novamente em alguns minutos.", ex);
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY, "books_unavailable",
					"Não conseguimos consultar o catálogo de livros agora. Tente novamente em instantes.", ex);
		}
	}
}
