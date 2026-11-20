package com.Reaten.music;

import java.util.List;
import java.util.UUID;

/**
 * Porta para publicar uma playlist na conta de streaming do usuário.
 * A implementação cuida de autenticação, limites e formato do provedor.
 */
public interface PlaylistPublisher {

	boolean isConfigured();

	/**
	 * Cria a playlist ou, se {@code existingId} ainda existir na conta, atualiza nome, descrição e faixas.
	 *
	 * @param trackIds ids das faixas no catálogo, na ordem da playlist
	 * @throws com.Reaten.shared.exception.ApiException com código estável (ex.: spotify_not_connected)
	 */
	PublishedPlaylist publish(UUID userId, String existingId, String name, String description, List<String> trackIds);

	record PublishedPlaylist(String id, String url) {
	}
}
