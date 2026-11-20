package com.Reaten.music;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Decide se uma faixa do catálogo é a música que a IA sugeriu.
 * <p>
 * Regra: o artista sugerido precisa estar entre os artistas da faixa E o título precisa bater
 * depois de normalizado (acentos, maiúsculas, pontuação e sufixos como "Remastered 2011" ou "Live").
 * Na dúvida, não há correspondência — uma sugestão sem faixa é melhor que uma faixa errada.
 */
@Component
public class TrackMatcher {

	public enum Confidence {
		/** Título e artista idênticos após normalização. */
		EXACT,
		/** Mesmo artista e título equivalente (sufixos, subtítulos, pequenas variações). */
		HIGH
	}

	public record Match(CatalogTrack track, Confidence confidence) {
	}

	/** Trechos de versão que não mudam a identidade da música. */
	private static final Pattern VERSION_NOISE = Pattern.compile(
			"\\s*[\\(\\[][^\\)\\]]*(remaster|version|versao|edit|mono|stereo|feat|ft\\.|from|bonus|deluxe|original)[^\\)\\]]*[\\)\\]]"
					+ "|\\s+-\\s+.*(remaster|version|versao|edit|mono|stereo|from|live|ao vivo|bonus|original).*$");

	/** Versões que mudam a experiência e só valem se a própria sugestão pedir por elas. */
	private static final List<String> ALTERED_VERSIONS = List.of("live", "ao vivo", "karaoke", "instrumental", "cover", "remix",
			"acoustic", "acustico", "sped up", "slowed", "8 bit", "lullaby");

	public Optional<Match> bestMatch(String title, String artist, List<CatalogTrack> candidates) {
		String wantedTitle = normalizeTitle(title);
		String wantedArtist = normalize(artist);
		String rawWanted = normalize(title);
		Match best = null;
		for (CatalogTrack candidate : candidates) {
			if (!artistMatches(wantedArtist, candidate.artists()) || introducesAlteredVersion(rawWanted, normalize(candidate.name()))) {
				continue;
			}
			String candidateTitle = normalizeTitle(candidate.name());
			if (candidateTitle.equals(wantedTitle)) {
				return Optional.of(new Match(candidate, Confidence.EXACT));
			}
			if (best == null && titlesEquivalent(wantedTitle, candidateTitle)) {
				best = new Match(candidate, Confidence.HIGH);
			}
		}
		return Optional.ofNullable(best);
	}

	static boolean artistMatches(String wanted, List<String> artists) {
		if (wanted.isEmpty()) {
			return false;
		}
		for (String artist : artists) {
			String candidate = normalize(artist);
			if (candidate.isEmpty()) {
				continue;
			}
			// "Max Richter" x "Max Richter"; "Simon & Garfunkel" x "Simon Garfunkel"; colaborações "A & B" contendo "A".
			if (candidate.equals(wanted) || containsWords(wanted, candidate) || containsWords(candidate, wanted)) {
				return true;
			}
		}
		return false;
	}

	static boolean titlesEquivalent(String a, String b) {
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		String shorter = a.length() <= b.length() ? a : b;
		String longer = shorter == a ? b : a;
		// Subtítulos: "Time" x "Time (Inception)" já some na normalização; aqui "Holocene" x "Holocene - single".
		if (longer.startsWith(shorter + " ") && shorter.length() >= longer.length() * 0.6) {
			return true;
		}
		Set<String> tokensA = new HashSet<>(Arrays.asList(a.split(" ")));
		Set<String> tokensB = new HashSet<>(Arrays.asList(b.split(" ")));
		Set<String> union = new HashSet<>(tokensA);
		union.addAll(tokensB);
		tokensA.retainAll(tokensB);
		return union.size() >= 2 && (double) tokensA.size() / union.size() >= 0.8;
	}

	private static boolean introducesAlteredVersion(String wanted, String candidate) {
		for (String marker : ALTERED_VERSIONS) {
			if (containsWords(candidate, marker) && !containsWords(wanted, marker)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsWords(String haystack, String needle) {
		return (" " + haystack + " ").contains(" " + needle + " ");
	}

	static String normalizeTitle(String value) {
		String lower = stripAccents(value.toLowerCase(Locale.ROOT));
		String withoutVersion = VERSION_NOISE.matcher(lower).replaceAll("");
		return clean(withoutVersion);
	}

	static String normalize(String value) {
		return value == null ? "" : clean(stripAccents(value.toLowerCase(Locale.ROOT)));
	}

	private static String stripAccents(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private static String clean(String value) {
		return value.replace("&", " ").replaceAll("[^a-z0-9]+", " ").replaceAll("\\b(the|a|o)\\b", " ")
				.replaceAll("\\s+", " ").strip();
	}
}
