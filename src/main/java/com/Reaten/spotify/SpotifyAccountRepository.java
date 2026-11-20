package com.Reaten.spotify;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface SpotifyAccountRepository extends JpaRepository<SpotifyAccount, UUID> {

	Optional<SpotifyAccount> findByUserId(UUID userId);

	/** Usado na renovação de token: impede duas renovações simultâneas para o mesmo usuário. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from SpotifyAccount a where a.userId = :userId")
	Optional<SpotifyAccount> findByUserIdForUpdate(UUID userId);

	void deleteByUserId(UUID userId);
}
