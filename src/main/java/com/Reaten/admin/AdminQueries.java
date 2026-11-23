package com.Reaten.admin;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.admin.AdminDtos.AdminPlaylist;
import com.Reaten.admin.AdminDtos.AdminPlaylistDetail;
import com.Reaten.admin.AdminDtos.AdminTrack;
import com.Reaten.admin.AdminDtos.AdminUser;
import com.Reaten.admin.AdminDtos.Overview;

/** Consultas de leitura da área de administração, direto em SQL (agregados entre várias tabelas). */
@Repository
@Transactional(readOnly = true)
class AdminQueries {

	private static final String PLAYLIST_COLUMNS = """
			p.id, p.name, p.mode, p.status, p.created_at, p.deleted_at, p.spotify_saved_at,
			p.description, p.user_notes, p.failure_code, p.spotify_playlist_url,
			(SELECT count(*) FROM playlist_tracks t WHERE t.playlist_id = p.id) AS tracks,
			u.id AS owner_id, u.name AS owner_name, u.email AS owner_email,
			b.title AS book_title, b.authors AS book_authors
			FROM playlists p
			JOIN users u ON u.id = p.user_id
			JOIN books b ON b.id = p.book_id
			""";

	private final JdbcClient jdbc;

	AdminQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Overview overview() {
		return jdbc.sql("""
				SELECT
				  (SELECT count(*) FROM users) AS users,
				  (SELECT count(*) FROM users WHERE email_verified_at IS NOT NULL) AS verified,
				  (SELECT count(*) FROM users WHERE last_seen_at > now() - interval '7 days') AS active,
				  (SELECT count(*) FROM spotify_accounts) AS spotify,
				  (SELECT count(*) FROM playlists) AS playlists,
				  (SELECT count(*) FROM playlists WHERE created_at > now() - interval '7 days') AS recent,
				  (SELECT count(*) FROM playlists WHERE deleted_at IS NOT NULL) AS deleted
				""")
				.query((rs, n) -> new Overview(rs.getLong("users"), rs.getLong("verified"), rs.getLong("active"),
						rs.getLong("spotify"), rs.getLong("playlists"), rs.getLong("recent"), rs.getLong("deleted")))
				.single();
	}

	/** Quem acessou por último aparece primeiro; quem nunca voltou desde o registro do acesso vai para o fim. */
	List<AdminUser> users() {
		return jdbc.sql("""
				SELECT u.id, u.name, u.email, u.role, u.email_verified_at, u.created_at, u.last_seen_at,
				       EXISTS (SELECT 1 FROM spotify_accounts s WHERE s.user_id = u.id) AS spotify,
				       (SELECT count(*) FROM playlists p WHERE p.user_id = u.id) AS playlists
				FROM users u
				ORDER BY u.last_seen_at DESC NULLS LAST, u.created_at DESC
				""")
				.query((rs, n) -> new AdminUser(
						rs.getObject("id", UUID.class),
						rs.getString("name"),
						rs.getString("email"),
						"ADMIN".equals(rs.getString("role")),
						rs.getObject("email_verified_at") != null,
						rs.getBoolean("spotify"),
						instant(rs, "created_at"),
						instant(rs, "last_seen_at"),
						rs.getLong("playlists")))
				.list();
	}

	/** Todas as playlists, inclusive as apagadas pelo usuário; mais recentes primeiro. */
	List<AdminPlaylist> playlists(UUID ownerId) {
		String where = ownerId == null ? "" : "WHERE p.user_id = :owner ";
		var statement = jdbc.sql("SELECT " + PLAYLIST_COLUMNS + where + "ORDER BY p.created_at DESC");
		if (ownerId != null) {
			statement = statement.param("owner", ownerId);
		}
		return statement.query((rs, n) -> playlist(rs)).list();
	}

	Optional<AdminPlaylistDetail> playlist(UUID id) {
		return jdbc.sql("SELECT " + PLAYLIST_COLUMNS + "WHERE p.id = :id")
				.param("id", id)
				.query((rs, n) -> new AdminPlaylistDetail(
						playlist(rs),
						rs.getString("description"),
						rs.getString("user_notes"),
						rs.getString("failure_code"),
						rs.getString("spotify_playlist_url"),
						tracks(id)))
				.optional();
	}

	private List<AdminTrack> tracks(UUID playlistId) {
		return jdbc.sql("""
				SELECT position, name, artists, album, spotify_url, recommendation_reason, source
				FROM playlist_tracks WHERE playlist_id = :id ORDER BY position
				""")
				.param("id", playlistId)
				.query((rs, n) -> new AdminTrack(
						rs.getInt("position"),
						rs.getString("name"),
						strings(rs.getArray("artists")),
						rs.getString("album"),
						rs.getString("spotify_url"),
						rs.getString("recommendation_reason"),
						rs.getString("source")))
				.list();
	}

	private static AdminPlaylist playlist(ResultSet rs) throws SQLException {
		return new AdminPlaylist(
				rs.getObject("id", UUID.class),
				rs.getString("name"),
				rs.getString("mode"),
				rs.getString("status"),
				rs.getInt("tracks"),
				rs.getObject("spotify_saved_at") != null,
				instant(rs, "created_at"),
				instant(rs, "deleted_at"),
				rs.getObject("owner_id", UUID.class),
				rs.getString("owner_name"),
				rs.getString("owner_email"),
				rs.getString("book_title"),
				strings(rs.getArray("book_authors")));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static List<String> strings(Array array) throws SQLException {
		return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
	}
}
