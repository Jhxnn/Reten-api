package com.Reaten.playlist;

import java.util.UUID;

import com.Reaten.ai.RecommendationResult.Recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Sugestão da IA. Não é uma faixa verificada — a confirmação no Spotify acontece na etapa seguinte. */
@Entity
@Table(name = "playlist_recommendations")
public class PlaylistRecommendation {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "playlist_id", nullable = false, updatable = false)
	private Playlist playlist;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(nullable = false, length = 300)
	private String artist;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "search_query", nullable = false, length = 300)
	private String searchQuery;

	protected PlaylistRecommendation() {
	}

	PlaylistRecommendation(Playlist playlist, int position, Recommendation source) {
		this.id = UUID.randomUUID();
		this.playlist = playlist;
		this.position = position;
		this.title = source.title();
		this.artist = source.artist();
		this.reason = source.reason();
		this.searchQuery = source.searchQuery();
	}

	public UUID getId() {
		return id;
	}

	public int getPosition() {
		return position;
	}

	public String getTitle() {
		return title;
	}

	public String getArtist() {
		return artist;
	}

	public String getReason() {
		return reason;
	}

	public String getSearchQuery() {
		return searchQuery;
	}
}
