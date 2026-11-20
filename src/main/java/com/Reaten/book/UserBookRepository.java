package com.Reaten.book;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserBookRepository extends JpaRepository<UserBook, UserBook.Key> {

	/** Registra a escolha ou atualiza a data da última escolha, de forma atômica. */
	@Modifying
	@Query(value = """
			insert into user_books (user_id, book_id, first_selected_at, last_selected_at)
			values (:userId, :bookId, :now, :now)
			on conflict (user_id, book_id) do update set last_selected_at = excluded.last_selected_at
			""", nativeQuery = true)
	void recordSelection(UUID userId, UUID bookId, Instant now);

	@Query("""
			select ub from UserBook ub join fetch ub.book
			where ub.key.userId = :userId
			order by ub.lastSelectedAt desc
			""")
	List<UserBook> findRecent(UUID userId, Limit limit);
}
