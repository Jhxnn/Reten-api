package com.Reaten.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.Reaten.ai.ListeningMode;
import com.Reaten.ai.RecommendationRequest;
import com.Reaten.ai.RecommendationResult;
import com.Reaten.ai.RecommenderException;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Testa o cliente contra um servidor local que imita a OpenAI Responses API. */
class OpenAiMusicRecommenderTest {

	record Reply(int status, String body) {
	}

	private final JsonMapper json = JsonMapper.builder().build();
	private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
	private final List<String> requestBodies = new CopyOnWriteArrayList<>();
	private HttpServer server;
	private String baseUrl;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/responses", exchange -> {
			requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			Reply reply = replies.poll();
			byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.getResponseHeaders().add("Retry-After", "0");
			exchange.sendResponseHeaders(reply.status(), bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private OpenAiMusicRecommender recommender(String apiKey) {
		return new OpenAiMusicRecommender(
				new OpenAiProperties(apiKey, "gpt-test", "low", baseUrl, Duration.ofSeconds(5), 8000, 2), json);
	}

	private static RecommendationRequest request(String notes) {
		return new RecommendationRequest("O nome do vento", null, List.of("Patrick Rothfuss"), "2019",
				List.of("Fantasia"), "Kvothe conta a própria história.", notes, ListeningMode.THEMATIC, 24);
	}

	private String successBody() {
		String payload = """
				{"playlistName":"Silêncio em três partes","description":"Para ler devagar.","musicalStyle":"Trilha orquestral",
				"atmosphere":{"literaryGenre":"Fantasia","moods":["melancolia"],"setting":"Pousada","era":"Medieval",
				"themes":["memória"],"emotionalIntensity":"MEDIA","universe":"Um mundo de magia e música"},
				"recommendations":[{"title":"Saman","artist":"Ólafur Arnalds","reason":"Intimista.","searchQuery":"Saman Ólafur Arnalds"}]}""";
		return json.createObjectNode()
				.put("status", "completed")
				.set("output", json.createArrayNode()
						.add(json.createObjectNode().put("type", "reasoning"))
						.add(json.createObjectNode().put("type", "message")
								.set("content", json.createArrayNode()
										.add(json.createObjectNode().put("type", "output_text").put("text", payload)))))
				.toString();
	}

	@Test
	void sendsStrictStructuredOutputRequestAndParsesTheResult() {
		replies.add(new Reply(200, successBody()));

		RecommendationResult result = recommender("sk-test").recommend(request("Chorei no final"));

		assertThat(result.playlistName()).isEqualTo("Silêncio em três partes");
		assertThat(result.recommendations()).singleElement().extracting(RecommendationResult.Recommendation::artist)
				.isEqualTo("Ólafur Arnalds");

		JsonNode sent = json.readTree(requestBodies.getFirst());
		assertThat(sent.path("model").asString()).isEqualTo("gpt-test");
		assertThat(sent.path("store").asBoolean()).isFalse();
		assertThat(sent.path("reasoning").path("effort").asString()).isEqualTo("low");
		JsonNode format = sent.path("text").path("format");
		assertThat(format.path("type").asString()).isEqualTo("json_schema");
		assertThat(format.path("strict").asBoolean()).isTrue();
		assertThat(format.path("schema").path("additionalProperties").asBoolean()).isFalse();
		assertThat(sent.path("input").asString())
				.contains("Título: O nome do vento")
				.contains("<IMPRESSOES_DO_LEITOR>\nChorei no final\n</IMPRESSOES_DO_LEITOR>")
				.contains("exatamente 24 músicas");
	}

	@Test
	void retriesTransientErrorsThenSucceeds() {
		replies.add(new Reply(503, "{\"error\":{\"message\":\"overloaded\"}}"));
		replies.add(new Reply(200, successBody()));

		assertThat(recommender("sk-test").recommend(request(null)).playlistName()).isNotBlank();
		assertThat(requestBodies).hasSize(2);
	}

	@Test
	void givesUpAfterMaxRetries() {
		for (int i = 0; i < 3; i++) {
			replies.add(new Reply(500, "{}"));
		}
		assertThatThrownBy(() -> recommender("sk-test").recommend(request(null)))
				.isInstanceOf(RecommenderException.class)
				.extracting("reason").isEqualTo(RecommenderException.Reason.UNAVAILABLE);
		assertThat(requestBodies).hasSize(3);
	}

	@Test
	void doesNotRetryWhenAccountHasNoQuota() {
		replies.add(new Reply(429, "{\"error\":{\"code\":\"insufficient_quota\"}}"));
		assertThatThrownBy(() -> recommender("sk-test").recommend(request(null)))
				.extracting("reason").isEqualTo(RecommenderException.Reason.RATE_LIMITED);
		assertThat(requestBodies).hasSize(1);
	}

	@Test
	void reportsRefusals() {
		String refusal = """
				{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"Não posso ajudar."}]}]}""";
		replies.add(new Reply(200, refusal));
		assertThatThrownBy(() -> recommender("sk-test").recommend(request(null)))
				.extracting("reason").isEqualTo(RecommenderException.Reason.REFUSED);
	}

	@Test
	void reportsIncompleteResponses() {
		replies.add(new Reply(200, "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}"));
		assertThatThrownBy(() -> recommender("sk-test").recommend(request(null)))
				.extracting("reason").isEqualTo(RecommenderException.Reason.INVALID_RESPONSE);
	}

	@Test
	void refusesToCallWithoutApiKey() {
		OpenAiMusicRecommender unconfigured = recommender("");
		assertThat(unconfigured.isConfigured()).isFalse();
		assertThatThrownBy(() -> unconfigured.recommend(request(null)))
				.extracting("reason").isEqualTo(RecommenderException.Reason.NOT_CONFIGURED);
		assertThat(requestBodies).isEmpty();
	}
}
