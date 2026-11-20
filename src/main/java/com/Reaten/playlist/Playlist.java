package com.Reaten.playlist;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
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
// Playlists apagadas ficam invisíveis para toda consulta JPA; só o limite diário as conta (SQL nativo).
@SQLRestriction("deleted_at IS NULL")
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

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_status", length = 20)
	private VerificationStatus verificationStatus;

	@Column(name = "spotify_saved_at")
	private Instant spotifySavedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position asc")
	private List<PlaylistRecommendation> recommendations = new ArrayList<>();

	@OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position asc")
	private List<PlaylistTrack> tracks = new ArrayList<>();

	public enum VerificationStatus {
		/** Sugestões buscadas no catálogo; só as encontradas viraram faixas. */
		VERIFIED,
		/** Catálogo musical não configurado neste ambiente. */
		NOT_CONFIGURED,
		/** O catálogo falhou durante a verificação; pode ser repetida. */
		FAILED
	}

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

	/** Grava a interpretação e as sugestões da IA. A playlist continua GENERATING até a verificação. */
	void applyRecommendations(RecommendationResult result, String model) {
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
		this.failureCode = null;
		this.updatedAt = Instant.now();
	}

	/**
	 * Conclui a geração com o resultado da verificação no catálogo.
	 * Só faixas encontradas entram em {@code tracks}; as demais sugestões ficam marcadas como não encontradas.
	 */
	void finishVerification(VerificationStatus verification, List<TrackVerifier.Outcome> outcomes) {
		if (!tracks.isEmpty()) {
			// As faixas antigas precisam ser removidas e gravadas antes (ver clearTracksForReverification):
			// o Hibernate insere antes de apagar, e a mesma faixa voltando violaria (playlist, spotify_track_id).
			throw new IllegalStateException("Limpe as faixas e grave a remoção antes de reverificar");
		}
		int position = 1;
		for (TrackVerifier.Outcome outcome : outcomes) {
			PlaylistRecommendation recommendation = recommendations.stream()
					.filter(r -> r.getId().equals(outcome.recommendationId())).findFirst().orElse(null);
			if (recommendation == null) {
				continue;
			}
			recommendation.markMatch(outcome.status());
			if (outcome.status() == PlaylistRecommendation.MatchStatus.MATCHED) {
				tracks.add(new PlaylistTrack(this, position++, outcome.track(), recommendation.getId(),
						recommendation.getReason(), PlaylistTrack.Source.AI));
			}
		}
		this.verificationStatus = verification;
		this.status = Status.READY;
		this.updatedAt = Instant.now();
	}

	/** Primeira etapa da reverificação: descarta as faixas atuais (o chamador faz o flush antes de inserir as novas). */
	void clearTracksForReverification() {
		this.tracks.clear();
	}

	/** Verificação impossível (catálogo não configurado ou fora do ar): as sugestões seguem não verificadas. */
	void finishWithoutVerification(VerificationStatus verification) {
		this.verificationStatus = verification;
		this.status = Status.READY;
		this.updatedAt = Instant.now();
	}

	void rename(String newName, String newDescription) {
		this.name = newName;
		this.description = newDescription;
		this.updatedAt = Instant.now();
	}

	boolean containsTrack(String spotifyTrackId) {
		return tracks.stream().anyMatch(t -> t.getSpotifyTrackId().equals(spotifyTrackId));
	}

	void addTrack(com.Reaten.music.CatalogTrack track) {
		tracks.add(new PlaylistTrack(this, tracks.size() + 1, track, null, null, PlaylistTrack.Source.MANUAL));
		this.updatedAt = Instant.now();
	}

	boolean removeTrack(UUID trackId) {
		boolean removed = tracks.removeIf(t -> t.getId().equals(trackId));
		if (removed) {
			int position = 1;
			for (PlaylistTrack track : tracks) {
				track.moveTo(position++);
			}
			this.updatedAt = Instant.now();
		}
		return removed;
	}

	void markPublished(String spotifyId, String spotifyUrl) {
		this.spotifyPlaylistId = spotifyId;
		this.spotifyPlaylistUrl = spotifyUrl;
		Instant now = Instant.now();
		this.spotifySavedAt = now;
		this.updatedAt = now;
	}

	/** Apaga do Reten (exclusão lógica). A cópia salva no Spotify, se houver, não é tocada. */
	void markDeleted() {
		Instant now = Instant.now();
		this.deletedAt = now;
		this.updatedAt = now;
	}

	/** Houve edição depois do último envio ao Spotify. */
	boolean hasUnpublishedChanges() {
		return spotifySavedAt != null && updatedAt.isAfter(spotifySavedAt);
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

	public List<PlaylistTrack> getTracks() {
		return tracks;
	}

	public VerificationStatus getVerificationStatus() {
		return verificationStatus;
	}

	public String getSpotifyPlaylistId() {
		return spotifyPlaylistId;
	}

	public Instant getSpotifySavedAt() {
		return spotifySavedAt;
	}
}
