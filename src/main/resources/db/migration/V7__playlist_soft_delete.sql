-- Playlists apagadas pelo usuário saem do Reten (listas, biblioteca, limite de 5),
-- mas a linha continua para o limite diário de gerações: apagar e gerar de novo não o contorna.
ALTER TABLE playlists ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX ix_playlists_user_active ON playlists (user_id) WHERE deleted_at IS NULL;
