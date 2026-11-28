# Despliegue comercial de PostgreSQL

## Instalación desde cero

`database/deployment/install.psql` requiere un esquema `public` vacío y el rol
dueño/instalador con permisos de DDL. Ejecuta el comando documentado en
[PostgreSQL y JDBC](../README.md#instalación-comercial-y-rol-aplicativo).
El orden es `V001 → V004 → V002 → V100 … V112`. Las migraciones conservan sus
transacciones individuales. El registro de las 16 versiones se inserta al
final, tras completar todas las migraciones. Si se interrumpe la instalación,
pueden quedar cambios confirmados y la guarda rechazará otra ejecución sobre
esa base. Conserva el diagnóstico y repite sobre otra base vacía tras resolver
la causa. Para operar, configura un rol aplicativo separado como se documenta
en la guía JDBC.

Para comprobar el instalador con JUnit, prepara una base aislada
`la_exclusiva_test` con este mismo script, configura `LAEXCLUSIVA_TEST_DB_URL` y
las variables JDBC de usuario y contraseña para esa base, y ejecuta:

```powershell
mvn "-Dlaexclusiva.test.postgres.commercialDeployment=true" "-Dtest=PostgresStartupVerifierIntegrationTest" test
```

## Respaldo y restauración de prueba obligatorios

Antes de intervenir una base con datos reales, acuerda una ventana de cambio,
confirma el servidor y la base, detén las escrituras de la aplicación y crea
un respaldo en un destino protegido. Este ejemplo restaura en una base nueva
de una instancia de ensayo aislada. Sustituye el puerto y los nombres por los
de tu entorno. Usa las herramientas cliente de una versión compatible.

```powershell
pg_dump -h localhost -p 5432 -U rol_respaldo -d la_exclusiva --format=custom --file=la_exclusiva_previo.dump
createdb -h localhost -p 55432 -U rol_restauracion la_exclusiva_restauracion
pg_restore -h localhost -p 55432 -U rol_restauracion -d la_exclusiva_restauracion --exit-on-error --single-transaction la_exclusiva_previo.dump
```

Comprueba el código de salida de cada comando antes de continuar. Prepara en
ensayo los roles necesarios para restaurar propietarios y permisos, pues
`pg_dump` de una base no incluye las definiciones de roles del clúster.
Valida versiones, conteos e integridad de clientes, ventas, cartera y stock,
y comprueba el acceso con el rol aplicativo. Ensaya la actualización prevista
sobre la copia restaurada y registra el resultado antes de autorizar el cambio
en la base real. El instalador limpio se reserva para bases vacías. Si ya hay
datos, identifica las versiones aplicadas y prepara un plan de actualización y
registro del historial a partir de la evidencia de esa base.

## Despliegue de V110 y V111

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
