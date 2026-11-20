package com.Reaten.book;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Um livro na história de um usuário: quando foi escolhido pela primeira e pela última vez. */
@Entity
@Table(name = "user_books")
class UserBook {

	@Embeddable
	record Key(@Column(name = "user_id") UUID userId, @Column(name = "book_id") UUID bookId) implements Serializable {
	}

	@EmbeddedId
	private Key key;

	// Escrita feita por SQL nativo (upsert) em UserBookRepository; aqui a relação é só leitura.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_id", insertable = false, updatable = false)
	private Book book;

	@Column(name = "first_selected_at", nullable = false)
	private Instant firstSelectedAt;

	@Column(name = "last_selected_at", nullable = false)
	private Instant lastSelectedAt;

	protected UserBook() {
	}

	Book getBook() {
		return book;
	}

	Instant getLastSelectedAt() {
		return lastSelectedAt;
	}
}
