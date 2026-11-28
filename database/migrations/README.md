# Despliegue de V110–V114

Antes de aplicar V110, ejecuta esta consulta de solo lectura. Usa la misma normalización que la migración y devuelve los IDs que requieren revisión.

```sql
WITH normalized AS (
    SELECT id,
           regexp_replace(regexp_replace(phone, '[[:space:]]', '', 'g'), '^\+', '') AS normalized_phone,
           regexp_replace(phone, '[[:space:]]', '', 'g') ~ '^[+]?[0-9]{1,50}$' AS valid
    FROM customer
), duplicates AS (
    SELECT normalized_phone
    FROM normalized
    WHERE valid
    GROUP BY normalized_phone
    HAVING count(*) > 1
)
SELECT id, 'FORMATO_INVALIDO' AS issue
FROM normalized
WHERE valid IS NOT TRUE
UNION ALL
SELECT n.id, 'DUPLICADO' AS issue
FROM normalized n
JOIN duplicates d USING (normalized_phone)
WHERE n.valid
ORDER BY issue, id;
```

Confirma los teléfonos correctos con los responsables de los datos, corrige los registros afectados y vuelve a ejecutar la consulta. Aplica V110 cuando devuelva cero filas.

## Prevalidación de IDs antes de V111

Conserva un respaldo previo y respeta el orden estricto V110 → V111 → V112 → V113 → V114. Antes de aplicar V111, ejecuta esta consulta de solo lectura con la normalización de `uq_customer_id_normalized`.

```sql
SELECT btrim(regexp_replace(id, '[[:space:]]+', ' ', 'g')) AS normalized_id,
       count(*) AS conflict_count,
       array_agg(id ORDER BY id) AS historical_ids
FROM customer
GROUP BY btrim(regexp_replace(id, '[[:space:]]+', ' ', 'g'))
HAVING count(*) > 1
ORDER BY normalized_id;
```

Concilia manualmente los IDs de cada grupo y revisa sus referencias antes de ejecutar V111. Evita editar automáticamente IDs referenciados. Vuelve a ejecutar la consulta y aplica V111 cuando devuelva cero filas.

V111 fue la versión que incorporó límites de crédito. V113 archiva esa columna como `legacy_credit_limit` sin borrar sus valores. La aplicación deja de tratarla como autorización financiera; las ventas a crédito conservan su saldo individual en cartera. No asignes cupos nuevos ni alteres los valores archivados como parte de la operación normal.

V114 agrega únicamente la referencia opcional `beer_product.image_filename`, validada como hash SHA-256 con extensión PNG/JPG. Los bytes permanecen en la carpeta portable local (`LAEXCLUSIVA_PRODUCT_IMAGES_DIR`); no se eliminan ni modifican imágenes o referencias históricas.

## Compatibilidad del instalador y verificador de PR #30

Los scripts de PR #30 que hoy se detienen en V112 deben actualizarse antes de integrar la rama
comercial que incluye V113/V114:

1. Extender su manifiesto de migraciones a V113 y V114 en ese orden, sin repetir ni alterar V111 o
   V112.
2. Cambiar la verificación del cupo final: V113 renombra `customer.credit_limit` a
   `customer.legacy_credit_limit`. Comprobar la preservación de los valores históricos desde la
   nueva columna y no exigir la antigua tras V113.
3. Comprobar que V114 crea `beer_product.image_filename` nullable y la restricción
   `ck_beer_product_image_filename`; productos históricos sin referencia siguen válidos.
4. Ejecutar la verificación combinada en el laboratorio PostgreSQL desechable: fijar un cupo no cero
   antes de V113, confirmar su conservación después; validar referencias permitidas y rechazo SQL
   de rutas/extensiones no admitidas después de V114.

La secuencia de esta rama y las restricciones V113/V114 se prueban en `database/validation/identity`.
La adaptación y ejecución de los scripts de PR #30 pertenece a Jesús y permanece pendiente; no
aplicar migraciones de esta secuencia a una base existente para comprobar compatibilidad.

## Medios de pago históricos

Los recibos de caja históricos sin medio informado conservan `NULL`. Las llamadas compatibles que omiten el medio usan `OTRO`; los abonos generales históricos se pueden reintentar con ese valor sin cambiar el recibo original.
