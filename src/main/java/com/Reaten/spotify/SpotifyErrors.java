package com.Reaten.spotify;

import org.springframework.http.HttpStatus;

import com.Reaten.shared.exception.ApiException;
import com.Reaten.spotify.client.SpotifyApiException;

public final class SpotifyErrors {

	private SpotifyErrors() {
	}

	public static ApiException toApiException(SpotifyApiException ex) {
		return switch (ex.getKind()) {
			case RATE_LIMITED -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "spotify_rate_limited",
					"O Spotify está recebendo muitas requisições agora. Tente novamente em instantes.", ex);
			case USER_NOT_ALLOWED -> new ApiException(HttpStatus.FORBIDDEN, "spotify_user_not_allowed",
					"Durante o beta, a Spotify só libera contas autorizadas manualmente pelo Resten.", ex);
			case INVALID_GRANT, TOKEN_EXPIRED -> new ApiException(HttpStatus.CONFLICT, "spotify_reauthorization_required",
					"Sua conexão com o Spotify expirou. Conecte novamente para continuar.", ex);
			case NOT_FOUND -> new ApiException(HttpStatus.NOT_FOUND, "spotify_not_found",
					"O Spotify não encontrou este item.", ex);
			case UNAVAILABLE -> new ApiException(HttpStatus.BAD_GATEWAY, "spotify_unavailable",
					"Não conseguimos falar com o Spotify agora. Tente novamente em instantes.", ex);
		};
	}
}
