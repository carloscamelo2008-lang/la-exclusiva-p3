BEGIN;

ALTER TABLE sale_payment
    ADD COLUMN idempotency_key VARCHAR(64);

ALTER TABLE sale_payment
    ADD CONSTRAINT uq_sale_payment_sale_idempotency_key
    UNIQUE (sale_id, idempotency_key);

COMMIT;
