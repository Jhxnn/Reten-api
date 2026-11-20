package com.Reaten.auth.passwordreset;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** Invalida tokens pendentes: só o link mais recente vale. */
	@Modifying
	@Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
	int invalidatePending(UUID userId, Instant now);
}
