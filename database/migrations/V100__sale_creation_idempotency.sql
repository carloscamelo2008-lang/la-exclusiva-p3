-- Numeración reservada por feature/carlos-finance-close para evitar colisión con V001/V002.
-- La clave de solicitud y la venta se guardan en la misma transacción.
BEGIN;

ALTER TABLE sale
    ADD COLUMN idempotency_key VARCHAR(64),
    ADD COLUMN request_fingerprint CHAR(64);

ALTER TABLE sale
    ADD CONSTRAINT uq_sale_idempotency_key UNIQUE (idempotency_key),
    ADD CONSTRAINT ck_sale_request_identity CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL)
        OR (idempotency_key IS NOT NULL
            AND request_fingerprint IS NOT NULL
            AND request_fingerprint ~ '^[0-9a-f]{64}$')
    );

COMMIT;
