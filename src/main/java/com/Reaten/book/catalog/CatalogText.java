package com.Reaten.book.catalog;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Limpeza de textos vindos dos catálogos (HTML, markdown, rodapés e repetições). */
public final class CatalogText {

	private static final int MAX_DESCRIPTION = 4000;

	private CatalogText() {
	}

	public static String cleanDescription(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String text = raw
				.replaceAll("(?i)<br\\s*/?>", "\n")
				.replaceAll("(?i)</p>", "\n\n")
				.replaceAll("<[^>]+>", "")
				.replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
				.replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
				.replace("\r", "");
		// Rodapés do Open Library ("----------\nAlso contained in: ...") e links markdown.
		int footer = text.indexOf("----------");
		if (footer > 0) {
			text = text.substring(0, footer);
		}
		text = text.replaceAll("\\[([^\\]]+)]\\([^)]+\\)", "$1")
				// Links markdown no estilo referência: "[Tad Williams][1]" e as linhas "[1]: https://..."
				.replaceAll("(?m)^\\s*\\[\\d+]:\\s*\\S+\\s*$", "")
				.replaceAll("\\[([^\\]]+)]\\[\\d+]", "$1")
				.replace("***", "").replace("**", "")
				.replaceAll("[ \\t]+", " ")
				.replaceAll("\\n{3,}", "\n\n")
				.strip();
		if (text.isEmpty()) {
			return null;
		}
		return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION).strip() + "…" : text;
	}

	/** Remove vazios e duplicatas (ignorando maiúsculas) e limita a quantidade. */
	public static List<String> distinct(Collection<String> values, int limit) {
		if (values == null) {
			return List.of();
		}
		Set<String> seen = new LinkedHashSet<>();
		return values.stream()
				.filter(v -> v != null && !v.isBlank())
				.map(String::strip)
				.filter(v -> seen.add(v.toLowerCase(Locale.ROOT)))
				.limit(limit)
				.toList();
	}

	public static String isbn13(Collection<String> isbns) {
		return isbns == null ? null
				: isbns.stream().map(i -> i.replace("-", "")).filter(i -> i.matches("\\d{13}")).findFirst().orElse(null);
	}

	public static String isbn10(Collection<String> isbns) {
		return isbns == null ? null
				: isbns.stream().map(i -> i.replace("-", "")).filter(i -> i.matches("\\d{9}[\\dXx]")).findFirst().orElse(null);
	}
}
