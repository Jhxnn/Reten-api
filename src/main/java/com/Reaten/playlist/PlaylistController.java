package com.Reaten.playlist;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.Reaten.auth.AuthenticatedUser;
import com.Reaten.playlist.dto.PlaylistDtos.GenerateRequest;
import com.Reaten.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.Reaten.playlist.dto.PlaylistDtos.UsageResponse;

import jakarta.validation.Valid;

/**
 * <pre>
 * POST /api/playlists          inicia a geração (202, status GENERATING)
 * GET  /api/playlists/{id}     estado e resultado da geração
 * GET  /api/playlists/usage    gerações usadas nas últimas 24h
 * </pre>
 */
@RestController
@RequestMapping("/api/playlists")
class PlaylistController {

	private final PlaylistService service;

	PlaylistController(PlaylistService service) {
		this.service = service;
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
}
