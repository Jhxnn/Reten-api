package com.Reaten.playlist;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

	@EntityGraph(attributePaths = { "book", "recommendations" })
	Optional<Playlist> findWithDetailsByIdAndUserId(UUID id, UUID userId);

	@EntityGraph(attributePaths = { "book" })
	Optional<Playlist> findWithBookById(UUID id);

	/**
	 * Tentativas de geração do usuário na janela do limite diário — inclusive as apagadas (SQL nativo
	 * ignora a restrição de exclusão lógica), para que apagar e gerar de novo não contorne o limite.
	 */
	@Query(value = "select count(*) from playlists where user_id = :userId and created_at > :since", nativeQuery = true)
	long countAttemptsSince(UUID userId, Instant since);

	@Query(value = "select min(created_at) from playlists where user_id = :userId and created_at > :since", nativeQuery = true)
	Optional<Instant> oldestAttemptSince(UUID userId, Instant since);

	/** Playlists que ocupam vaga: não apagadas e que não falharam. */
	@Query("""
			select count(p) from Playlist p
			where p.userId = :userId and p.status <> com.Reaten.playlist.Playlist.Status.FAILED
			""")
	long countOccupyingSlots(UUID userId);

	/**
	 * Serializa pedidos de geração do mesmo usuário até o fim da transação, para que duas requisições
	 * simultâneas não ultrapassem o limite diário.
	 */
	@Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:userId))) as lock", nativeQuery = true)
	Integer lockUserForGeneration(String userId);

	/** Linha da listagem: a playlist (com o livro) e quantas faixas verificadas ela tem. */
	interface SummaryRow {
		Playlist getPlaylist();

		long getTrackCount();
	}

	/** Playlists do usuário, mais recentes primeiro; {@code bookId} nulo traz todas. */
	@Query("""
			select p as playlist, (select count(t) from PlaylistTrack t where t.playlist = p) as trackCount
			from Playlist p join fetch p.book
			where p.userId = :userId and (:bookId is null or p.book.id = :bookId)
			order by p.createdAt desc
			""")
	java.util.List<SummaryRow> findSummaries(UUID userId, UUID bookId, org.springframework.data.domain.Limit limit);

	/** Gerações que nunca terminaram (ex.: reinício do servidor no meio) viram falha. */
	@Modifying
	@Query("""
			update Playlist p set p.status = com.Reaten.playlist.Playlist.Status.FAILED,
			p.failureCode = 'generation_timeout', p.updatedAt = :now
			where p.status = com.Reaten.playlist.Playlist.Status.GENERATING and p.createdAt < :olderThan
			""")
	int failStale(Instant olderThan, Instant now);
}
