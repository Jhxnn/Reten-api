package com.Reaten.auth.emailverification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verification_codes")
class EmailVerificationCode {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false, length = 254)
	private String email;

	@Column(name = "code_hash", nullable = false, updatable = false, length = 64)
	private String codeHash;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EmailVerificationCode() {
	}

	EmailVerificationCode(UUID userId, String email, String codeHash, Instant expiresAt, Instant now) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.email = email;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	boolean isUsable(Instant now, int maxAttempts) {
		return usedAt == null && attempts < maxAttempts && now.isBefore(expiresAt);
	}

	/** Conta uma tentativa errada; ao atingir o máximo, o código deixa de valer. */
	int registerFailedAttempt(Instant now, int maxAttempts) {
		attempts++;
		if (attempts >= maxAttempts) {
			usedAt = now;
		}
		return Math.max(0, maxAttempts - attempts);
	}

	void markUsed(Instant now) {
		this.usedAt = now;
	}

	String getEmail() {
		return email;
	}

	String getCodeHash() {
		return codeHash;
	}

	Instant getCreatedAt() {
		return createdAt;
	}
}
