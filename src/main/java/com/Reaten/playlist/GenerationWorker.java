package com.Reaten.playlist;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.Reaten.ai.MusicRecommender;
import com.Reaten.ai.RecommendationRequest;
import com.Reaten.ai.RecommendationResult;
import com.Reaten.ai.RecommendationValidator;
import com.Reaten.ai.RecommenderException;
import com.Reaten.book.Book;

/**
 * Executa a geração fora da requisição HTTP (thread virtual via @Async): IA, validação e verificação no catálogo.
 * A chamada à IA acontece sem transação aberta; só leitura e gravação usam o banco.
 */
@Component
class GenerationWorker {

	private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);

	private final PlaylistRepository playlists;
	private final MusicRecommender recommender;
	private final RecommendationValidator validator;
	private final GenerationProperties properties;
	private final TransactionTemplate transactions;
	private final PlaylistVerification verification;

	GenerationWorker(PlaylistRepository playlists, MusicRecommender recommender, RecommendationValidator validator,
			GenerationProperties properties, TransactionTemplate transactions, PlaylistVerification verification) {
		this.playlists = playlists;
		this.recommender = recommender;
		this.validator = validator;
		this.properties = properties;
		this.transactions = transactions;
		this.verification = verification;
	}

	@Async
	public void generate(UUID playlistId) {
		long started = System.currentTimeMillis();
		try {
			RecommendationRequest request = transactions.execute(status -> buildRequest(playlistId));
			RecommendationResult raw = recommender.recommend(request);
			RecommendationResult valid = validator.validate(raw, properties.trackCount());
			transactions.executeWithoutResult(status -> playlists.findById(playlistId)
					.ifPresent(playlist -> playlist.applyRecommendations(valid, recommender.modelName())));
			log.info("Playlist {}: IA respondeu em {} ms com {} sugestões", playlistId,
					System.currentTimeMillis() - started, valid.recommendations().size());
			verification.run(playlistId);
		} catch (RecommenderException ex) {
			log.warn("Geração da playlist {} falhou: {} ({})", playlistId, ex.getMessage(), ex.getReason());
			markFailed(playlistId, ex.getReason().code);
		} catch (RuntimeException ex) {
			log.error("Erro inesperado gerando a playlist {}", playlistId, ex);
			markFailed(playlistId, "generation_error");
		}
	}

	private RecommendationRequest buildRequest(UUID playlistId) {
		Playlist playlist = playlists.findWithBookById(playlistId)
				.orElseThrow(() -> new IllegalStateException("Playlist sumiu antes da geração: " + playlistId));
		Book book = playlist.getBook();
		return new RecommendationRequest(book.getTitle(), book.getSubtitle(), book.getAuthors(), book.getPublishedDate(),
				book.getCategories(), book.getDescription(), playlist.getUserNotes(), playlist.getMode(),
				properties.trackCount());
	}

	private void markFailed(UUID playlistId, String code) {
		transactions.executeWithoutResult(status -> playlists.findById(playlistId).ifPresent(p -> p.fail(code)));
	}
}
