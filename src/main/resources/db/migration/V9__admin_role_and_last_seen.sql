-- Papel do usuário (a conta de administrador é criada pela aplicação a partir de RETEN_ADMIN_*)
-- e o último acesso, mostrado na área de administração.
ALTER TABLE users
    ADD COLUMN role         VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN last_seen_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));
