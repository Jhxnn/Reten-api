package com.Reaten.music;

import java.util.List;
import java.util.Optional;

/**
 * Porta para o catálogo musical usado na verificação das sugestões e na busca de substituições.
 * O domínio de playlists só conhece esta interface; o Spotify é uma implementação.
 */
public interface MusicCatalog {

	boolean isConfigured();

	/** @param limit no máximo 10 (limite da Spotify Web API em Development Mode) */
	List<CatalogTrack> searchTracks(String query, int limit);

	Optional<CatalogTrack> findTrack(String id);
}
