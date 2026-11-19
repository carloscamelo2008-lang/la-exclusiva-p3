-- Libro de caja: los recaudos de ventas/cartera se insertan en sus transacciones de origen.
BEGIN;

CREATE TABLE cash_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    direction VARCHAR(8) NOT NULL,
    category VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    occurred_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    description TEXT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    CONSTRAINT uq_cash_movement_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_cash_movement_source UNIQUE (source_type, source_reference),
    CONSTRAINT ck_cash_movement_direction CHECK (direction IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_cash_movement_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_movement_description CHECK (btrim(description) <> ''),
    CONSTRAINT ck_cash_movement_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cash_movement_classification CHECK (
        (source_type = 'SALE_INITIAL' AND direction = 'INCOME' AND category = 'SALE_INITIAL_PAYMENT')
        OR (source_type = 'SALE_INSTALLMENT' AND direction = 'INCOME' AND category = 'SALE_INSTALLMENT')
        OR (source_type = 'GENERAL_DEBT_PAYMENT' AND direction = 'INCOME'
            AND category = 'GENERAL_DEBT_PAYMENT')
        OR (source_type = 'MANUAL' AND direction = 'INCOME' AND category = 'OTHER_INCOME')
        OR (source_type = 'MANUAL' AND direction = 'EXPENSE'
            AND category IN ('SERVICES', 'ICE', 'WORKER_PAYMENT', 'WITHDRAWAL', 'OTHER_EXPENSE'))
    )
);

CREATE INDEX idx_cash_movement_date_category
    ON cash_movement (occurred_at, category, id);

COMMIT;
