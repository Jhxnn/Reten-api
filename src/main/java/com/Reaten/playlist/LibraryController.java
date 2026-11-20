package com.Reaten.playlist;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Reaten.auth.AuthenticatedUser;
import com.Reaten.playlist.dto.PlaylistDtos.LibraryResponse;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistSummary;

/**
 * <pre>
 * GET /api/playlists?bookId=&limit=   playlists do usuário em resumo, mais recentes primeiro
 * GET /api/library                    livros escolhidos, cada um com suas trilhas
 * </pre>
 */
@RestController
class LibraryController {

	private final LibraryService library;

	LibraryController(LibraryService library) {
		this.library = library;
	}

	@GetMapping("/api/playlists")
	List<PlaylistSummary> playlists(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) UUID bookId,
			@RequestParam(defaultValue = "50") int limit) {
		return library.playlists(user.id(), bookId, limit);
	}

	@GetMapping("/api/library")
	LibraryResponse library(@AuthenticationPrincipal AuthenticatedUser user) {
		return library.library(user.id());
	}
}
