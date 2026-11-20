package com.Reaten.music;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TrackMatcherTest {

	private final TrackMatcher matcher = new TrackMatcher();

	private static CatalogTrack track(String id, String name, String... artists) {
		return new CatalogTrack(id, name, List.of(artists), "Álbum", null, 200_000, "https://open.spotify.com/track/" + id);
	}

	@Test
	void exactMatchIgnoringAccentsAndCase() {
		var match = matcher.bestMatch("Gymnopédie No. 1", "Erik Satie",
				List.of(track("1", "Gymnopedie No.1", "Erik Satie", "Pascal Rogé")));
		assertThat(match).get().extracting(TrackMatcher.Match::confidence).isEqualTo(TrackMatcher.Confidence.EXACT);
	}

	@Test
	void ignoresRemasterAndSoundtrackSuffixes() {
		assertThat(matcher.bestMatch("Time", "Hans Zimmer",
				List.of(track("1", "Time - From Inception", "Hans Zimmer")))).isPresent();
		assertThat(matcher.bestMatch("Holocene", "Bon Iver",
				List.of(track("2", "Holocene (2021 Remaster)", "Bon Iver")))).isPresent();
	}

	@Test
	void requiresTheSuggestedArtist() {
		assertThat(matcher.bestMatch("Holocene", "Bon Iver",
				List.of(track("1", "Holocene", "Some Cover Band")))).isEmpty();
	}

	@Test
	void acceptsCollaborationsContainingTheArtist() {
		assertThat(matcher.bestMatch("Saman", "Ólafur Arnalds",
				List.of(track("1", "Saman", "Olafur Arnalds", "Alice Sara Ott")))).isPresent();
	}

	@Test
	void rejectsLiveOrRemixVersionsUnlessAsked() {
		List<CatalogTrack> candidates = List.of(track("1", "Riverside - Live", "Agnes Obel"),
				track("2", "Riverside (Remix)", "Agnes Obel"));
		assertThat(matcher.bestMatch("Riverside", "Agnes Obel", candidates)).isEmpty();
		assertThat(matcher.bestMatch("Riverside (Live)", "Agnes Obel", candidates)).isPresent();
	}

	@Test
	void prefersExactOverEquivalentEvenIfLaterInResults() {
		var match = matcher.bestMatch("On the Nature of Daylight", "Max Richter",
				List.of(track("1", "On the Nature of Daylight Entropy", "Max Richter"),
						track("2", "On The Nature Of Daylight", "Max Richter")));
		assertThat(match).get().satisfies(m -> {
			assertThat(m.track().id()).isEqualTo("2");
			assertThat(m.confidence()).isEqualTo(TrackMatcher.Confidence.EXACT);
		});
		// Sem a versão exata, a equivalente é aceita com confiança menor.
		assertThat(matcher.bestMatch("On the Nature of Daylight", "Max Richter",
				List.of(track("1", "On the Nature of Daylight Entropy", "Max Richter"))))
				.get().extracting(TrackMatcher.Match::confidence).isEqualTo(TrackMatcher.Confidence.HIGH);
	}

	private static CatalogTrack onAlbum(String id, String name, String album, String... artists) {
		return new CatalogTrack(id, name, List.of(artists), album, null, 200_000, "https://open.spotify.com/track/" + id);
	}

	@Test
	void prefersTheOriginalOverARerecordingAlbumEvenWhenItComesFirst() {
		// Caso real: "The Gravel Road" veio de "Imitation Games Vol.4" (regravação da London Music Works).
		var match = matcher.bestMatch("The Gravel Road", "James Newton Howard", List.of(
				onAlbum("regravacao", "The Gravel Road (from \"The Village\")", "Imitation Games Vol.4",
						"James Newton Howard", "London Music Works", "Nick Squires", "Will Harvey"),
				onAlbum("original", "The Gravel Road", "The Village (Original Motion Picture Soundtrack)", "James Newton Howard")));
		assertThat(match).get().extracting(m -> m.track().id()).isEqualTo("original");
	}

	@Test
	void keepsEnsembleOriginalsAndRejectsUnaskedArrangements() {
		// Casos reais: a original credita o conjunto; as alternativas são arranjos que a IA não pediu.
		var daylight = matcher.bestMatch("On the Nature of Daylight", "Max Richter", List.of(
				onAlbum("original", "On the Nature of Daylight", "The Blue Notebooks (15 Years)",
						"Max Richter", "Louisa Fuller", "Natalia Bonner", "John Metcalfe", "Philip Sheppard", "Chris Worsey"),
				onAlbum("orquestral", "On the Nature of Daylight - Orchestral Version", "The Blue Notebooks (15 Years)",
						"Max Richter", "Lorenz Dangel")));
		assertThat(daylight).get().extracting(m -> m.track().id()).isEqualTo("original");

		var flight = matcher.bestMatch("Flight from the City", "Jóhann Jóhannsson", List.of(
				onAlbum("piano", "Flight from the City (Performed on Piano)", "Flight from the City (Performed on Piano)",
						"Jóhann Jóhannsson", "Alice Sara Ott"),
				onAlbum("original", "Flight from the City", "Orphée", "Jóhann Jóhannsson", "Yuki Numata Resnick",
						"Tarn Travers", "Ben Russell", "Clarice Jensen")));
		assertThat(flight).get().extracting(m -> m.track().id()).isEqualTo("original");
	}

	@Test
	void prefersTheOriginalSoundtrackOverACompilationWithTheLiteralTitle() {
		// Caso real (resultados da Spotify, na ordem): o original se chama "New Moon (The Meadow)";
		// a coletânea com intérprete extra (Stanislav Gallin) tem o título literal "The Meadow".
		var match = matcher.bestMatch("The Meadow", "Alexandre Desplat", List.of(
				onAlbum("original", "New Moon (The Meadow)", "The Twilight Saga: New Moon (Original Motion Picture Soundtrack)",
						"Alexandre Desplat"),
				onAlbum("coletanea", "The Meadow - From \"The Twilight Saga: New Moon\"", "Music from the Twilight Saga",
						"Alexandre Desplat", "Stanislav Gallin"),
				onAlbum("piano", "New Moon [The Meadow]", "New Moon [The Meadow]", "Alexandre Desplat", "Martin Jacoby")));
		assertThat(match).get().extracting(m -> m.track().id()).isEqualTo("original");
	}

	@Test
	void recognisesTitlesInsideParenthesesAndBeforeADash() {
		assertThat(TrackMatcher.titleVariants("New Moon (The Meadow)")).contains("meadow");
		assertThat(TrackMatcher.titleVariants("The Gravel Road - Score")).contains("gravel road");
		// Só a primeira parte do hífen: "... - Main Title" não vira o título "Main Title".
		assertThat(TrackMatcher.titleVariants("Battlestar Galactica - Main Title")).doesNotContain("main title");
	}

	@Test
	void acceptsARerecordingOnlyWhenItIsTheOnlyOption() {
		var match = matcher.bestMatch("The Gravel Road", "James Newton Howard", List.of(
				onAlbum("regravacao", "The Gravel Road", "Imitation Games Vol.4", "James Newton Howard", "London Music Works")));
		assertThat(match).get().extracting(m -> m.track().id()).isEqualTo("regravacao");
	}

	@Test
	void soundtrackAlbumsAreNotTreatedAsRerecordings() {
		assertThat(TrackMatcher.isRerecordingAlbum(TrackMatcher.normalize("Inception (Music from the Motion Picture)"))).isFalse();
		assertThat(TrackMatcher.isRerecordingAlbum(TrackMatcher.normalize("A Tribute to Hans Zimmer"))).isTrue();
		assertThat(TrackMatcher.isRerecordingAlbum(TrackMatcher.normalize("Piano Versions of Movie Themes"))).isTrue();
	}

	@Test
	void rejectsDifferentSongsByTheSameArtist() {
		assertThat(matcher.bestMatch("On the Nature of Daylight", "Max Richter",
				List.of(track("1", "Vladimir's Blues", "Max Richter")))).isEmpty();
	}
}
