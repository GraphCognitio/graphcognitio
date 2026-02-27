ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD CONSTRAINT ck_users_role_valid CHECK (role IN ('USER', 'MOD', 'ADMIN'));

CREATE INDEX idx_users_role ON users (role);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE INDEX idx_audit_log_actor_user_id ON audit_log (actor_user_id);
CREATE INDEX idx_audit_log_created_at_desc ON audit_log (created_at DESC);

DO $$
DECLARE
    admin_email TEXT := NULLIF('${bootstrap_admin_email}', '');
BEGIN
    IF admin_email IS NOT NULL THEN
        UPDATE users
        SET role = 'ADMIN'
        WHERE lower(email) = lower(admin_email);
    END IF;
END $$;
