package com.Reaten.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String normalizedEmail);

	boolean existsByEmail(String normalizedEmail);

	boolean existsByIdAndEmailVerifiedAtIsNotNull(UUID id);

	/** Registra o acesso sem carregar a entidade (chamado durante requisições comuns). */
	@Transactional
	@Modifying
	@Query("update User u set u.lastSeenAt = :now where u.id = :id")
	void touchLastSeen(@Param("id") UUID id, @Param("now") Instant now);
}
