package com.Reaten.spotify.client;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.Reaten.spotify.SpotifyProperties;
import com.Reaten.spotify.client.SpotifyApiException.Kind;
import com.fasterxml.jackson.annotation.JsonProperty;

@Component
class HttpSpotifyAuthClient implements SpotifyAuthClient {

	private final SpotifyProperties properties;
	private final RestClient accounts;
	private final RestClient api;

	HttpSpotifyAuthClient(SpotifyProperties properties) {
		this.properties = properties;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		this.accounts = RestClient.builder().baseUrl(properties.accountsBaseUrl()).requestFactory(factory).build();
		this.api = RestClient.builder().baseUrl(properties.apiBaseUrl()).requestFactory(factory).build();
	}

	@Override
	public String authorizeUrl(String state) {
		return UriComponentsBuilder.fromUriString(properties.accountsBaseUrl())
				.path("/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", properties.clientId())
				.queryParam("scope", properties.scopes())
				.queryParam("redirect_uri", properties.redirectUri())
				.queryParam("state", state)
				.encode()
				.build()
				.toUriString();
	}

	@Override
	public TokenGrant exchangeCode(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("redirect_uri", properties.redirectUri());
		return requestToken(form);
	}

	@Override
	public TokenGrant refresh(String refreshToken) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "refresh_token");
		form.add("refresh_token", refreshToken);
		return requestToken(form);
	}

	@Override
	public SpotifyProfile currentProfile(String accessToken) {
		ProfileBody body = call(() -> api.get()
				.uri("/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(ProfileBody.class));
		return new SpotifyProfile(body.id(), body.displayName());
	}

	private TokenGrant requestToken(MultiValueMap<String, String> form) {
		TokenBody body = call(() -> accounts.post()
				.uri("/api/token")
				.header(HttpHeaders.AUTHORIZATION, basicAuth())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenBody.class));
		if (body == null || body.accessToken() == null) {
			throw new SpotifyApiException(Kind.UNAVAILABLE, "Resposta de token vazia da Spotify", null, null);
		}
		return new TokenGrant(body.accessToken(), body.refreshToken(), body.scope(),
				Duration.ofSeconds(body.expiresIn()));
	}

	private String basicAuth() {
		String credentials = properties.clientId() + ":" + properties.clientSecret();
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

	/** Traduz erros HTTP da Spotify para categorias que o domínio entende. */
	private <T> T call(java.util.function.Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException ex) {
			HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
			if (status == HttpStatus.BAD_REQUEST && ex.getResponseBodyAsString().contains("invalid_grant")) {
				throw new SpotifyApiException(Kind.INVALID_GRANT, "Autorização Spotify inválida ou revogada", null, ex);
			}
			if (status == HttpStatus.FORBIDDEN) {
				throw new SpotifyApiException(Kind.USER_NOT_ALLOWED, "Conta Spotify sem acesso ao app", null, ex);
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

	private record TokenBody(
			@JsonProperty("access_token") String accessToken,
			@JsonProperty("refresh_token") String refreshToken,
			@JsonProperty("scope") String scope,
			@JsonProperty("expires_in") long expiresIn) {
	}

	private record ProfileBody(
			@JsonProperty("id") String id,
			@JsonProperty("display_name") String displayName) {
	}
}
