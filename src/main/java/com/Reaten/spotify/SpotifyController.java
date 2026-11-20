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
import jakarta.servlet.http.HttpSession;

/**
 * Conexão com o Spotify.
 * <pre>
 * GET    /api/spotify/connection   estado da conexão do usuário
 * GET    /api/spotify/authorize    redireciona para o consentimento da Spotify (navegação do browser; ?voltar=/caminho)
 * GET    /api/spotify/callback     retorno da Spotify; redireciona para o frontend com o resultado
 * DELETE /api/spotify/connection   desconecta (apaga os tokens)
 * </pre>
 */
@RestController
@RequestMapping("/api/spotify")
class SpotifyController {

	static final String RETURN_PATH_ATTRIBUTE = "reten.spotify.returnPath";
	static final String DEFAULT_RETURN = "/inicio";

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

	/**
	 * @param voltar caminho interno do frontend para onde voltar depois do consentimento (ex.: a playlist
	 *               que a pessoa queria salvar). Só caminhos internos são aceitos.
	 */
	@GetMapping("/authorize")
	ResponseEntity<Void> authorize(@RequestParam(required = false) String voltar, HttpServletRequest request) {
		String returnPath = safeReturnPath(voltar);
		if (!oauth.isConfigured()) {
			return redirectToFrontend(Outcome.NOT_CONFIGURED, returnPath);
		}
		request.getSession().setAttribute(RETURN_PATH_ATTRIBUTE, returnPath);
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
		HttpSession session = request.getSession(false);
		String returnPath = session == null ? DEFAULT_RETURN : safeReturnPath((String) session.getAttribute(RETURN_PATH_ATTRIBUTE));
		if (session != null) {
			session.removeAttribute(RETURN_PATH_ATTRIBUTE);
		}
		if (user == null) {
			return redirectToFrontend(Outcome.SESSION_EXPIRED, DEFAULT_RETURN);
		}
		return redirectToFrontend(oauth.completeAuthorization(user.id(), code, state, error, session), returnPath);
	}

	/** Evita redirecionamento aberto: só caminhos do próprio frontend, sem esquema nem host. */
	static String safeReturnPath(String value) {
		if (value == null || !value.matches("/[A-Za-z0-9/_-]{0,200}") || value.startsWith("//")) {
			return DEFAULT_RETURN;
		}
		return value;
	}

	@DeleteMapping("/connection")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disconnect(@AuthenticationPrincipal AuthenticatedUser user) {
		accounts.disconnect(user.id());
	}

	private ResponseEntity<Void> redirectToFrontend(Outcome outcome, String path) {
		UriComponentsBuilder target = UriComponentsBuilder.fromUriString(properties.frontendUrl()).path(path);
		if (outcome == Outcome.CONNECTED) {
			target.queryParam("spotify", "conectado");
		} else {
			target.queryParam("spotify", "erro").queryParam("motivo", outcome.code);
		}
		return ResponseEntity.status(HttpStatus.FOUND).location(target.build().toUri()).build();
	}
}
