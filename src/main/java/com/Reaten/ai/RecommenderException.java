package com.Reaten.ai;

/** Falha da IA já classificada; {@code code} vai para o banco e para o frontend. */
public class RecommenderException extends RuntimeException {

	public enum Reason {
		NOT_CONFIGURED("ai_not_configured"),
		RATE_LIMITED("ai_rate_limited"),
		REFUSED("ai_refused"),
		INVALID_RESPONSE("ai_invalid_response"),
		UNAVAILABLE("ai_unavailable");

		public final String code;

		Reason(String code) {
			this.code = code;
		}
	}

	private final Reason reason;

	public RecommenderException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
