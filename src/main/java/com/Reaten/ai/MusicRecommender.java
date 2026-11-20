package com.Reaten.ai;

/**
 * Porta para a interpretação literária e as recomendações musicais.
 * A implementação padrão usa a OpenAI; o restante do sistema só conhece esta interface.
 */
public interface MusicRecommender {

	/** false quando não há credenciais — a geração deve recusar com mensagem clara. */
	boolean isConfigured();

	/** Identificador do modelo, guardado junto da playlist para rastreabilidade. */
	String modelName();

	/**
	 * @throws RecommenderException quando a IA falha, recusa ou devolve algo inválido
	 */
	RecommendationResult recommend(RecommendationRequest request);
}
