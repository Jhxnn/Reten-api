package com.Reaten.ai;

/** Os três modos de ouvir um livro. As descrições alimentam o prompt da IA. */
public enum ListeningMode {

	THEMATIC("Trilha temática",
			"Trilhas sonoras de cinema, séries e jogos, música instrumental e composições orquestrais, épicas, "
					+ "sombrias, românticas ou melancólicas conforme a obra. Sem vocais com letra (vocalizes sem palavras são aceitáveis)."),
	VOCAL("Músicas com voz",
			"Canções com vocal de qualquer gênero (rock, indie, pop, folk, MPB, clássica com canto, etc.), escolhidas pelos "
					+ "sentimentos, temas e atmosfera do livro. Misture artistas conhecidos e menos óbvios."),
	FOCUS("Concentração",
			"Música para leitura prolongada: lo-fi hip hop, ambient, piano instrumental, jazz suave, minimalismo. "
					+ "Sem letras, sem mudanças bruscas de dinâmica, nada que dispute a atenção com o texto.");

	private final String label;
	private final String guidance;

	ListeningMode(String label, String guidance) {
		this.label = label;
		this.guidance = guidance;
	}

	public String label() {
		return label;
	}

	public String guidance() {
		return guidance;
	}
}
