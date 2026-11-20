-- Faixas verificadas no Spotify. Só entra aqui o que foi encontrado no catálogo real.

ALTER TABLE playlists
    ADD COLUMN verification_status VARCHAR(20),
    ADD COLUMN spotify_saved_at    TIMESTAMPTZ,
    ADD CONSTRAINT ck_playlists_verification
        CHECK (verification_status IS NULL OR verification_status IN ('VERIFIED', 'NOT_CONFIGURED', 'FAILED'));

-- Resultado da busca de cada sugestão da IA.
ALTER TABLE playlist_recommendations
    ADD COLUMN match_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT ck_recommendations_match
        CHECK (match_status IN ('PENDING', 'MATCHED', 'NOT_FOUND', 'DUPLICATE'));

CREATE TABLE playlist_tracks (
    id                    UUID          PRIMARY KEY,
    playlist_id           UUID          NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    recommendation_id     UUID          REFERENCES playlist_recommendations (id) ON DELETE SET NULL,
    position              INTEGER       NOT NULL,
    spotify_track_id      VARCHAR(64)   NOT NULL,
    name                  VARCHAR(500)  NOT NULL,
    artists               TEXT[]        NOT NULL DEFAULT '{}',
    album                 VARCHAR(500),
    album_image_url       VARCHAR(500),
    duration_ms           INTEGER,
    spotify_url           VARCHAR(300)  NOT NULL,
    recommendation_reason VARCHAR(500),
    source                VARCHAR(20)   NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_playlist_tracks_track UNIQUE (playlist_id, spotify_track_id),
    CONSTRAINT ck_playlist_tracks_source CHECK (source IN ('AI', 'MANUAL'))
);

CREATE INDEX ix_playlist_tracks_playlist ON playlist_tracks (playlist_id, position);
