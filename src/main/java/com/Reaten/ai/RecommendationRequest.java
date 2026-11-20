package com.Reaten.ai;

import java.util.List;

/**
 * O que a IA recebe sobre o livro. Apenas metadados, sinopse e as impressões do leitor —
 * nunca o texto da obra.
 *
 * @param userNotes impressões opcionais do leitor; tratadas como dado, nunca como instrução
 * @param trackCount quantidade de sugestões pedidas (sobra margem para as que não forem encontradas no Spotify)
 */
public record RecommendationRequest(
		String title,
		String subtitle,
		List<String> authors,
		String publishedDate,
		List<String> categories,
		String synopsis,
		String userNotes,
		ListeningMode mode,
		int trackCount) {
}
