package com.Reaten.playlist;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.book.Book;
import com.Reaten.book.BookService;
import com.Reaten.book.dto.BookDtos.BookResponse;
import com.Reaten.playlist.dto.PlaylistDtos.BookSummary;
import com.Reaten.playlist.dto.PlaylistDtos.LibraryBook;
import com.Reaten.playlist.dto.PlaylistDtos.LibraryResponse;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistSummary;

/** Histórico do usuário: playlists em lista e a biblioteca agrupada por livro. */
@Service
public class LibraryService {

	static final int MAX_PLAYLISTS = 500;
	static final int MAX_BOOKS = 200;

	private final PlaylistRepository playlists;
	private final BookService books;
	private final GenerationProperties properties;

	LibraryService(PlaylistRepository playlists, BookService books, GenerationProperties properties) {
		this.playlists = playlists;
		this.books = books;
		this.properties = properties;
	}

	@Transactional
	public List<PlaylistSummary> playlists(UUID userId, UUID bookId, int limit) {
		playlists.failStale(Instant.now().minus(properties.staleAfter()), Instant.now());
		int bounded = Math.max(1, Math.min(limit, MAX_PLAYLISTS));
		return playlists.findSummaries(userId, bookId, Limit.of(bounded)).stream().map(LibraryService::toSummary).toList();
	}

	/**
	 * Estante pessoal: todo livro escolhido aparece (mesmo sem trilha ainda), ordenado pela atividade
	 * mais recente — escolha do livro ou criação de uma trilha.
	 */
	@Transactional
	public LibraryResponse library(UUID userId) {
		List<PlaylistSummary> all = playlists(userId, null, MAX_PLAYLISTS);
		Map<UUID, List<PlaylistSummary>> byBook = new LinkedHashMap<>();
		for (PlaylistSummary summary : all) {
			byBook.computeIfAbsent(summary.book().id(), id -> new ArrayList<>()).add(summary);
		}

		List<LibraryBook> shelf = new ArrayList<>();
		for (BookResponse book : books.recent(userId, MAX_BOOKS)) {
			shelf.add(new LibraryBook(
					new BookSummary(book.id(), book.title(), book.authors(), book.coverUrl()),
					book.publishedDate(),
					book.lastSelectedAt(),
					byBook.getOrDefault(book.id(), List.of())));
		}
		shelf.sort(Comparator.comparing(LibraryService::lastActivity).reversed());
		return new LibraryResponse(shelf.size(), all.size(), shelf);
	}

	private static Instant lastActivity(LibraryBook entry) {
		Instant latestPlaylist = entry.playlists().isEmpty() ? Instant.EPOCH : entry.playlists().getFirst().createdAt();
		Instant selected = entry.lastSelectedAt() == null ? Instant.EPOCH : entry.lastSelectedAt();
		return latestPlaylist.isAfter(selected) ? latestPlaylist : selected;
	}

	private static PlaylistSummary toSummary(PlaylistRepository.SummaryRow row) {
		Playlist playlist = row.getPlaylist();
		Book book = playlist.getBook();
		return new PlaylistSummary(
				playlist.getId(),
				playlist.getName(),
				playlist.getMode(),
				playlist.getStatus().name(),
				playlist.getVerificationStatus() == null ? null : playlist.getVerificationStatus().name(),
				row.getTrackCount(),
				new BookSummary(book.getId(), book.getTitle(), book.getAuthors(), book.getCoverUrl()),
				playlist.getSpotifyPlaylistUrl(),
				playlist.getCreatedAt());
	}
}
