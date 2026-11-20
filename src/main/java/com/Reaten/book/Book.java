package com.Reaten.book;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.Reaten.book.catalog.CatalogBook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Livro escolhido por algum usuário, copiado do catálogo de origem. */
@Entity
@Table(name = "books")
public class Book {

	@Id
	private UUID id;

	@Column(nullable = false, length = 20, updatable = false)
	private String source;

	@Column(name = "external_id", nullable = false, length = 100, updatable = false)
	private String externalId;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(length = 500)
	private String subtitle;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> authors;

	@Column(columnDefinition = "text")
	private String description;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> categories;

	@Column(name = "published_date", length = 10)
	private String publishedDate;

	@Column(length = 13)
	private String isbn13;

	@Column(length = 10)
	private String isbn10;

	@Column(name = "cover_url", length = 1000)
	private String coverUrl;

	@Column(name = "page_count")
	private Integer pageCount;

	@Column(length = 10)
	private String language;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Book() {
	}

	static Book from(CatalogBook source) {
		Book book = new Book();
		book.id = UUID.randomUUID();
		book.source = source.source();
		book.externalId = source.externalId();
		book.createdAt = Instant.now();
		book.refresh(source);
		return book;
	}

	/** Atualiza os dados com a versão mais recente do catálogo. */
	void refresh(CatalogBook source) {
		this.title = truncate(source.title(), 500);
		this.subtitle = truncate(source.subtitle(), 500);
		this.authors = source.authors();
		this.description = source.description();
		this.categories = source.categories();
		this.publishedDate = truncate(source.publishedDate(), 10);
		this.isbn13 = source.isbn13();
		this.isbn10 = source.isbn10();
		this.coverUrl = truncate(source.coverUrl(), 1000);
		this.pageCount = source.pageCount();
		this.language = truncate(source.language(), 10);
		this.updatedAt = Instant.now();
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	public UUID getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getTitle() {
		return title;
	}

	public String getSubtitle() {
		return subtitle;
	}

	public List<String> getAuthors() {
		return authors;
	}

	public String getDescription() {
		return description;
	}

	public List<String> getCategories() {
		return categories;
	}

	public String getPublishedDate() {
		return publishedDate;
	}

	public String getIsbn13() {
		return isbn13;
	}

	public String getIsbn10() {
		return isbn10;
	}

	public String getCoverUrl() {
		return coverUrl;
	}

	public Integer getPageCount() {
		return pageCount;
	}

	public String getLanguage() {
		return language;
	}
}
