-- Auditoría mínima de seguridad: actor validado, acción, objetivo y resultado cerrado.
BEGIN;

CREATE TABLE administrative_audit_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_reference VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    result_code VARCHAR(16) NOT NULL,
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_admin_audit_action CHECK (action IN
        ('USER_CREATED', 'ROLE_CHANGED', 'ACCOUNT_ACTIVATED', 'ACCOUNT_DEACTIVATED',
         'PASSWORD_RESET', 'SELLER_ASSIGNMENT_CHANGED', 'STATEMENT_SEND_ATTEMPT')),
    CONSTRAINT ck_admin_audit_target CHECK (target_type IN ('USER', 'CUSTOMER')),
    CONSTRAINT ck_admin_audit_result CHECK (result_code IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_admin_audit_target_reference CHECK
        (btrim(target_reference) <> '' AND length(target_reference) <= 128)
);

CREATE INDEX idx_admin_audit_occurred_at ON administrative_audit_event (occurred_at DESC);
CREATE INDEX idx_admin_audit_actor ON administrative_audit_event (actor_user_id, occurred_at DESC);

COMMIT;
