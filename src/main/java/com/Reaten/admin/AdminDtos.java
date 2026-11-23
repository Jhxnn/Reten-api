package com.Reaten.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas da área de administração (só leitura). */
public final class AdminDtos {

	private AdminDtos() {
	}

	/** Números do topo da página. "Ativos" = acessaram nos últimos 7 dias. */
	public record Overview(
			long users,
			long verifiedUsers,
			long activeLast7Days,
			long spotifyConnected,
			long playlists,
			long playlistsLast7Days,
			long deletedPlaylists) {
	}

	public record AdminUser(
			UUID id,
			String name,
			String email,
			boolean admin,
			boolean emailVerified,
			boolean spotifyConnected,
			Instant createdAt,
			Instant lastSeenAt,
			long playlists) {
	}

	public record AdminPlaylist(
			UUID id,
			String name,
			String mode,
			String status,
			int tracks,
			boolean savedOnSpotify,
			Instant createdAt,
			Instant deletedAt,
			UUID ownerId,
			String ownerName,
			String ownerEmail,
			String bookTitle,
			List<String> bookAuthors) {
	}

	public record AdminTrack(
			int position,
			String name,
			List<String> artists,
			String album,
			String spotifyUrl,
			String reason,
			String source) {
	}

	public record AdminPlaylistDetail(
			AdminPlaylist playlist,
			String description,
			String userNotes,
			String failureCode,
			String spotifyPlaylistUrl,
			List<AdminTrack> tracks) {
	}
}
