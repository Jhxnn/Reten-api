package com.Reaten.spotify.client;

import java.time.Duration;

/** Falha ao falar com a Spotify, já classificada para quem chama decidir o que fazer. */
public class SpotifyApiException extends RuntimeException {

	public enum Kind {
		/** Refresh token revogado/expirado ou código inválido: o usuário precisa autorizar de novo. */
		INVALID_GRANT,
		/** Conta não cadastrada na allowlist do app em Development Mode, ou recurso proibido (HTTP 403). */
		USER_NOT_ALLOWED,
		/** HTTP 401: token expirado ou revogado entre a emissão e o uso. */
		TOKEN_EXPIRED,
		/** HTTP 404. */
		NOT_FOUND,
		/** HTTP 429. */
		RATE_LIMITED,
		/** Erro 5xx, timeout ou resposta inesperada. */
		UNAVAILABLE
	}

	private final Kind kind;
	private final Duration retryAfter;

	public SpotifyApiException(Kind kind, String message, Duration retryAfter, Throwable cause) {
		super(message, cause);
		this.kind = kind;
		this.retryAfter = retryAfter;
	}

	public Kind getKind() {
		return kind;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}
}
