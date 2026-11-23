package com.Reaten.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Reaten.admin.AdminDtos.AdminPlaylist;
import com.Reaten.admin.AdminDtos.AdminPlaylistDetail;
import com.Reaten.admin.AdminDtos.AdminUser;
import com.Reaten.admin.AdminDtos.Overview;
import com.Reaten.shared.exception.ApiException;

/** Área de administração: só leitura. O acesso (papel ADMIN) é exigido no SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final AdminQueries queries;

	AdminController(AdminQueries queries) {
		this.queries = queries;
	}

	@GetMapping("/overview")
	Overview overview() {
		return queries.overview();
	}

	@GetMapping("/users")
	List<AdminUser> users() {
		return queries.users();
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
