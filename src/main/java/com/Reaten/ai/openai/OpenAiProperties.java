package com.Reaten.ai.openai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param reasoningEffort enviado só quando preenchido. Use vazio para modelos sem raciocínio.
 */
@ConfigurationProperties(prefix = "reten.openai")
public record OpenAiProperties(
		String apiKey,
		@DefaultValue("gpt-5-mini") String model,
		@DefaultValue("low") String reasoningEffort,
		@DefaultValue("https://api.openai.com/v1") String baseUrl,
		@DefaultValue("120s") Duration timeout,
		@DefaultValue("8000") int maxOutputTokens,
		@DefaultValue("2") int maxRetries) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
