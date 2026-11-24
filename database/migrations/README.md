# Despliegue de V110 y V111

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

Conserva un respaldo previo y respeta el orden estricto V110 → V111 → V112. Antes de aplicar V111, ejecuta esta consulta de solo lectura con la normalización de `uq_customer_id_normalized`.

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

V111 deja el cupo de los clientes existentes en cero. Después de migrar, revisa sus saldos y asigna individualmente el cupo total autorizado desde la edición de Clientes. Si se hace por SQL, usa un `UPDATE customer SET credit_limit = :cupo_aprobado WHERE id = :id_cliente` parametrizado y verifica que afecte exactamente una fila. El saldo pendiente consume parte de ese cupo. Para clientes sin crédito autorizado, conserva el cero.

## Medios de pago históricos

Los recibos de caja históricos sin medio informado conservan `NULL`. Las llamadas compatibles que omiten el medio usan `OTRO`; los abonos generales históricos se pueden reintentar con ese valor sin cambiar el recibo original.
