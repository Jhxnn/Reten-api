package com.Reaten.ai.openai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.Reaten.ai.MusicRecommender;
import com.Reaten.ai.RecommendationRequest;
import com.Reaten.ai.RecommendationResult;
import com.Reaten.ai.RecommenderException;
import com.Reaten.ai.RecommenderException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recomendações via OpenAI Responses API com Structured Outputs (json_schema estrito).
 * Erros transitórios (429, 5xx, timeout) têm até {@code maxRetries} novas tentativas com espera crescente,
 * respeitando Retry-After. Falta de crédito (insufficient_quota) não é repetida.
 */
@Component
class OpenAiMusicRecommender implements MusicRecommender {

	private static final Logger log = LoggerFactory.getLogger(OpenAiMusicRecommender.class);

	private final OpenAiProperties properties;
	private final JsonMapper json;
	private final RestClient client;

	OpenAiMusicRecommender(OpenAiProperties properties, JsonMapper json) {
		this.properties = properties;
		this.json = json;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		factory.setReadTimeout(properties.timeout());
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
	}

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public String modelName() {
		return properties.model();
	}

	@Override
	public RecommendationResult recommend(RecommendationRequest request) {
		if (!isConfigured()) {
			throw new RecommenderException(Reason.NOT_CONFIGURED, "OPENAI_API_KEY não configurada", null);
		}
		Map<String, Object> body = requestBody(request);
		JsonNode response = sendWithRetries(body);
		return parse(response);
	}

	private Map<String, Object> requestBody(RecommendationRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.model());
		body.put("instructions", PlaylistPrompt.instructions());
		body.put("input", PlaylistPrompt.input(request));
		body.put("max_output_tokens", properties.maxOutputTokens());
		body.put("store", false);
		if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
			body.put("reasoning", Map.of("effort", properties.reasoningEffort()));
		}
		body.put("text", Map.of("format", Map.of(
				"type", "json_schema",
				"name", "reten_playlist",
				"strict", true,
				"schema", PlaylistPrompt.schema())));
		return body;
	}

	private JsonNode sendWithRetries(Map<String, Object> body) {
		int attempt = 0;
		while (true) {
			try {
				return client.post()
						.uri("/responses")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.body(body)
						.retrieve()
						.body(JsonNode.class);
			} catch (RestClientResponseException ex) {
				int status = ex.getStatusCode().value();
				String responseBody = ex.getResponseBodyAsString();
				if (status == 429 && responseBody.contains("insufficient_quota")) {
					throw new RecommenderException(Reason.RATE_LIMITED, "Conta OpenAI sem crédito disponível", ex);
				}
				boolean transientError = status == 429 || status >= 500;
				if (!transientError || attempt >= properties.maxRetries()) {
					log.warn("OpenAI respondeu {}: {}", status, abbreviate(responseBody));
					throw new RecommenderException(status == 429 ? Reason.RATE_LIMITED : Reason.UNAVAILABLE,
							"OpenAI respondeu " + status, ex);
				}
				waitBeforeRetry(attempt, ex.getResponseHeaders());
			} catch (ResourceAccessException ex) {
				if (attempt >= properties.maxRetries()) {
					throw new RecommenderException(Reason.UNAVAILABLE, "OpenAI indisponível ou lenta demais", ex);
				}
				waitBeforeRetry(attempt, null);
			}
			attempt++;
		}
	}

	RecommendationResult parse(JsonNode response) {
		if (response == null) {
			throw new RecommenderException(Reason.INVALID_RESPONSE, "Resposta vazia da OpenAI", null);
		}
		String status = response.path("status").asString("");
		if ("incomplete".equals(status)) {
			throw new RecommenderException(Reason.INVALID_RESPONSE,
					"Resposta incompleta da OpenAI: " + response.path("incomplete_details").path("reason").asString("desconhecido"), null);
		}
		for (JsonNode item : response.path("output")) {
			if (!"message".equals(item.path("type").asString(""))) {
				continue;
			}
			for (JsonNode content : item.path("content")) {
				String type = content.path("type").asString("");
				if ("refusal".equals(type)) {
					throw new RecommenderException(Reason.REFUSED, "A IA recusou o pedido: " + content.path("refusal").asString(""), null);
				}
				if ("output_text".equals(type)) {
					try {
						return json.readValue(content.path("text").asString(""), RecommendationResult.class);
					} catch (JacksonException ex) {
						throw new RecommenderException(Reason.INVALID_RESPONSE, "JSON da IA fora do formato esperado", ex);
					}
				}
			}
		}
		throw new RecommenderException(Reason.INVALID_RESPONSE, "A resposta da OpenAI não trouxe texto", null);
	}

	private static void waitBeforeRetry(int attempt, HttpHeaders headers) {
		long millis = (long) (2000 * Math.pow(2, attempt));
		if (headers != null && headers.getFirst(HttpHeaders.RETRY_AFTER) != null) {
			try {
				millis = Math.min(30_000, Long.parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER).strip()) * 1000);
			} catch (NumberFormatException ignored) {
				// mantém o backoff exponencial
			}
		}
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RecommenderException(Reason.UNAVAILABLE, "Geração interrompida", ex);
		}
	}

	private static String abbreviate(String text) {
		return text == null || text.length() <= 300 ? text : text.substring(0, 300) + "…";
	}
}
