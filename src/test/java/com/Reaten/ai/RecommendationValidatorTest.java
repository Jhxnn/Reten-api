package com.Reaten.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.Reaten.ai.RecommendationResult.Atmosphere;
import com.Reaten.ai.RecommendationResult.Recommendation;

import jakarta.validation.Validation;

class RecommendationValidatorTest {

	private final RecommendationValidator validator =
			new RecommendationValidator(Validation.buildDefaultValidatorFactory().getValidator());

	private static final Atmosphere ATMOSPHERE = new Atmosphere("Fantasia", List.of("melancolia"), "Pousada",
			"Medieval", List.of("memória"), "MEDIA", "Mundo mágico");

	private static Recommendation track(String title, String artist) {
		return new Recommendation(title, artist, "Combina com o livro.", title + " " + artist);
	}

	private static List<Recommendation> distinctTracks(int count) {
		return new ArrayList<>(IntStream.rangeClosed(1, count).mapToObj(i -> track("Faixa " + i, "Artista " + i)).toList());
	}

	private static RecommendationResult result(List<Recommendation> tracks) {
		return new RecommendationResult("Nome", "Descrição", "Estilo", ATMOSPHERE, tracks);
	}

	@Test
	void removesDuplicatesIgnoringAccentsCaseAndPunctuation() {
		List<Recommendation> tracks = distinctTracks(12);
		tracks.add(track("Gymnopédie No. 1", "Erik Satie"));
		tracks.add(track("gymnopedie no 1", "ERIK SATIE"));

		RecommendationResult valid = validator.validate(result(tracks), 30);

		assertThat(valid.recommendations()).hasSize(13);
	}

	@Test
	void keepsAtMostTwoTracksPerArtist() {
		List<Recommendation> tracks = distinctTracks(12);
		tracks.add(track("A", "Max Richter"));
		tracks.add(track("B", "Max Richter"));
		tracks.add(track("C", "max richter"));

		assertThat(validator.validate(result(tracks), 30).recommendations())
				.filteredOn(r -> r.artist().equalsIgnoreCase("Max Richter")).hasSize(2);
	}

	@Test
	void dropsInvalidItemsAndTrimsToTheRequestedSize() {
		List<Recommendation> tracks = distinctTracks(30);
		tracks.add(0, new Recommendation("", "Sem título", "x", "x"));

		RecommendationResult valid = validator.validate(result(tracks), 24);

		assertThat(valid.recommendations()).hasSize(24).noneMatch(r -> r.title().isBlank());
	}

	@Test
	void failsWhenTooFewUsableSuggestionsRemain() {
		assertThatThrownBy(() -> validator.validate(result(distinctTracks(5)), 24))
				.isInstanceOf(RecommenderException.class)
				.extracting("reason").isEqualTo(RecommenderException.Reason.INVALID_RESPONSE);
	}

	@Test
	void failsWhenTheAtmosphereIsInvalid() {
		Atmosphere bad = new Atmosphere("Fantasia", List.of(), "Pousada", "Medieval", List.of(), "EXTREMA", "Mundo");
		RecommendationResult result = new RecommendationResult("Nome", "Descrição", "Estilo", bad, distinctTracks(12));

		assertThatThrownBy(() -> validator.validate(result, 24))
				.isInstanceOf(RecommenderException.class)
				.hasMessageContaining("emotionalIntensity");
	}
}
