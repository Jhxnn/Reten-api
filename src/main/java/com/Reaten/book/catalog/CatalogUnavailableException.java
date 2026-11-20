package com.Reaten.book.catalog;

/** O catálogo externo não respondeu, recusou por limite de uso ou devolveu algo inesperado. */
public class CatalogUnavailableException extends RuntimeException {

	private final boolean rateLimited;

	public CatalogUnavailableException(String message, boolean rateLimited, Throwable cause) {
		super(message, cause);
		this.rateLimited = rateLimited;
	}

	public boolean isRateLimited() {
		return rateLimited;
	}
}
