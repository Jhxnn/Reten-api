package com.Reaten.playlist;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param dailyLimit  gerações por usuário em 24 horas (janela móvel)
 * @param trackCount  sugestões pedidas à IA; sobra margem para faixas não encontradas no Spotify
 * @param staleAfter  geração ainda "em andamento" depois disso é considerada perdida
 */
@ConfigurationProperties(prefix = "reten.generation")
public record GenerationProperties(
		@DefaultValue("10") int dailyLimit,
		@DefaultValue("24") int trackCount,
		@DefaultValue("5m") Duration staleAfter) {
}
