package com.Reaten.book;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, UUID> {

	Optional<Book> findBySourceAndExternalId(String source, String externalId);
}
