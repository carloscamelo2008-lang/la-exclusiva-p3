-- Las colisiones o formatos históricos inválidos requieren corrección explícita de los datos.
BEGIN;

UPDATE customer
SET phone = regexp_replace(regexp_replace(phone, '[[:space:]]', '', 'g'), '^\+', '')
WHERE regexp_replace(phone, '[[:space:]]', '', 'g') ~ '^[+]?[0-9]{1,50}$';

ALTER TABLE customer
    ADD CONSTRAINT ck_customer_phone_digits CHECK (phone ~ '^[0-9]{1,50}$');

CREATE UNIQUE INDEX uq_customer_phone ON customer (phone);

COMMIT;
