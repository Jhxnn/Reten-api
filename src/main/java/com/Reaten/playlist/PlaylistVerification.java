package com.Reaten.playlist;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.Reaten.playlist.Playlist.VerificationStatus;

/**
 * Confere as sugestões da playlist no catálogo e conclui a geração.
 * As buscas acontecem fora de transação; só a leitura das sugestões e a gravação do resultado usam o banco.
 * Falha do catálogo não perde a playlist: ela fica pronta com as sugestões não verificadas e pode ser reverificada.
 */
@Component
class PlaylistVerification {

	private static final Logger log = LoggerFactory.getLogger(PlaylistVerification.class);

	private final PlaylistRepository playlists;
	private final TrackVerifier verifier;
	private final TransactionTemplate transactions;

	PlaylistVerification(PlaylistRepository playlists, TrackVerifier verifier, TransactionTemplate transactions) {
		this.playlists = playlists;
		this.verifier = verifier;
		this.transactions = transactions;
	}

	void run(UUID playlistId) {
		if (!verifier.isAvailable()) {
			finish(playlistId, playlist -> playlist.finishWithoutVerification(VerificationStatus.NOT_CONFIGURED));
			return;
		}
		List<TrackVerifier.Suggestion> suggestions = transactions.execute(status -> playlists.findById(playlistId)
				.map(p -> p.getRecommendations().stream()
						.map(r -> new TrackVerifier.Suggestion(r.getId(), r.getTitle(), r.getArtist()))
						.toList())
				.orElse(List.of()));
		long started = System.currentTimeMillis();
		try {
			List<TrackVerifier.Outcome> outcomes = verifier.verify(suggestions);
			finish(playlistId, playlist -> playlist.finishVerification(VerificationStatus.VERIFIED, outcomes));
			long matched = outcomes.stream().filter(o -> o.status() == PlaylistRecommendation.MatchStatus.MATCHED).count();
			log.info("Playlist {} verificada em {} ms: {} de {} sugestões encontradas", playlistId,
					System.currentTimeMillis() - started, matched, suggestions.size());
		} catch (RuntimeException ex) {
			log.warn("Verificação da playlist {} falhou: {}", playlistId, ex.getMessage());
			finish(playlistId, playlist -> playlist.finishWithoutVerification(VerificationStatus.FAILED));
		}
	}

	private void finish(UUID playlistId, java.util.function.Consumer<Playlist> change) {
		transactions.executeWithoutResult(status -> playlists.findById(playlistId).ifPresent(change));
	}
}
