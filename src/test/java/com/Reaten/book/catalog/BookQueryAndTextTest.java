package com.Reaten.book.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BookQueryAndTextTest {

	@Test
	void detectsIsbnTypedInTheGeneralField() {
		assertThat(BookQuery.of(" 978-85-99296-49-3 ", BookQuery.Field.ALL))
				.isEqualTo(new BookQuery("9788599296493", BookQuery.Field.ISBN));
		assertThat(BookQuery.of("857164398x", BookQuery.Field.ALL))
				.isEqualTo(new BookQuery("857164398X", BookQuery.Field.ISBN));
	}

	@Test
	void keepsTextQueriesAndCollapsesSpaces() {
		assertThat(BookQuery.of("  o   nome do vento ", BookQuery.Field.ALL))
				.isEqualTo(new BookQuery("o nome do vento", BookQuery.Field.ALL));
		assertThat(BookQuery.of("Rothfuss", BookQuery.Field.AUTHOR).field()).isEqualTo(BookQuery.Field.AUTHOR);
		// Número que não é ISBN continua sendo texto.
		assertThat(BookQuery.of("1984", BookQuery.Field.ALL).field()).isEqualTo(BookQuery.Field.ALL);
	}

	@Test
	void cleansHtmlMarkdownAndOpenLibraryFooters() {
		String raw = "<p>***O Nome do Vento*** conta a história de <b>Kvothe</b>.</p><br>Veja [mais](https://x.y)."
				+ "\r\n\r\n----------\r\nAlso contained in:\r\n- Box set";

		assertThat(CatalogText.cleanDescription(raw))
				.isEqualTo("O Nome do Vento conta a história de Kvothe.\n\n\nVeja mais.".replace("\n\n\n", "\n\n"));
	}

	@Test
	void cleansReferenceStyleMarkdownLinks() {
		String raw = "Compared to [Tad Williams][1] and [George R. R. Martin][2].\r\n\r\n[1]: https://a.b/x\r\n[2]: https://a.b/y";

		assertThat(CatalogText.cleanDescription(raw)).isEqualTo("Compared to Tad Williams and George R. R. Martin.");
	}

	@Test
	void distinctIgnoresCaseAndBlanks() {
		assertThat(CatalogText.distinct(List.of("Fantasy", "fantasy", " ", "Magic"), 5)).containsExactly("Fantasy", "Magic");
	}

	@Test
	void picksIsbnsByLength() {
		List<String> isbns = List.of("6555651857", "978-6555651850");
		assertThat(CatalogText.isbn13(isbns)).isEqualTo("9786555651850");
		assertThat(CatalogText.isbn10(isbns)).isEqualTo("6555651857");
	}
}
