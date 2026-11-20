package com.Reaten.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.Reaten.ai.RecommendationResult.Recommendation;
import com.Reaten.ai.RecommenderException.Reason;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Confere e limpa o que a IA devolveu antes de qualquer uso, seja qual for o provedor:
 * campos obrigatórios e tamanhos, duplicatas, excesso de músicas do mesmo artista e quantidade mínima.
 */
@Component
public class RecommendationValidator {

	static final int MIN_RECOMMENDATIONS = 10;
	static final int MAX_PER_ARTIST = 2;

	private final Validator validator;

	RecommendationValidator(Validator validator) {
		this.validator = validator;
	}

	public RecommendationResult validate(RecommendationResult result, int maxTracks) {
		if (result == null) {
			throw new RecommenderException(Reason.INVALID_RESPONSE, "Resposta vazia", null);
		}
		// Sugestões individuais inválidas são descartadas; o restante precisa estar íntegro.
		List<Recommendation> usable = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		Map<String, Integer> perArtist = new HashMap<>();
		for (Recommendation item : result.recommendations() == null ? List.<Recommendation>of() : result.recommendations()) {
			if (item == null || !validator.validate(item).isEmpty()) {
				continue;
			}
			String artistKey = normalize(item.artist());
			String key = normalize(item.title()) + "|" + artistKey;
			if (!seen.add(key) || perArtist.merge(artistKey, 1, Integer::sum) > MAX_PER_ARTIST) {
				continue;
			}
			usable.add(new Recommendation(item.title().strip(), item.artist().strip(), item.reason().strip(),
					item.searchQuery().strip()));
			if (usable.size() == maxTracks) {
				break;
			}
		}

		RecommendationResult cleaned = new RecommendationResult(
				strip(result.playlistName()), strip(result.description()), strip(result.musicalStyle()),
				result.atmosphere(), usable);
		Set<ConstraintViolation<RecommendationResult>> violations = validator.validate(cleaned);
		if (!violations.isEmpty()) {
			ConstraintViolation<RecommendationResult> first = violations.iterator().next();
			throw new RecommenderException(Reason.INVALID_RESPONSE,
					"Campo inválido na resposta da IA: " + first.getPropertyPath() + " " + first.getMessage(), null);
		}
		if (usable.size() < MIN_RECOMMENDATIONS) {
			throw new RecommenderException(Reason.INVALID_RESPONSE,
					"A IA sugeriu só " + usable.size() + " músicas válidas", null);
		}
		return cleaned;
	}

	/** Compara ignorando acentos, maiúsculas e pontuação: "Gymnopédie No. 1" == "gymnopedie no 1". */
	static String normalize(String value) {
		String decomposed = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").strip();
	}

	private static String strip(String value) {
		return value == null ? null : value.strip();
	}
}
