package com.Reaten.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	private UUID id;

	@Column(nullable = false, length = 80)
	private String name;

	/** Sempre normalizado (trim + minúsculas). */
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Nulo até o leitor digitar o código enviado para {@link #email}. */
	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	protected User() {
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	public void markEmailVerified(Instant now) {
		if (emailVerifiedAt == null) {
			emailVerifiedAt = now;
		}
	}

	/** Troca o e-mail (já normalizado); o novo endereço precisa ser confirmado de novo. */
	public void changeEmail(String normalizedEmail) {
		this.email = normalizedEmail;
		this.emailVerifiedAt = null;
	}

	public User(String name, String email, String passwordHash) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = Instant.now();
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
