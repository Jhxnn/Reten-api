package com.Reaten.playlist;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.Reaten.ai.MusicRecommender;
import com.Reaten.book.Book;
import com.Reaten.book.BookRepository;
import com.Reaten.playlist.dto.PlaylistDtos.GenerateRequest;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.Reaten.playlist.dto.PlaylistDtos.UsageResponse;
import com.Reaten.shared.exception.ApiException;

/** Geração (com limite diário), consulta e uso. Edição e publicação ficam em {@link PlaylistEditingService}. */
@Service
public class PlaylistService {

	private static final Duration WINDOW = Duration.ofHours(24);
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

	private final PlaylistRepository playlists;
	private final BookRepository books;
	private final MusicRecommender recommender;
	private final GenerationWorker worker;
	private final GenerationProperties properties;
	private final TransactionTemplate transactions;
	private final PlaylistMapper mapper;

	PlaylistService(PlaylistRepository playlists, BookRepository books, MusicRecommender recommender,
			GenerationWorker worker, GenerationProperties properties, TransactionTemplate transactions,
			PlaylistMapper mapper) {
		this.playlists = playlists;
		this.books = books;
		this.recommender = recommender;
		this.worker = worker;
		this.properties = properties;
		this.transactions = transactions;
		this.mapper = mapper;
	}

	/**
	 * Registra a geração e dispara o trabalho em segundo plano. Retorna logo, com status GENERATING.
	 * O registro é confirmado no banco antes de o worker começar, para ele sempre encontrar a playlist.
	 */
	public PlaylistResponse requestGeneration(UUID userId, GenerateRequest request) {
		if (!recommender.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_not_configured",
					PlaylistMapper.FAILURE_MESSAGES.get("ai_not_configured"));
		}
		UUID playlistId = transactions.execute(status -> {
			playlists.lockUserForGeneration(userId.toString());
			UsageResponse usage = usage(userId);
			if (usage.playlistCount() >= usage.maxPlaylists()) {
				throw ApiException.conflict("playlist_limit_reached",
						"Você já tem " + usage.maxPlaylists() + " playlists. Apague uma na sua biblioteca para criar outra.");
			}
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
		return mapper.toResponse(owned(userId, playlistId));
	}

	@Transactional(readOnly = true)
	public UsageResponse usage(UUID userId) {
		Instant since = Instant.now().minus(WINDOW);
		int used = (int) playlists.countAttemptsSince(userId, since);
		int remaining = Math.max(0, properties.dailyLimit() - used);
		Instant resetsAt = remaining > 0 ? null
				: playlists.oldestAttemptSince(userId, since).map(oldest -> oldest.plus(WINDOW)).orElse(null);
		int playlistCount = (int) playlists.countOccupyingSlots(userId);
		return new UsageResponse(properties.dailyLimit(), used, remaining, resetsAt, recommender.isConfigured(),
				properties.maxPlaylists(), playlistCount);
	}

	/** Apaga a playlist do Reten (libera vaga). A cópia salva no Spotify continua na conta do usuário. */
	@Transactional
	public void delete(UUID userId, UUID playlistId) {
		owned(userId, playlistId).markDeleted();
	}

	private Playlist owned(UUID userId, UUID playlistId) {
		return playlists.findWithDetailsByIdAndUserId(playlistId, userId)
				.orElseThrow(() -> ApiException.notFound("playlist_not_found", "Playlist não encontrada."));
	}
}
