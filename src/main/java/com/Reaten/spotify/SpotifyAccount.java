package com.Reaten.spotify;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Conexão OAuth de um usuário do Reten com sua conta Spotify. Tokens sempre cifrados. */
@Entity
@Table(name = "spotify_accounts")
public class SpotifyAccount {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, unique = true, updatable = false)
	private UUID userId;

	@Column(name = "spotify_user_id", nullable = false, length = 128)
	private String spotifyUserId;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "access_token_encrypted", nullable = false)
	private String accessTokenEncrypted;

	@Column(name = "refresh_token_encrypted", nullable = false)
	private String refreshTokenEncrypted;

	@Column(nullable = false, length = 500)
	private String scope;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SpotifyAccount() {
	}

	SpotifyAccount(UUID userId) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.createdAt = Instant.now();
	}

	void link(String spotifyUserId, String displayName, String accessTokenEncrypted, String refreshTokenEncrypted,
			String scope, Instant expiresAt) {
		this.spotifyUserId = spotifyUserId;
		this.displayName = displayName;
		updateTokens(accessTokenEncrypted, refreshTokenEncrypted, scope, expiresAt);
	}

	void updateTokens(String accessTokenEncrypted, String refreshTokenEncrypted, String scope, Instant expiresAt) {
		this.accessTokenEncrypted = accessTokenEncrypted;
		if (refreshTokenEncrypted != null) {
			this.refreshTokenEncrypted = refreshTokenEncrypted;
		}
		if (scope != null) {
			this.scope = scope;
		}
		this.expiresAt = expiresAt;
		this.updatedAt = Instant.now();
	}

	public UUID getUserId() {
		return userId;
	}

	public String getSpotifyUserId() {
		return spotifyUserId;
	}

	public String getDisplayName() {
		return displayName;
	}

	String getAccessTokenEncrypted() {
		return accessTokenEncrypted;
	}

	String getRefreshTokenEncrypted() {
		return refreshTokenEncrypted;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
