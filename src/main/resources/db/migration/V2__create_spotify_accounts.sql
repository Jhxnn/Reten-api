-- Conexão OAuth com o Spotify (no máximo uma por usuário).
-- access_token e refresh_token são cifrados com AES-GCM pela aplicação antes de chegar aqui.

CREATE TABLE spotify_accounts (
    id                     UUID         PRIMARY KEY,
    user_id                UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    spotify_user_id        VARCHAR(128) NOT NULL,
    display_name           VARCHAR(200),
    access_token_encrypted TEXT         NOT NULL,
    refresh_token_encrypted TEXT        NOT NULL,
    scope                  VARCHAR(500) NOT NULL,
    expires_at             TIMESTAMPTZ  NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_spotify_accounts_user UNIQUE (user_id)
);
