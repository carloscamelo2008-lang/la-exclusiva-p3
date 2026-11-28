BEGIN;

-- Conserva los valores autorizados históricamente, pero deja de tratarlos como regla de fiado.
ALTER TABLE customer RENAME COLUMN credit_limit TO legacy_credit_limit;
ALTER TABLE customer RENAME CONSTRAINT ck_customer_credit_limit TO ck_customer_legacy_credit_limit;
COMMENT ON COLUMN customer.legacy_credit_limit IS
    'Valor histórico conservado tras retirar el cupo como regla comercial; el fiado se registra por venta.';

COMMIT;
