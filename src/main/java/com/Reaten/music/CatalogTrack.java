package com.Reaten.music;

import java.util.List;

/**
 * Faixa real de um catálogo de streaming, como devolvida pelo provedor.
 *
 * @param id  id da faixa no provedor (ex.: id do Spotify)
 * @param url link público para ouvir a faixa
 */
public record CatalogTrack(
		String id,
		String name,
		List<String> artists,
		String album,
		String imageUrl,
		Integer durationMs,
		String url) {

	public CatalogTrack {
		artists = artists == null ? List.of() : List.copyOf(artists);
	}

	public String uri() {
		return "spotify:track:" + id;
	}
}
