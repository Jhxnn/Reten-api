package com.Reaten.playlist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.Reaten.music.CatalogTrack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Faixa verificada no catálogo real. Os dados vêm sempre do Spotify, nunca do cliente. */
@Entity
@Table(name = "playlist_tracks")
public class PlaylistTrack {

	public enum Source {
		/** Encontrada a partir de uma sugestão da IA. */
		AI,
		/** Adicionada pelo usuário (substituição). */
		MANUAL
	}

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "playlist_id", nullable = false, updatable = false)
	private Playlist playlist;

	@Column(name = "recommendation_id", updatable = false)
	private UUID recommendationId;

	@Column(nullable = false)
	private int position;

	@Column(name = "spotify_track_id", nullable = false, length = 64, updatable = false)
	private String spotifyTrackId;

	@Column(nullable = false, length = 500)
	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> artists;

	@Column(length = 500)
	private String album;

	@Column(name = "album_image_url", length = 500)
	private String albumImageUrl;

	@Column(name = "duration_ms")
	private Integer durationMs;

	@Column(name = "spotify_url", nullable = false, length = 300)
	private String spotifyUrl;

	@Column(name = "recommendation_reason", length = 500)
	private String recommendationReason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private Source source;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PlaylistTrack() {
	}

	PlaylistTrack(Playlist playlist, int position, CatalogTrack track, UUID recommendationId, String reason, Source source) {
		this.id = UUID.randomUUID();
		this.playlist = playlist;
		this.position = position;
		this.spotifyTrackId = track.id();
		this.name = truncate(track.name(), 500);
		this.artists = track.artists();
		this.album = truncate(track.album(), 500);
		this.albumImageUrl = truncate(track.imageUrl(), 500);
		this.durationMs = track.durationMs();
		this.spotifyUrl = track.url();
		this.recommendationId = recommendationId;
		this.recommendationReason = truncate(reason, 500);
		this.source = source;
		this.createdAt = Instant.now();
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	void moveTo(int newPosition) {
		this.position = newPosition;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRecommendationId() {
		return recommendationId;
	}

	public int getPosition() {
		return position;
	}

	public String getSpotifyTrackId() {
		return spotifyTrackId;
	}

	public String getName() {
		return name;
	}

	public List<String> getArtists() {
		return artists;
	}

	public String getAlbum() {
		return album;
	}

	public String getAlbumImageUrl() {
		return albumImageUrl;
	}

	public Integer getDurationMs() {
		return durationMs;
	}

	public String getSpotifyUrl() {
		return spotifyUrl;
	}

	public String getRecommendationReason() {
		return recommendationReason;
	}

	public Source getSource() {
		return source;
	}
}
