-- La Exclusiva P3 - esquema inicial PostgreSQL
-- Ejecutar una sola vez sobre una base de datos vacía.
-- Importes: NUMERIC(19,2). Fechas locales: TIMESTAMP(6) WITHOUT TIME ZONE.
-- Todas las claves foráneas restringen el borrado físico.

BEGIN;

CREATE TABLE beer_product (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    volume_ml INTEGER NOT NULL,
    units_per_basket INTEGER NOT NULL,
    container_type VARCHAR(32) NOT NULL,
    container_group VARCHAR(100),
    CONSTRAINT ck_beer_product_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_beer_product_volume CHECK (volume_ml > 0),
    CONSTRAINT ck_beer_product_basket CHECK (units_per_basket > 0 AND units_per_basket % 2 = 0),
    CONSTRAINT ck_beer_product_container_type
        CHECK (container_type IN ('RETURNABLE_EXCLUSIVE', 'RETURNABLE_SHARED', 'NON_RETURNABLE_CAN')),
    CONSTRAINT ck_beer_product_container_group CHECK (
        (container_type = 'RETURNABLE_SHARED'
            AND container_group IS NOT NULL AND btrim(container_group) <> '')
        OR
        (container_type <> 'RETURNABLE_SHARED' AND container_group IS NULL)
    )
);

CREATE TABLE product_price (
    product_id VARCHAR(64) NOT NULL,
    presentation_code VARCHAR(32) NOT NULL,
    price NUMERIC(19,2) NOT NULL,
    CONSTRAINT pk_product_price PRIMARY KEY (product_id, presentation_code),
    CONSTRAINT fk_product_price_product FOREIGN KEY (product_id)
        REFERENCES beer_product (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_price_presentation
        CHECK (presentation_code IN ('UNIT', 'HALF_BASKET', 'FULL_BASKET')),
    CONSTRAINT ck_product_price_positive CHECK (price > 0)
);

CREATE TABLE inventory_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL UNIQUE,
    total_units INTEGER NOT NULL,
    CONSTRAINT fk_inventory_item_product FOREIGN KEY (product_id)
        REFERENCES beer_product (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_item_total CHECK (total_units >= 0)
);

CREATE TABLE stock_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    units INTEGER NOT NULL,
    reason TEXT NOT NULL,
    timestamp TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id)
        REFERENCES beer_product (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_movement_type CHECK (type IN ('ENTRY', 'EXIT')),
    CONSTRAINT ck_stock_movement_units CHECK (units > 0),
    CONSTRAINT ck_stock_movement_reason CHECK (btrim(reason) <> '')
);

CREATE TABLE customer (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    address TEXT NOT NULL,
    balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_customer_phone CHECK (btrim(phone) <> ''),
    CONSTRAINT ck_customer_address CHECK (btrim(address) <> ''),
    CONSTRAINT ck_customer_balance CHECK (balance >= 0)
);

CREATE TABLE seller (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    address TEXT NOT NULL,
    CONSTRAINT ck_seller_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_seller_phone CHECK (btrim(phone) <> ''),
    CONSTRAINT ck_seller_address CHECK (btrim(address) <> '')
);

CREATE TABLE sale (
    id VARCHAR(48) PRIMARY KEY,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    seller_id VARCHAR(32) NOT NULL,
    customer_id VARCHAR(64),
    CONSTRAINT fk_sale_seller FOREIGN KEY (seller_id)
        REFERENCES seller (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_customer FOREIGN KEY (customer_id)
        REFERENCES customer (id) ON DELETE RESTRICT
);

CREATE TABLE sale_line (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id VARCHAR(48) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    presentation_code VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    units_per_basket_snapshot INTEGER NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    CONSTRAINT fk_sale_line_sale FOREIGN KEY (sale_id)
        REFERENCES sale (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_line_product FOREIGN KEY (product_id)
        REFERENCES beer_product (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sale_line_presentation
        CHECK (presentation_code IN ('UNIT', 'HALF_BASKET', 'FULL_BASKET')),
    CONSTRAINT ck_sale_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sale_line_basket_snapshot
        CHECK (units_per_basket_snapshot > 0 AND units_per_basket_snapshot % 2 = 0),
    CONSTRAINT ck_sale_line_unit_price CHECK (unit_price > 0)
);

CREATE TABLE sale_payment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id VARCHAR(48) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    timestamp TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_sale_payment_sale FOREIGN KEY (sale_id)
        REFERENCES sale (id) ON DELETE RESTRICT,
    CONSTRAINT ck_sale_payment_amount CHECK (amount > 0)
);

CREATE TABLE user_account (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    seller_id VARCHAR(32),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_user_account_seller FOREIGN KEY (seller_id)
        REFERENCES seller (id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_account_username CHECK (btrim(username) <> ''),
    CONSTRAINT ck_user_account_password_hash CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_user_account_role CHECK (role IN ('ADMINISTRATOR', 'WORKER'))
);

CREATE UNIQUE INDEX uq_user_account_username_normalized
    ON user_account (lower(btrim(username)));

CREATE INDEX idx_stock_movement_product_timestamp
    ON stock_movement (product_id, timestamp);
CREATE INDEX idx_sale_line_sale_id ON sale_line (sale_id);
CREATE INDEX idx_sale_line_product_id ON sale_line (product_id);
CREATE INDEX idx_sale_payment_sale_id ON sale_payment (sale_id);
CREATE INDEX idx_sale_seller_id ON sale (seller_id);
CREATE INDEX idx_sale_customer_created_at ON sale (customer_id, created_at);
CREATE INDEX idx_sale_created_at ON sale (created_at);
CREATE INDEX idx_user_account_seller_id ON user_account (seller_id);

COMMIT;
