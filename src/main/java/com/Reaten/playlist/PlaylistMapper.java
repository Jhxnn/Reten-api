package com.Reaten.playlist;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.Reaten.book.Book;
import com.Reaten.music.PlaylistPublisher;
import com.Reaten.playlist.dto.PlaylistDtos.Atmosphere;
import com.Reaten.playlist.dto.PlaylistDtos.BookSummary;
import com.Reaten.playlist.dto.PlaylistDtos.Failure;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.Reaten.playlist.dto.PlaylistDtos.SpotifyState;
import com.Reaten.playlist.dto.PlaylistDtos.Suggestion;
import com.Reaten.playlist.dto.PlaylistDtos.Track;

/** Converte a playlist em resposta da API. Precisa de transação aberta (coleções carregadas sob demanda). */
@Component
class PlaylistMapper {

	static final Map<String, String> FAILURE_MESSAGES = Map.of(
			"ai_not_configured", "A inteligência artificial ainda não foi configurada neste ambiente.",
			"ai_rate_limited", "O serviço de IA está sobrecarregado ou sem crédito agora. Tente novamente mais tarde.",
			"ai_refused", "A IA não aceitou gerar uma trilha para este pedido. Tente outro modo ou reescreva suas impressões.",
			"ai_invalid_response", "A IA devolveu uma resposta incompleta. Tente gerar de novo.",
			"ai_unavailable", "O serviço de IA não respondeu a tempo. Tente gerar de novo em instantes.",
			"generation_timeout", "A geração foi interrompida antes de terminar. Tente gerar de novo.",
			"generation_error", "Algo deu errado durante a geração. Tente de novo.");

	private final PlaylistPublisher publisher;

	PlaylistMapper(PlaylistPublisher publisher) {
		this.publisher = publisher;
	}

	PlaylistResponse toResponse(Playlist playlist) {
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
				playlist.getVerificationStatus() == null ? null : playlist.getVerificationStatus().name(),
				playlist.getTracks().stream()
						.map(t -> new Track(t.getId(), t.getPosition(), t.getSpotifyTrackId(), t.getName(), t.getArtists(),
								t.getAlbum(), t.getAlbumImageUrl(), t.getDurationMs(), t.getSpotifyUrl(),
								t.getRecommendationReason(), t.getSource().name()))
						.toList(),
				playlist.getRecommendations().stream()
						.map(r -> new Suggestion(r.getPosition(), r.getTitle(), r.getArtist(), r.getReason(), r.getMatchStatus().name()))
						.toList(),
				new SpotifyState(publisher.isConfigured(), playlist.getSpotifyPlaylistUrl(), playlist.getSpotifySavedAt(),
						playlist.hasUnpublishedChanges()),
				playlist.getStatus() == Playlist.Status.FAILED
						? new Failure(playlist.getFailureCode(),
								FAILURE_MESSAGES.getOrDefault(playlist.getFailureCode(), FAILURE_MESSAGES.get("generation_error")))
						: null,
				playlist.getCreatedAt());
	}
}
