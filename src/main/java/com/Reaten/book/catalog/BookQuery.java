package com.Reaten.book.catalog;

import java.util.regex.Pattern;

/** Pesquisa normalizada. ISBNs digitados no campo geral são reconhecidos automaticamente. */
public record BookQuery(String text, Field field) {

	public enum Field {
		ALL, TITLE, AUTHOR, ISBN
	}

	private static final Pattern ISBN = Pattern.compile("^(97[89])?\\d{9}[\\dXx]$");

	public static BookQuery of(String rawText, Field requested) {
		String text = rawText == null ? "" : rawText.strip().replaceAll("\\s+", " ");
		Field field = requested == null ? Field.ALL : requested;
		String compact = text.replace("-", "").replace(" ", "");
		if (field == Field.ISBN || (field == Field.ALL && ISBN.matcher(compact).matches())) {
			return new BookQuery(compact.toUpperCase(), Field.ISBN);
		}
		return new BookQuery(text, field);
	}
}
