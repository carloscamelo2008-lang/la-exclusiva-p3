BEGIN;

ALTER TABLE seller ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE cash_movement
    ADD COLUMN user_id BIGINT REFERENCES user_account(id),
    ADD COLUMN payment_method VARCHAR(20),
    ADD CONSTRAINT ck_cash_payment_method CHECK (payment_method IN
        ('EFECTIVO', 'TRANSFERENCIA', 'TARJETA', 'OTRO'));

ALTER TABLE sale_payment ADD COLUMN received_by_user_id BIGINT REFERENCES user_account(id);
ALTER TABLE customer_general_debt_movement ADD COLUMN received_by_user_id BIGINT REFERENCES user_account(id);
ALTER TABLE stock_movement ADD COLUMN actor_user_id BIGINT REFERENCES user_account(id);

COMMIT;
