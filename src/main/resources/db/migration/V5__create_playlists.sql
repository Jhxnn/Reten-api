-- Playlists geradas a partir de um livro.
-- A geração é assíncrona: a linha nasce como GENERATING e termina em READY ou FAILED.
-- Toda tentativa conta para o limite diário do usuário (cada uma custa uma chamada à IA).

CREATE TABLE playlists (
    id                   UUID          PRIMARY KEY,
    user_id              UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    book_id              UUID          NOT NULL REFERENCES books (id),
    mode                 VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    user_notes           VARCHAR(500),
    name                 VARCHAR(120),
    description          VARCHAR(1000),
    musical_style        VARCHAR(200),
    -- Atmosfera identificada pela IA
    literary_genre       VARCHAR(200),
    moods                TEXT[]        NOT NULL DEFAULT '{}',
    setting              VARCHAR(500),
    era                  VARCHAR(200),
    themes               TEXT[]        NOT NULL DEFAULT '{}',
    emotional_intensity  VARCHAR(10),
    universe             VARCHAR(1000),
    ai_model             VARCHAR(80),
    failure_code         VARCHAR(60),
    spotify_playlist_id  VARCHAR(100),
    spotify_playlist_url VARCHAR(500),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_playlists_mode CHECK (mode IN ('THEMATIC', 'VOCAL', 'FOCUS')),
    CONSTRAINT ck_playlists_status CHECK (status IN ('GENERATING', 'READY', 'FAILED')),
    CONSTRAINT ck_playlists_intensity CHECK (emotional_intensity IS NULL OR emotional_intensity IN ('BAIXA', 'MEDIA', 'ALTA'))
);

CREATE INDEX ix_playlists_user_created ON playlists (user_id, created_at DESC);
CREATE INDEX ix_playlists_book ON playlists (book_id);

-- Sugestões da IA. NÃO são faixas verificadas: a verificação no Spotify (Fase 5) cria playlist_tracks.
CREATE TABLE playlist_recommendations (
    id           UUID          PRIMARY KEY,
    playlist_id  UUID          NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    position     INTEGER       NOT NULL,
    title        VARCHAR(300)  NOT NULL,
    artist       VARCHAR(300)  NOT NULL,
    reason       VARCHAR(500)  NOT NULL,
    search_query VARCHAR(300)  NOT NULL,
    CONSTRAINT uk_playlist_recommendations_position UNIQUE (playlist_id, position)
);
