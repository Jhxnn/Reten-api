package com.Reaten.playlist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;

import com.Reaten.music.CatalogTrack;
import com.Reaten.music.MusicCatalog;
import com.Reaten.music.TrackMatcher;
import com.Reaten.playlist.PlaylistRecommendation.MatchStatus;

/**
 * Procura cada sugestão da IA no catálogo real.
 * <ol>
 * <li>Busca de campo: {@code track:"título" artist:"artista"} (até 10 resultados, para a gravação original ter chance de aparecer).</li>
 * <li>Se não houver correspondência, busca livre {@code título artista} (até 10).</li>
 * </ol>
 * Poucas buscas em paralelo, porque a cota da Spotify em Development Mode é compartilhada pelo app inteiro.
 * A mesma faixa encontrada para duas sugestões entra uma vez só.
 */
@Component
class TrackVerifier {

	static final int PARALLEL_SEARCHES = 3;

	record Suggestion(UUID recommendationId, String title, String artist) {
	}

	record Outcome(UUID recommendationId, MatchStatus status, CatalogTrack track) {
	}

	private final MusicCatalog catalog;
	private final TrackMatcher matcher;

	TrackVerifier(MusicCatalog catalog, TrackMatcher matcher) {
		this.catalog = catalog;
		this.matcher = matcher;
	}

	boolean isAvailable() {
		return catalog.isConfigured();
	}

	/** @throws RuntimeException se o catálogo falhar — quem chama decide como registrar a falha */
	List<Outcome> verify(List<Suggestion> suggestions) {
		List<Optional<CatalogTrack>> found = new ArrayList<>();
		Semaphore permits = new Semaphore(PARALLEL_SEARCHES);
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Optional<CatalogTrack>>> futures = new ArrayList<>();
			for (Suggestion suggestion : suggestions) {
				futures.add(executor.submit(() -> {
					permits.acquire();
					try {
						return find(suggestion);
					} finally {
						permits.release();
					}
				}));
			}
			for (Future<Optional<CatalogTrack>> future : futures) {
				found.add(await(future));
			}
		}

		List<Outcome> outcomes = new ArrayList<>();
		Set<String> used = new HashSet<>();
		for (int i = 0; i < suggestions.size(); i++) {
			UUID id = suggestions.get(i).recommendationId();
			Optional<CatalogTrack> track = found.get(i);
			if (track.isEmpty()) {
				outcomes.add(new Outcome(id, MatchStatus.NOT_FOUND, null));
			} else if (!used.add(track.get().id())) {
				outcomes.add(new Outcome(id, MatchStatus.DUPLICATE, null));
			} else {
				outcomes.add(new Outcome(id, MatchStatus.MATCHED, track.get()));
			}
		}
		return outcomes;
	}

	private Optional<CatalogTrack> find(Suggestion suggestion) {
		String title = sanitize(suggestion.title());
		String artist = sanitize(suggestion.artist());
		Optional<TrackMatcher.Match> match = matcher.bestMatch(suggestion.title(), suggestion.artist(),
				catalog.searchTracks("track:\"" + title + "\" artist:\"" + artist + "\"", 10));
		if (match.isEmpty()) {
			match = matcher.bestMatch(suggestion.title(), suggestion.artist(), catalog.searchTracks(title + " " + artist, 10));
		}
		return match.map(TrackMatcher.Match::track);
	}

	/** Aspas e dois-pontos quebrariam a sintaxe de campos da busca. */
	private static String sanitize(String value) {
		return value.replace("\"", " ").replace(":", " ").replaceAll("\\s+", " ").strip();
	}

	private static <T> T await(Future<T> future) {
		try {
			return future.get();
		} catch (ExecutionException ex) {
			if (ex.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException(ex.getCause());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Verificação interrompida", ex);
		}
	}
}
