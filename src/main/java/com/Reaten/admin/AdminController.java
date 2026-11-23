package com.Reaten.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.Reaten.admin.AdminDtos.AdminPlaylist;
import com.Reaten.admin.AdminDtos.AdminPlaylistDetail;
import com.Reaten.admin.AdminDtos.AdminUser;
import com.Reaten.admin.AdminDtos.Overview;
import com.Reaten.auth.AuthenticatedUser;
import com.Reaten.auth.emailverification.EmailVerificationService;
import com.Reaten.shared.exception.ApiException;

/**
 * Área de administração. Quase tudo é leitura; a única ação é confirmar o e-mail de um leitor.
 * O acesso (papel ADMIN) é exigido no SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final AdminQueries queries;
	private final EmailVerificationService emailVerification;

	AdminController(AdminQueries queries, EmailVerificationService emailVerification) {
		this.queries = queries;
		this.emailVerification = emailVerification;
	}

	@GetMapping("/overview")
	Overview overview() {
		return queries.overview();
	}

	@GetMapping("/users")
	List<AdminUser> users() {
		return queries.users();
	}

	/** Confirma o e-mail sem código. Repetir não faz nada (o e-mail já estará confirmado). */
	@PostMapping("/users/{id}/email-verification")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirmEmail(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser admin) {
		emailVerification.confirmByAdmin(id, admin.id());
	}

	@GetMapping("/playlists")
	List<AdminPlaylist> playlists(@RequestParam(required = false) UUID userId) {
		return queries.playlists(userId);
	}

	@GetMapping("/playlists/{id}")
	AdminPlaylistDetail playlist(@PathVariable UUID id) {
		return queries.playlist(id)
				.orElseThrow(() -> ApiException.notFound("playlist_not_found", "Playlist não encontrada."));
	}
}
