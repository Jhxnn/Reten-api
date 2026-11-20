package com.Reaten.book;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Reaten.auth.AuthenticatedUser;
import com.Reaten.book.catalog.BookQuery;
import com.Reaten.book.dto.BookDtos.BookResponse;
import com.Reaten.book.dto.BookDtos.CatalogBookResponse;
import com.Reaten.book.dto.BookDtos.SearchResponse;
import com.Reaten.book.dto.BookDtos.SelectBookRequest;

import jakarta.validation.Valid;

/**
 * <pre>
 * GET  /api/books/search?q=&by=all|title|author|isbn   busca no catálogo externo
 * GET  /api/books/catalog?id=fonte:id                  detalhes (com sinopse) antes de escolher
 * POST /api/books/select                               escolhe o livro: guarda no Reten e no histórico
 * GET  /api/books/recent                               livros escolhidos recentemente pelo usuário
 * GET  /api/books/{id}                                 livro já guardado
 * </pre>
 */
@RestController
@RequestMapping("/api/books")
class BookController {

	private final BookService service;

	BookController(BookService service) {
		this.service = service;
	}

	@GetMapping("/search")
	SearchResponse search(@RequestParam String q, @RequestParam(defaultValue = "all") String by) {
		return service.search(q, parseField(by));
	}

	@GetMapping("/catalog")
	CatalogBookResponse preview(@RequestParam("id") String catalogId) {
		return service.preview(catalogId);
	}

	@PostMapping("/select")
	BookResponse select(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody SelectBookRequest request) {
		return service.select(user.id(), request.catalogId());
	}

	@GetMapping("/recent")
	List<BookResponse> recent(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.recent(user.id());
	}

	@GetMapping("/{id}")
	BookResponse get(@PathVariable UUID id) {
		return service.get(id);
	}

	private static BookQuery.Field parseField(String by) {
		return switch (by.toLowerCase()) {
			case "title" -> BookQuery.Field.TITLE;
			case "author" -> BookQuery.Field.AUTHOR;
			case "isbn" -> BookQuery.Field.ISBN;
			default -> BookQuery.Field.ALL;
		};
	}
}
