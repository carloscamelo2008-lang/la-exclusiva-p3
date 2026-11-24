-- Preferencias y seguimiento de cobranza. Los perfiles nuevos no cambian datos históricos.
BEGIN;

CREATE TABLE customer_collection_profile (
    customer_id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(254),
    authorized_channel VARCHAR(16) NOT NULL DEFAULT 'NONE',
    collection_consent BOOLEAN NOT NULL DEFAULT FALSE,
    consent_recorded_at TIMESTAMP(6) WITHOUT TIME ZONE,
    reminders_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    commitment_date DATE,
    observed_balance NUMERIC(19,2),
    observed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT fk_collection_profile_customer FOREIGN KEY (customer_id)
        REFERENCES customer (id) ON DELETE RESTRICT,
    CONSTRAINT ck_collection_profile_email CHECK (email IS NULL OR
        (btrim(email) <> '' AND length(email) <= 254 AND position('@' IN email) > 1)),
    CONSTRAINT ck_collection_profile_channel CHECK (authorized_channel IN ('NONE', 'EMAIL', 'PHONE')),
    CONSTRAINT ck_collection_profile_consent CHECK (
        (collection_consent = FALSE AND authorized_channel = 'NONE'
            AND consent_recorded_at IS NULL AND reminders_enabled = FALSE)
        OR
        (collection_consent = TRUE AND authorized_channel <> 'NONE'
            AND consent_recorded_at IS NOT NULL)
    ),
    CONSTRAINT ck_collection_profile_email_channel CHECK (
        (authorized_channel = 'EMAIL' AND email IS NOT NULL)
        OR (authorized_channel <> 'EMAIL' AND email IS NULL)
    ),
    CONSTRAINT ck_collection_profile_observation CHECK (
        (observed_balance IS NULL AND observed_at IS NULL)
        OR (observed_balance >= 0 AND observed_at IS NOT NULL)
    )
);

CREATE TABLE customer_collection_follow_up (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    commitment_date DATE,
    balance_snapshot NUMERIC(19,2),
    note VARCHAR(500),
    occurred_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_collection_follow_up_customer FOREIGN KEY (customer_id)
        REFERENCES customer (id) ON DELETE RESTRICT,
    CONSTRAINT fk_collection_follow_up_actor FOREIGN KEY (actor_user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_collection_follow_up_type CHECK (event_type IN
        ('COMMITMENT_SET', 'CONSENT_CHANGED', 'BALANCE_CHANGED', 'NOTE_ADDED',
         'REMINDER_PREPARED', 'REMINDER_SENT', 'REMINDER_FAILED')),
    CONSTRAINT ck_collection_follow_up_balance CHECK (balance_snapshot IS NULL OR balance_snapshot >= 0),
    CONSTRAINT ck_collection_follow_up_note CHECK (note IS NULL OR length(note) <= 500)
);

CREATE INDEX idx_collection_follow_up_customer_date
    ON customer_collection_follow_up (customer_id, occurred_at DESC);

CREATE TABLE customer_collection_reminder (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    week_start DATE NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    balance_snapshot NUMERIC(19,2) NOT NULL,
    generated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    last_attempt_at TIMESTAMP(6) WITHOUT TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    result_code VARCHAR(32) NOT NULL DEFAULT 'PREPARED',
    CONSTRAINT fk_collection_reminder_customer FOREIGN KEY (customer_id)
        REFERENCES customer (id) ON DELETE RESTRICT,
    CONSTRAINT uq_collection_reminder_customer_week UNIQUE (customer_id, week_start),
    CONSTRAINT ck_collection_reminder_monday CHECK (extract(isodow FROM week_start) = 1),
    CONSTRAINT ck_collection_reminder_channel CHECK (channel IN ('EMAIL', 'PHONE')),
    CONSTRAINT ck_collection_reminder_status CHECK (status IN ('PENDING_REVIEW', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_collection_reminder_balance CHECK (balance_snapshot > 0),
    CONSTRAINT ck_collection_reminder_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_collection_reminder_result CHECK (result_code IN
        ('PREPARED', 'SIMULATED', 'SENT', 'WINDOW_BLOCKED', 'CONSENT_MISSING', 'BALANCE_CHANGED',
         'PROVIDER_FAILED', 'PAYMENT_INSTRUCTIONS_UNVERIFIED', 'MANUAL_CONTACT_ONLY'))
);

CREATE INDEX idx_collection_reminder_review
    ON customer_collection_reminder (status, generated_at);

COMMIT;
