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
 * Entre vários candidatos válidos, prefere a gravação original (ver {@link #score}).
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
			"acoustic", "acustico", "sped up", "slowed", "8 bit", "lullaby", "orchestral", "orchestra version", "performed on",
			"piano version", "solo piano", "rework", "reworked", "reimagined", "arrangement", "arranged");

	/**
	 * Álbuns de regravação, tributo e "no estilo de": a faixa traz o compositor original nos créditos,
	 * mas não é a gravação que a IA sugeriu. Só são aceitos se não houver nenhuma outra opção.
	 */
	private static final List<String> RERECORDING_ALBUMS = List.of("tribute", "tributo", "homenagem", "cover", "covers",
			"karaoke", "made famous", "originally performed", "in the style of", "as performed by", "imitation", "renditions",
			"lullaby", "8 bit", "piano versions", "string quartet tribute", "plays the music of", "inspired by");

	/**
	 * Escolhe, entre os resultados do catálogo, a gravação que melhor corresponde à sugestão.
	 * Todos os candidatos válidos (artista presente, título equivalente) são pontuados:
	 * título exato, artista sugerido como primeiro crédito e poucos artistas extras sobem;
	 * álbuns de regravação/tributo descem. Em empate, vale a ordem de relevância do catálogo.
	 */
	public Optional<Match> bestMatch(String title, String artist, List<CatalogTrack> candidates) {
		String wantedTitle = normalizeTitle(title);
		String wantedArtist = normalize(artist);
		String rawWanted = normalize(title);
		Match best = null;
		int bestScore = Integer.MIN_VALUE;
		for (CatalogTrack candidate : candidates) {
			if (!artistMatches(wantedArtist, candidate.artists()) || introducesAlteredVersion(rawWanted, normalize(candidate.name()))) {
				continue;
			}
			Set<String> variants = titleVariants(candidate.name());
			boolean exact = variants.contains(wantedTitle);
			if (!exact && variants.stream().noneMatch(v -> titlesEquivalent(wantedTitle, v))) {
				continue;
			}
			int score = score(exact, wantedArtist, rawWanted, candidate);
			if (score > bestScore) {
				bestScore = score;
				best = new Match(candidate, exact ? Confidence.EXACT : Confidence.HIGH);
			}
		}
		return Optional.ofNullable(best);
	}

	private static final Pattern BRACKETS = Pattern.compile("[\\(\\[]([^\\)\\]]*)[\\)\\]]");

	/**
	 * Formas sob as quais o título de uma faixa do catálogo pode corresponder à sugestão:
	 * o título normalizado; sem os trechos entre parênteses/colchetes; o conteúdo desses trechos
	 * ("New Moon (The Meadow)" → "meadow"); e a primeira parte antes de " - "
	 * ("The Gravel Road - Score" → "gravel road"). Só a primeira parte do hífen conta, porque partes
	 * seguintes costumam ser descrições genéricas ("... - Main Title").
	 */
	static Set<String> titleVariants(String name) {
		Set<String> variants = new java.util.LinkedHashSet<>();
		String lower = stripAccents(name.toLowerCase(Locale.ROOT));
		variants.add(normalizeTitle(name));
		variants.add(clean(BRACKETS.matcher(lower).replaceAll(" ")));
		java.util.regex.Matcher inner = BRACKETS.matcher(lower);
		while (inner.find()) {
			variants.add(clean(inner.group(1)));
		}
		int dash = lower.indexOf(" - ");
		if (dash > 0) {
			variants.add(clean(BRACKETS.matcher(lower.substring(0, dash)).replaceAll(" ")));
		}
		variants.removeIf(String::isBlank);
		return variants;
	}

	static int score(boolean exactTitle, String wantedArtist, String rawWantedTitle, CatalogTrack candidate) {
		int score = exactTitle ? 10 : 0;
		List<String> artists = candidate.artists();
		if (!artists.isEmpty() && artistMatches(wantedArtist, List.of(artists.getFirst()))) {
			score += 5;
		}
		// Não há penalidade por artistas extras: gravações originais de música instrumental creditam
		// os músicos do conjunto (ex.: "On the Nature of Daylight" em The Blue Notebooks).
		String album = normalize(candidate.album());
		if (!album.isEmpty() && isRerecordingAlbum(album) && !isRerecordingAlbum(rawWantedTitle)) {
			score -= 50;
		}
		return score;
	}

	static boolean isRerecordingAlbum(String normalizedAlbum) {
		return RERECORDING_ALBUMS.stream().anyMatch(marker -> containsWords(normalizedAlbum, marker));
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
