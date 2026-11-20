package com.Reaten.spotify.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.Reaten.spotify.client.SpotifyApiException.Kind;

/** Infraestrutura HTTP comum aos clientes da Spotify: timeouts e tradução de erros. */
public final class SpotifyHttp {

	private SpotifyHttp() {
	}

	public static RestClient client(String baseUrl) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(15));
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	/** Traduz erros HTTP da Spotify para categorias que o domínio entende. */
	public static <T> T call(Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException ex) {
			HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
			if (status == HttpStatus.BAD_REQUEST && ex.getResponseBodyAsString().contains("invalid_grant")) {
				throw new SpotifyApiException(Kind.INVALID_GRANT, "Autorização Spotify inválida ou revogada", null, ex);
			}
			if (status == HttpStatus.UNAUTHORIZED) {
				throw new SpotifyApiException(Kind.TOKEN_EXPIRED, "Token Spotify expirado ou inválido", null, ex);
			}
			if (status == HttpStatus.FORBIDDEN) {
				throw new SpotifyApiException(Kind.USER_NOT_ALLOWED, "Acesso negado pela Spotify", null, ex);
			}
			if (status == HttpStatus.NOT_FOUND) {
				throw new SpotifyApiException(Kind.NOT_FOUND, "Recurso não encontrado na Spotify", null, ex);
			}
			if (status == HttpStatus.TOO_MANY_REQUESTS) {
				throw new SpotifyApiException(Kind.RATE_LIMITED, "Limite de requisições da Spotify atingido",
						retryAfter(ex.getResponseHeaders()), ex);
			}
			throw new SpotifyApiException(Kind.UNAVAILABLE, "Spotify respondeu " + ex.getStatusCode(), null, ex);
		} catch (ResourceAccessException ex) {
			throw new SpotifyApiException(Kind.UNAVAILABLE, "Spotify indisponível", null, ex);
		}
	}

	private static Duration retryAfter(HttpHeaders headers) {
		if (headers == null || headers.getFirst(HttpHeaders.RETRY_AFTER) == null) {
			return null;
		}
		try {
			return Duration.ofSeconds(Long.parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER).strip()));
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}
