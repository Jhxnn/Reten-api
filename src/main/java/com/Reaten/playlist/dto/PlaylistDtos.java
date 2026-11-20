package com.Reaten.playlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.Reaten.ai.ListeningMode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PlaylistDtos {

	private PlaylistDtos() {
	}

	public record GenerateRequest(
			@NotNull UUID bookId,
			@NotNull ListeningMode mode,
			@Size(max = 500, message = "Use no máximo 500 caracteres.") String notes) {
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

	/** {@code verified} é sempre false nesta etapa: a confirmação no Spotify vem depois. */
	public record Suggestion(int position, String title, String artist, String reason, boolean verified) {
	}

	public record Failure(String code, String message) {
	}

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
			List<Suggestion> suggestions,
			Failure failure,
			Instant createdAt) {
	}

	/** @param resetsAt quando a próxima geração volta a ficar disponível (nulo se ainda há gerações livres) */
	public record UsageResponse(int limit, int used, int remaining, Instant resetsAt, boolean aiConfigured) {
	}
}
