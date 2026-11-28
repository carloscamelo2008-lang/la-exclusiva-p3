-- Referencia opcional a una imagen local portable; los archivos no se guardan en PostgreSQL.
ALTER TABLE beer_product
    ADD COLUMN image_filename VARCHAR(68);

ALTER TABLE beer_product
    ADD CONSTRAINT ck_beer_product_image_filename
    CHECK (image_filename IS NULL OR image_filename ~ '^[0-9a-f]{64}[.](png|jpg)$');

COMMENT ON COLUMN beer_product.image_filename IS
    'Nombre SHA-256 opcional de una imagen PNG/JPG en el almacén local de fotografías.';
