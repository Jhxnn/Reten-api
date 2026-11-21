-- Confirmação de e-mail por código de 6 dígitos.

ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Contas criadas antes desta mudança continuam liberadas.
UPDATE users SET email_verified_at = created_at;

-- Só o hash do código é guardado. "email" registra para qual endereço o código foi enviado,
-- para que um código antigo não confirme um e-mail trocado depois.
CREATE TABLE email_verification_codes (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email      VARCHAR(254) NOT NULL,
    code_hash  VARCHAR(64)  NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_email_verification_codes_user ON email_verification_codes (user_id, created_at DESC);
