package com.Reaten.playlist;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.Reaten.ai.MusicRecommender;
import com.Reaten.book.Book;
import com.Reaten.book.BookRepository;
import com.Reaten.playlist.dto.PlaylistDtos.Atmosphere;
import com.Reaten.playlist.dto.PlaylistDtos.BookSummary;
import com.Reaten.playlist.dto.PlaylistDtos.Failure;
import com.Reaten.playlist.dto.PlaylistDtos.GenerateRequest;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.Reaten.playlist.dto.PlaylistDtos.Suggestion;
import com.Reaten.playlist.dto.PlaylistDtos.UsageResponse;
import com.Reaten.shared.exception.ApiException;

@Service
public class PlaylistService {

	private static final Duration WINDOW = Duration.ofHours(24);
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

	private static final Map<String, String> FAILURE_MESSAGES = Map.of(
			"ai_not_configured", "A inteligência artificial ainda não foi configurada neste ambiente.",
			"ai_rate_limited", "O serviço de IA está sobrecarregado ou sem crédito agora. Tente novamente mais tarde.",
			"ai_refused", "A IA não aceitou gerar uma trilha para este pedido. Tente outro modo ou reescreva suas impressões.",
			"ai_invalid_response", "A IA devolveu uma resposta incompleta. Tente gerar de novo.",
			"ai_unavailable", "O serviço de IA não respondeu a tempo. Tente gerar de novo em instantes.",
			"generation_timeout", "A geração foi interrompida antes de terminar. Tente gerar de novo.",
			"generation_error", "Algo deu errado durante a geração. Tente de novo.");

	private final PlaylistRepository playlists;
	private final BookRepository books;
	private final MusicRecommender recommender;
	private final GenerationWorker worker;
	private final GenerationProperties properties;
	private final TransactionTemplate transactions;

	PlaylistService(PlaylistRepository playlists, BookRepository books, MusicRecommender recommender,
			GenerationWorker worker, GenerationProperties properties, TransactionTemplate transactions) {
		this.playlists = playlists;
		this.books = books;
		this.recommender = recommender;
		this.worker = worker;
		this.properties = properties;
		this.transactions = transactions;
	}

	/**
	 * Registra a geração e dispara o trabalho em segundo plano. Retorna logo, com status GENERATING.
	 * O registro é confirmado no banco antes de o worker começar, para ele sempre encontrar a playlist.
	 */
	public PlaylistResponse requestGeneration(UUID userId, GenerateRequest request) {
		if (!recommender.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_not_configured", FAILURE_MESSAGES.get("ai_not_configured"));
		}
		UUID playlistId = transactions.execute(status -> {
			playlists.lockUserForGeneration(userId.toString());
			UsageResponse usage = usage(userId);
			if (usage.remaining() <= 0) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "generation_limit_reached",
						"Você usou as " + usage.limit() + " gerações das últimas 24 horas. A próxima libera às "
								+ HOUR.format(usage.resetsAt()) + ".");
			}
			Book book = books.findById(request.bookId())
					.orElseThrow(() -> ApiException.notFound("book_not_found", "Livro não encontrado. Escolha o livro de novo."));
			String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().strip();
			return playlists.save(Playlist.pending(userId, book, request.mode(), notes)).getId();
		});
		worker.generate(playlistId);
		// Chamada interna não passa pelo proxy do @Transactional: a transação vem do template.
		return transactions.execute(status -> get(userId, playlistId));
	}

	@Transactional
	public PlaylistResponse get(UUID userId, UUID playlistId) {
		playlists.failStale(Instant.now().minus(properties.staleAfter()), Instant.now());
		Playlist playlist = playlists.findWithDetailsByIdAndUserId(playlistId, userId)
				.orElseThrow(() -> ApiException.notFound("playlist_not_found", "Playlist não encontrada."));
		return toResponse(playlist);
	}

	@Transactional(readOnly = true)
	public UsageResponse usage(UUID userId) {
		Instant since = Instant.now().minus(WINDOW);
		int used = (int) playlists.countByUserIdAndCreatedAtAfter(userId, since);
		int remaining = Math.max(0, properties.dailyLimit() - used);
		Instant resetsAt = remaining > 0 ? null
				: playlists.oldestCreatedAfter(userId, since).map(oldest -> oldest.plus(WINDOW)).orElse(null);
		return new UsageResponse(properties.dailyLimit(), used, remaining, resetsAt, recommender.isConfigured());
	}

	private PlaylistResponse toResponse(Playlist playlist) {
		Book book = playlist.getBook();
		boolean ready = playlist.getStatus() == Playlist.Status.READY;
		return new PlaylistResponse(
				playlist.getId(),
				playlist.getStatus().name(),
				playlist.getMode(),
				new BookSummary(book.getId(), book.getTitle(), book.getAuthors(), book.getCoverUrl()),
				playlist.getUserNotes(),
				playlist.getName(),
				playlist.getDescription(),
				playlist.getMusicalStyle(),
				ready ? new Atmosphere(playlist.getLiteraryGenre(), playlist.getMoods(), playlist.getSetting(),
						playlist.getEra(), playlist.getThemes(), playlist.getEmotionalIntensity(), playlist.getUniverse()) : null,
				playlist.getRecommendations().stream()
						.map(r -> new Suggestion(r.getPosition(), r.getTitle(), r.getArtist(), r.getReason(), false))
						.toList(),
				playlist.getStatus() == Playlist.Status.FAILED
						? new Failure(playlist.getFailureCode(),
								FAILURE_MESSAGES.getOrDefault(playlist.getFailureCode(), FAILURE_MESSAGES.get("generation_error")))
						: null,
				playlist.getCreatedAt());
	}
}
