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

	@Test
	void rejectsDifferentSongsByTheSameArtist() {
		assertThat(matcher.bestMatch("On the Nature of Daylight", "Max Richter",
				List.of(track("1", "Vladimir's Blues", "Max Richter")))).isEmpty();
	}
}
