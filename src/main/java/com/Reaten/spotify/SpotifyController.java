package com.Reaten.spotify;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.Reaten.auth.AuthenticatedUser;
import com.Reaten.config.RetenProperties;
import com.Reaten.spotify.SpotifyOAuthService.Outcome;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Conexão com o Spotify.
 * <pre>
 * GET    /api/spotify/connection   estado da conexão do usuário
 * GET    /api/spotify/authorize    redireciona para o consentimento da Spotify (navegação do browser)
 * GET    /api/spotify/callback     retorno da Spotify; redireciona para o frontend com o resultado
 * DELETE /api/spotify/connection   desconecta (apaga os tokens)
 * </pre>
 */
@RestController
@RequestMapping("/api/spotify")
class SpotifyController {

	private final SpotifyOAuthService oauth;
	private final SpotifyAccountService accounts;
	private final RetenProperties properties;

	SpotifyController(SpotifyOAuthService oauth, SpotifyAccountService accounts, RetenProperties properties) {
		this.oauth = oauth;
		this.accounts = accounts;
		this.properties = properties;
	}

	record ConnectionResponse(boolean configured, boolean connected, String displayName, Instant connectedAt) {
	}

	@GetMapping("/connection")
	ConnectionResponse connection(@AuthenticationPrincipal AuthenticatedUser user) {
		return accounts.findConnection(user.id())
				.map(a -> new ConnectionResponse(oauth.isConfigured(), true, a.getDisplayName(), a.getCreatedAt()))
				.orElseGet(() -> new ConnectionResponse(oauth.isConfigured(), false, null, null));
	}

	@GetMapping("/authorize")
	ResponseEntity<Void> authorize(HttpServletRequest request) {
		if (!oauth.isConfigured()) {
			return redirectToFrontend(Outcome.NOT_CONFIGURED);
		}
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(oauth.startAuthorization(request.getSession())))
				.build();
	}

	@GetMapping("/callback")
	ResponseEntity<Void> callback(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			HttpServletRequest request) {
		if (user == null) {
			return redirectToFrontend(Outcome.SESSION_EXPIRED);
		}
		return redirectToFrontend(oauth.completeAuthorization(user.id(), code, state, error, request.getSession(false)));
	}

	@DeleteMapping("/connection")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disconnect(@AuthenticationPrincipal AuthenticatedUser user) {
		accounts.disconnect(user.id());
	}

	private ResponseEntity<Void> redirectToFrontend(Outcome outcome) {
		UriComponentsBuilder target = UriComponentsBuilder.fromUriString(properties.frontendUrl()).path("/inicio");
		if (outcome == Outcome.CONNECTED) {
			target.queryParam("spotify", "conectado");
		} else {
			target.queryParam("spotify", "erro").queryParam("motivo", outcome.code);
		}
		return ResponseEntity.status(HttpStatus.FOUND).location(target.build().toUri()).build();
	}
}
