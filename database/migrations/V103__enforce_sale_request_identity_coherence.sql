-- Refuerza el CHECK de V100 en bases donde esa versión ya fue aplicada.
-- No transforma filas: si hay identidades históricas incompletas, la validación falla de forma segura.
BEGIN;

ALTER TABLE sale
    DROP CONSTRAINT ck_sale_request_identity;

ALTER TABLE sale
    ADD CONSTRAINT ck_sale_request_identity CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL)
        OR (idempotency_key IS NOT NULL
            AND request_fingerprint IS NOT NULL
            AND request_fingerprint ~ '^[0-9a-f]{64}$')
    ) NOT VALID;

ALTER TABLE sale
    VALIDATE CONSTRAINT ck_sale_request_identity;

COMMIT;
