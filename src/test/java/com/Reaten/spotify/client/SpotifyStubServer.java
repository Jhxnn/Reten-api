package com.Reaten.spotify.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/** Servidor HTTP local que imita o Spotify: respostas enfileiradas por "MÉTODO caminho". */
public class SpotifyStubServer implements AutoCloseable {

	public record Reply(int status, String body) {
	}

	public record Request(String method, String path, String query, String authorization, String body) {
	}

	private final HttpServer server;
	private final Map<String, Queue<Reply>> replies = new ConcurrentHashMap<>();
	public final List<Request> requests = new CopyOnWriteArrayList<>();

	public SpotifyStubServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
			requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
					exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Authorization"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			Queue<Reply> queue = replies.get(key);
			Reply reply = queue == null || queue.isEmpty() ? new Reply(404, "{}") : (queue.size() > 1 ? queue.poll() : queue.peek());
			byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.getResponseHeaders().add("Retry-After", "0");
			exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.start();
	}

	/** A última resposta enfileirada se repete para chamadas seguintes. */
	public SpotifyStubServer on(String methodAndPath, int status, String body) {
		replies.computeIfAbsent(methodAndPath, k -> new ConcurrentLinkedQueue<>()).add(new Reply(status, body));
		return this;
	}

	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public List<Request> requestsTo(String methodAndPath) {
		return requests.stream().filter(r -> (r.method() + " " + r.path()).equals(methodAndPath)).toList();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
