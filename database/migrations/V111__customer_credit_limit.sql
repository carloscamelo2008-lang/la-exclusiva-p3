BEGIN;

ALTER TABLE customer
    ADD COLUMN credit_limit NUMERIC(19,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_customer_credit_limit CHECK (credit_limit >= 0 AND credit_limit <> 'NaN'::numeric);

-- Conserva las claves históricas y sus FK; impide altas equivalentes por espacios.
CREATE UNIQUE INDEX uq_customer_id_normalized
    ON customer (btrim(regexp_replace(id, '[[:space:]]+', ' ', 'g')));

COMMIT;
