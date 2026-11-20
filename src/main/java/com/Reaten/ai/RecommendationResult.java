package com.Reaten.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Resposta estruturada da IA. As anotações de validação são conferidas antes de qualquer uso:
 * o schema da OpenAI garante o formato, não a qualidade do conteúdo.
 */
public record RecommendationResult(
		@NotBlank @Size(max = 120) String playlistName,
		@NotBlank @Size(max = 1000) String description,
		@NotBlank @Size(max = 200) String musicalStyle,
		@NotNull @Valid Atmosphere atmosphere,
		@NotNull @Size(min = 1, max = 60) List<@Valid Recommendation> recommendations) {

	public record Atmosphere(
			@NotBlank @Size(max = 200) String literaryGenre,
			@NotNull @Size(max = 8) List<@NotBlank @Size(max = 60) String> moods,
			@NotBlank @Size(max = 500) String setting,
			@NotBlank @Size(max = 200) String era,
			@NotNull @Size(max = 8) List<@NotBlank @Size(max = 80) String> themes,
			@NotNull @Pattern(regexp = "BAIXA|MEDIA|ALTA") String emotionalIntensity,
			@NotBlank @Size(max = 1000) String universe) {
	}

	/**
	 * @param searchQuery termos sugeridos pela IA para encontrar a faixa no Spotify
	 */
	public record Recommendation(
			@NotBlank @Size(max = 300) String title,
			@NotBlank @Size(max = 300) String artist,
			@NotBlank @Size(max = 500) String reason,
			@NotBlank @Size(max = 300) String searchQuery) {
	}
}
