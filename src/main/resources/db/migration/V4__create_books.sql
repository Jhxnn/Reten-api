-- Livros escolhidos por usuários (os resultados de busca não são guardados).
-- source + external_id identificam o livro no catálogo de origem (Open Library ou Google Books).
-- published_date guarda a data como o catálogo informa: '2007', '2007-03' ou '2007-03-27'.

CREATE TABLE books (
    id             UUID          PRIMARY KEY,
    source         VARCHAR(20)   NOT NULL,
    external_id    VARCHAR(100)  NOT NULL,
    title          VARCHAR(500)  NOT NULL,
    subtitle       VARCHAR(500),
    authors        TEXT[]        NOT NULL DEFAULT '{}',
    description    TEXT,
    categories     TEXT[]        NOT NULL DEFAULT '{}',
    published_date VARCHAR(10),
    isbn13         VARCHAR(13),
    isbn10         VARCHAR(10),
    cover_url      VARCHAR(1000),
    page_count     INTEGER,
    language       VARCHAR(10),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_books_source_external UNIQUE (source, external_id)
);

CREATE INDEX ix_books_isbn13 ON books (isbn13);

-- Histórico de livros escolhidos por cada usuário ("usados recentemente").
CREATE TABLE user_books (
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    book_id           UUID        NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    first_selected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_selected_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, book_id)
);

CREATE INDEX ix_user_books_recent ON user_books (user_id, last_selected_at DESC);
