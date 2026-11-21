package com.Reaten.auth.emailverification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, UUID> {

	/** O código mais recente da conta (usado ou não): vale para conferir e para a espera entre envios. */
	Optional<EmailVerificationCode> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

	long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

	/** Só o código mais recente vale. */
	@Modifying
	@Query("update EmailVerificationCode c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
	int invalidatePending(UUID userId, Instant now);
}
