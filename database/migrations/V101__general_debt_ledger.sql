-- Libro auditable de deuda no asociada a ventas; no duplica ni sustituye customer.balance.
BEGIN;

CREATE TABLE customer_general_debt_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    movement_type VARCHAR(24) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    occurred_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    category VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    CONSTRAINT fk_general_debt_movement_customer FOREIGN KEY (customer_id)
        REFERENCES customer (id) ON DELETE RESTRICT,
    CONSTRAINT uq_general_debt_movement_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_general_debt_movement_type CHECK (movement_type IN ('DEBT_INCREASE', 'PAYMENT')),
    CONSTRAINT ck_general_debt_movement_amount CHECK (amount > 0),
    CONSTRAINT ck_general_debt_movement_category CHECK (btrim(category) <> ''),
    CONSTRAINT ck_general_debt_movement_description CHECK (btrim(description) <> ''),
    CONSTRAINT ck_general_debt_movement_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_general_debt_customer_date
    ON customer_general_debt_movement (customer_id, occurred_at, id);
CREATE INDEX idx_general_debt_category_date
    ON customer_general_debt_movement (category, occurred_at);

COMMIT;
