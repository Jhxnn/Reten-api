package com.Reaten.playlist;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.Reaten.auth.AuthenticatedUser;
import com.Reaten.playlist.dto.PlaylistDtos.AddTrackRequest;
import com.Reaten.playlist.dto.PlaylistDtos.CatalogTrackResponse;
import com.Reaten.playlist.dto.PlaylistDtos.GenerateRequest;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.Reaten.playlist.dto.PlaylistDtos.UpdateRequest;
import com.Reaten.playlist.dto.PlaylistDtos.UsageResponse;

import jakarta.validation.Valid;

/**
 * <pre>
 * POST   /api/playlists                         inicia a geração (202, status GENERATING)
 * GET    /api/playlists/usage                   gerações usadas nas últimas 24h
 * GET    /api/playlists/{id}                    estado e resultado
 * DELETE /api/playlists/{id}                    apaga do Reten (libera vaga; a cópia no Spotify fica)
 * PATCH  /api/playlists/{id}                    edita nome e descrição
 * DELETE /api/playlists/{id}/tracks/{trackId}   remove uma faixa
 * GET    /api/playlists/{id}/replacements?q=    busca faixas no Spotify para substituir/acrescentar
 * POST   /api/playlists/{id}/tracks             acrescenta uma faixa do Spotify
 * POST   /api/playlists/{id}/verify             refaz a verificação das sugestões no Spotify
 * POST   /api/playlists/{id}/spotify            cria ou atualiza a playlist na conta Spotify
 * </pre>
 */
@RestController
@RequestMapping("/api/playlists")
class PlaylistController {

	private final PlaylistService service;
	private final PlaylistEditingService editing;

	PlaylistController(PlaylistService service, PlaylistEditingService editing) {
		this.service = service;
		this.editing = editing;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	PlaylistResponse generate(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody GenerateRequest request) {
		return service.requestGeneration(user.id(), request);
	}

	@GetMapping("/usage")
	UsageResponse usage(@AuthenticationPrincipal AuthenticatedUser user) {
		return service.usage(user.id());
	}

	@GetMapping("/{id}")
	PlaylistResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return service.get(user.id(), id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		service.delete(user.id(), id);
	}

	@PatchMapping("/{id}")
	PlaylistResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody UpdateRequest request) {
		return editing.update(user.id(), id, request);
	}

	@DeleteMapping("/{id}/tracks/{trackId}")
	PlaylistResponse removeTrack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@PathVariable UUID trackId) {
		return editing.removeTrack(user.id(), id, trackId);
	}

	@GetMapping("/{id}/replacements")
	List<CatalogTrackResponse> replacements(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@RequestParam String q) {
		return editing.searchReplacements(user.id(), id, q);
	}

	@PostMapping("/{id}/tracks")
	PlaylistResponse addTrack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
			@Valid @RequestBody AddTrackRequest request) {
		return editing.addTrack(user.id(), id, request.spotifyTrackId());
	}

	@PostMapping("/{id}/verify")
	PlaylistResponse reverify(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return editing.reverify(user.id(), id);
	}

	@PostMapping("/{id}/spotify")
	PlaylistResponse publish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
		return editing.publish(user.id(), id);
	}
}
