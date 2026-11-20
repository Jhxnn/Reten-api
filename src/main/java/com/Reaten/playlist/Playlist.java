package com.Reaten.playlist;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.Reaten.ai.ListeningMode;
import com.Reaten.ai.RecommendationResult;
import com.Reaten.book.Book;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "playlists")
public class Playlist {

	public enum Status {
		GENERATING, READY, FAILED
	}

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_id", nullable = false, updatable = false)
	private Book book;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private ListeningMode mode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(name = "user_notes", length = 500, updatable = false)
	private String userNotes;

	@Column(length = 120)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(name = "musical_style", length = 200)
	private String musicalStyle;

	@Column(name = "literary_genre", length = 200)
	private String literaryGenre;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> moods = new ArrayList<>();

	@Column(length = 500)
	private String setting;

	@Column(length = 200)
	private String era;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> themes = new ArrayList<>();

	@Column(name = "emotional_intensity", length = 10)
	private String emotionalIntensity;

	@Column(length = 1000)
	private String universe;

	@Column(name = "ai_model", length = 80)
	private String aiModel;

	@Column(name = "failure_code", length = 60)
	private String failureCode;

	@Column(name = "spotify_playlist_id", length = 100)
	private String spotifyPlaylistId;

	@Column(name = "spotify_playlist_url", length = 500)
	private String spotifyPlaylistUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position asc")
	private List<PlaylistRecommendation> recommendations = new ArrayList<>();

	protected Playlist() {
	}

	static Playlist pending(UUID userId, Book book, ListeningMode mode, String userNotes) {
		Playlist playlist = new Playlist();
		playlist.id = UUID.randomUUID();
		playlist.userId = userId;
		playlist.book = book;
		playlist.mode = mode;
		playlist.userNotes = userNotes;
		playlist.status = Status.GENERATING;
		playlist.createdAt = Instant.now();
		playlist.updatedAt = playlist.createdAt;
		return playlist;
	}

	void complete(RecommendationResult result, String model) {
		this.name = result.playlistName();
		this.description = result.description();
		this.musicalStyle = result.musicalStyle();
		RecommendationResult.Atmosphere atmosphere = result.atmosphere();
		this.literaryGenre = atmosphere.literaryGenre();
		this.moods = new ArrayList<>(atmosphere.moods());
		this.setting = atmosphere.setting();
		this.era = atmosphere.era();
		this.themes = new ArrayList<>(atmosphere.themes());
		this.emotionalIntensity = atmosphere.emotionalIntensity();
		this.universe = atmosphere.universe();
		this.aiModel = model;
		this.recommendations.clear();
		int position = 1;
		for (RecommendationResult.Recommendation item : result.recommendations()) {
			this.recommendations.add(new PlaylistRecommendation(this, position++, item));
		}
		this.status = Status.READY;
		this.failureCode = null;
		this.updatedAt = Instant.now();
	}

	void fail(String code) {
		this.status = Status.FAILED;
		this.failureCode = code;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Book getBook() {
		return book;
	}

	public ListeningMode getMode() {
		return mode;
	}

	public Status getStatus() {
		return status;
	}

	public String getUserNotes() {
		return userNotes;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getMusicalStyle() {
		return musicalStyle;
	}

	public String getLiteraryGenre() {
		return literaryGenre;
	}

	public List<String> getMoods() {
		return moods;
	}

	public String getSetting() {
		return setting;
	}

	public String getEra() {
		return era;
	}

	public List<String> getThemes() {
		return themes;
	}

	public String getEmotionalIntensity() {
		return emotionalIntensity;
	}

	public String getUniverse() {
		return universe;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getSpotifyPlaylistUrl() {
		return spotifyPlaylistUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<PlaylistRecommendation> getRecommendations() {
		return recommendations;
	}
}
