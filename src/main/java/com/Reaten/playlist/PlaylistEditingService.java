package com.Reaten.playlist;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.Reaten.music.CatalogTrack;
import com.Reaten.music.MusicCatalog;
import com.Reaten.music.PlaylistPublisher;
import com.Reaten.music.PlaylistPublisher.PublishedPlaylist;
import com.Reaten.playlist.dto.PlaylistDtos.CatalogTrackResponse;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.Reaten.playlist.dto.PlaylistDtos.UpdateRequest;
import com.Reaten.shared.exception.ApiException;

/**
 * Tudo o que acontece depois da geração: editar nome e descrição, remover e substituir faixas,
 * reverificar no catálogo e salvar na conta Spotify.
 * Chamadas externas (catálogo, Spotify) acontecem fora de transação.
 */
@Service
public class PlaylistEditingService {

	static final int MAX_TRACKS = 100;

	private final PlaylistRepository playlists;
	private final MusicCatalog catalog;
	private final PlaylistPublisher publisher;
	private final PlaylistVerification verification;
	private final PlaylistMapper mapper;
	private final TransactionTemplate transactions;

	PlaylistEditingService(PlaylistRepository playlists, MusicCatalog catalog, PlaylistPublisher publisher,
			PlaylistVerification verification, PlaylistMapper mapper, TransactionTemplate transactions) {
		this.playlists = playlists;
		this.catalog = catalog;
		this.publisher = publisher;
		this.verification = verification;
		this.mapper = mapper;
		this.transactions = transactions;
	}

	public PlaylistResponse update(UUID userId, UUID playlistId, UpdateRequest request) {
		return change(userId, playlistId, playlist -> {
			String description = request.description() == null || request.description().isBlank() ? null : request.description().strip();
			playlist.rename(request.name().strip(), description);
		});
	}

	public PlaylistResponse removeTrack(UUID userId, UUID playlistId, UUID trackId) {
		return change(userId, playlistId, playlist -> {
			if (!playlist.removeTrack(trackId)) {
				throw ApiException.notFound("track_not_found", "Esta faixa não está na playlist.");
			}
		});
	}

	/** Busca no catálogo para substituir ou acrescentar faixas; esconde o que já está na playlist. */
	public List<CatalogTrackResponse> searchReplacements(UUID userId, UUID playlistId, String query) {
		requireCatalog();
		String text = query == null ? "" : query.strip();
		if (text.length() < 2) {
			throw ApiException.badRequest("query_too_short", "Digite pelo menos 2 caracteres para buscar.");
		}
		Set<String> existing = transactions.execute(status -> editable(userId, playlistId).getTracks().stream()
				.map(PlaylistTrack::getSpotifyTrackId).collect(Collectors.toSet()));
		return catalogCall(() -> catalog.searchTracks(text, 10)).stream()
				.filter(track -> !existing.contains(track.id()))
				.map(CatalogTrackResponse::of)
				.toList();
	}

	/** Acrescenta uma faixa escolhida pelo usuário. Os dados são relidos do catálogo, nunca aceitos do cliente. */
	public PlaylistResponse addTrack(UUID userId, UUID playlistId, String spotifyTrackId) {
		requireCatalog();
		transactions.executeWithoutResult(status -> editable(userId, playlistId));
		CatalogTrack track = catalogCall(() -> catalog.findTrack(spotifyTrackId))
				.orElseThrow(() -> ApiException.notFound("track_not_found", "Não encontramos esta faixa no Spotify."));
		return change(userId, playlistId, playlist -> {
			if (playlist.containsTrack(track.id())) {
				throw ApiException.conflict("track_already_added", "Esta faixa já está na playlist.");
			}
			if (playlist.getTracks().size() >= MAX_TRACKS) {
				throw ApiException.badRequest("playlist_full", "A playlist já tem " + MAX_TRACKS + " faixas.");
			}
			playlist.addTrack(track);
		});
	}

	/** Refaz a busca das sugestões no catálogo (ex.: depois de uma falha do Spotify). Substitui as faixas atuais. */
	public PlaylistResponse reverify(UUID userId, UUID playlistId) {
		requireCatalog();
		transactions.executeWithoutResult(status -> {
			Playlist playlist = owned(userId, playlistId);
			if (playlist.getStatus() != Playlist.Status.READY) {
				throw ApiException.conflict("playlist_not_ready", "A playlist ainda não está pronta.");
			}
		});
		verification.run(playlistId);
		return transactions.execute(status -> mapper.toResponse(owned(userId, playlistId)));
	}

	/** Cria a playlist na conta Spotify do usuário ou atualiza a que já foi criada. */
	public PlaylistResponse publish(UUID userId, UUID playlistId) {
		if (!publisher.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "spotify_not_configured",
					"A integração com o Spotify ainda não foi configurada neste ambiente.");
		}
		record Snapshot(String existingId, String name, String description, List<String> trackIds) {
		}
		Snapshot snapshot = transactions.execute(status -> {
			Playlist playlist = editable(userId, playlistId);
			if (playlist.getTracks().isEmpty()) {
				throw ApiException.badRequest("playlist_empty", "Adicione pelo menos uma música antes de salvar.");
			}
			return new Snapshot(playlist.getSpotifyPlaylistId(), playlist.getName(), playlist.getDescription(),
					playlist.getTracks().stream().map(PlaylistTrack::getSpotifyTrackId).toList());
		});
		PublishedPlaylist published = publisher.publish(userId, snapshot.existingId(), snapshot.name(),
				snapshot.description(), snapshot.trackIds());
		return change(userId, playlistId, playlist -> playlist.markPublished(published.id(), published.url()));
	}

	private PlaylistResponse change(UUID userId, UUID playlistId, java.util.function.Consumer<Playlist> change) {
		return transactions.execute(status -> {
			Playlist playlist = editable(userId, playlistId);
			change.accept(playlist);
			playlists.flush();
			return mapper.toResponse(playlist);
		});
	}

	/** Só playlists prontas e verificadas têm faixas reais para editar ou publicar. */
	private Playlist editable(UUID userId, UUID playlistId) {
		Playlist playlist = owned(userId, playlistId);
		if (playlist.getStatus() != Playlist.Status.READY || playlist.getVerificationStatus() != Playlist.VerificationStatus.VERIFIED) {
			throw ApiException.conflict("playlist_not_verified",
					"Esta playlist ainda não tem faixas confirmadas no Spotify.");
		}
		return playlist;
	}

	private Playlist owned(UUID userId, UUID playlistId) {
		return playlists.findWithDetailsByIdAndUserId(playlistId, userId)
				.orElseThrow(() -> ApiException.notFound("playlist_not_found", "Playlist não encontrada."));
	}

	private void requireCatalog() {
		if (!catalog.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "spotify_not_configured",
					"A integração com o Spotify ainda não foi configurada neste ambiente.");
		}
	}

	/** Falhas do catálogo viram erro legível, sem expor detalhes do provedor. */
	private static <T> T catalogCall(java.util.function.Supplier<T> call) {
		try {
			return call.get();
		} catch (ApiException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "catalog_unavailable",
					"Não conseguimos consultar o Spotify agora. Tente novamente em instantes.", ex);
		}
	}
}
