package com.Reaten.playlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.Reaten.ai.ListeningMode;
import com.Reaten.music.CatalogTrack;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PlaylistDtos {

	private PlaylistDtos() {
	}

	public record GenerateRequest(
			@NotNull UUID bookId,
			@NotNull ListeningMode mode,
			@Size(max = 500, message = "Use no máximo 500 caracteres.") String notes) {
	}

	/** Limites da Spotify: nome até 100 caracteres; a descrição enviada é encurtada para 300. */
	public record UpdateRequest(
			@NotBlank(message = "Dê um nome à playlist.") @Size(max = 100, message = "Use no máximo 100 caracteres.") String name,
			@Size(max = 1000, message = "Use no máximo 1000 caracteres.") String description) {
	}

	public record AddTrackRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9]{10,40}") String spotifyTrackId) {
	}

	public record BookSummary(UUID id, String title, List<String> authors, String coverUrl) {
	}

	public record Atmosphere(
			String literaryGenre,
			List<String> moods,
			String setting,
			String era,
			List<String> themes,
			String emotionalIntensity,
			String universe) {
	}

	/**
	 * Sugestão da IA e o resultado da busca dela no catálogo.
	 *
	 * @param matchStatus PENDING (não verificada), MATCHED, NOT_FOUND ou DUPLICATE
	 */
	public record Suggestion(int position, String title, String artist, String reason, String matchStatus) {
	}

	/** Faixa verificada: existe no Spotify. */
	public record Track(
			UUID id,
			int position,
			String spotifyTrackId,
			String name,
			List<String> artists,
			String album,
			String imageUrl,
			Integer durationMs,
			String spotifyUrl,
			String reason,
			String source) {
	}

	/** Faixa do catálogo para escolher como substituição. */
	public record CatalogTrackResponse(String spotifyTrackId, String name, List<String> artists, String album,
			String imageUrl, Integer durationMs, String spotifyUrl) {

		public static CatalogTrackResponse of(CatalogTrack track) {
			return new CatalogTrackResponse(track.id(), track.name(), track.artists(), track.album(), track.imageUrl(),
					track.durationMs(), track.url());
		}
	}

	/**
	 * @param playlistUrl         link da playlist na conta do usuário (nulo se nunca foi salva)
	 * @param hasUnsavedChanges   houve edição depois do último envio
	 */
	public record SpotifyState(boolean publishingAvailable, String playlistUrl, Instant savedAt, boolean hasUnsavedChanges) {
	}

	public record Failure(String code, String message) {
	}

	/**
	 * @param verificationStatus VERIFIED, NOT_CONFIGURED ou FAILED (nulo enquanto gera)
	 * @param tracks             só faixas que existem no catálogo
	 */
	public record PlaylistResponse(
			UUID id,
			String status,
			ListeningMode mode,
			BookSummary book,
			String userNotes,
			String name,
			String description,
			String musicalStyle,
			Atmosphere atmosphere,
			String verificationStatus,
			List<Track> tracks,
			List<Suggestion> suggestions,
			SpotifyState spotify,
			Failure failure,
			Instant createdAt) {
	}

	/** Playlist em listas (início, biblioteca, página do livro): sem faixas nem sugestões. */
	public record PlaylistSummary(
			UUID id,
			String name,
			ListeningMode mode,
			String status,
			String verificationStatus,
			long trackCount,
			BookSummary book,
			String spotifyUrl,
			Instant createdAt) {
	}

	/** Um livro na estante do usuário, com as trilhas criadas a partir dele (mais recentes primeiro). */
	public record LibraryBook(
			BookSummary book,
			String publishedDate,
			Instant lastSelectedAt,
			List<PlaylistSummary> playlists) {
	}

	public record LibraryResponse(int bookCount, int playlistCount, List<LibraryBook> books) {
	}

	/** @param resetsAt quando a próxima geração volta a ficar disponível (nulo se ainda há gerações livres) */
	public record UsageResponse(int limit, int used, int remaining, Instant resetsAt, boolean aiConfigured) {
	}
}
