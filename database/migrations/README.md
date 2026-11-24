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

V111 deja el cupo de los clientes existentes en cero. Después de migrar, revisa sus saldos y asigna individualmente el cupo total autorizado desde la edición de Clientes. Si se hace por SQL, usa un `UPDATE customer SET credit_limit = :cupo_aprobado WHERE id = :id_cliente` parametrizado y verifica que afecte exactamente una fila. El saldo pendiente consume parte de ese cupo. Para clientes sin crédito autorizado, conserva el cero.
