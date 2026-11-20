package com.Reaten.book.catalog;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

/** Utilidades compartilhadas pelos adaptadores de catálogo. */
final class CatalogHttp {

	static final long RETRY_DELAY_MS = 600;

	private CatalogHttp() {
	}

	static RestClient client(String baseUrl) {
		HttpClient http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.defaultHeader("User-Agent", "Reten/0.1")
				.build();
	}

	static <T> T call(String source, Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException ex) {
			// Instabilidade momentânea do catálogo (comum no Open Library): uma nova tentativa curta.
			if (isTransient(ex.getStatusCode().value())) {
				pause();
				try {
					return request.get();
				} catch (RestClientResponseException retry) {
					throw unavailable(source, retry);
				} catch (ResourceAccessException retry) {
					throw new CatalogUnavailableException(source + " indisponível", false, retry);
				}
			}
			throw unavailable(source, ex);
		} catch (ResourceAccessException ex) {
			throw new CatalogUnavailableException(source + " indisponível", false, ex);
		}
	}

	private static boolean isTransient(int status) {
		return status == 502 || status == 503 || status == 504;
	}

	private static CatalogUnavailableException unavailable(String source, RestClientResponseException ex) {
		boolean limited = ex.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
				|| ex.getResponseBodyAsString().contains("rateLimitExceeded");
		return new CatalogUnavailableException(source + " respondeu " + ex.getStatusCode(), limited, ex);
	}

	private static void pause() {
		try {
			Thread.sleep(RETRY_DELAY_MS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	static String text(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asString();
		return value == null || value.isBlank() ? null : value.strip();
	}

	static List<String> texts(JsonNode array) {
		List<String> values = new ArrayList<>();
		if (array != null && array.isArray()) {
			for (JsonNode item : array) {
				String value = text(item);
				if (value != null) {
					values.add(value);
				}
			}
		}
		return values;
	}

	static Integer integer(JsonNode node) {
		return node == null || !node.isNumber() ? null : node.asInt();
	}
}
