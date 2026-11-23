# V104 – Versión de seguridad de usuarios

**Estado: preparada; aplicación pendiente de autorización.** Carlos reservó V104;
Jesús debe revisar sus implicaciones de seguridad. No se ha ejecutado en ninguna base.

`V104__user_security_version.sql` añade a `public.user_account` la columna
`security_version BIGINT NOT NULL DEFAULT 0` y una restricción no negativa.
Las cuentas existentes comienzan en cero, sin cambiar sus otros datos. Los INSERT
también comienzan en cero, aunque el cliente solicite otra versión.

El trigger incrementa la versión una vez cuando cambian realmente `role`,
`password_hash`, `active` o `seller_id`, usando `IS DISTINCT FROM` para contemplar
nulos. Si no cambian, conserva la versión anterior, incluso ante un intento de
modificarla directamente. Rechaza el desbordamiento BIGINT. La función utiliza
`SECURITY INVOKER` y `search_path = pg_catalog`.

La columna debe desplegarse antes de utilizar el adaptador Java actualizado. Sus
SELECT recuperan `security_version` y rechazan versiones nulas o negativas. Los
INSERT, con ID explícito o generado, omiten la versión de entrada y exigen
`RETURNING id, security_version` con versión inicial cero. Los IDs históricos y el
límite de 64 intentos por colisiones de identidad se conservan.

El UPDATE filtra por ID y versión esperada y recupera `RETURNING security_version`.
No escribe la versión en SET ni la incrementa en Java: esa responsabilidad es del
trigger. Cero filas provoca `ConcurrentModificationException` por cuenta ausente o
modificada; no distingue ambas causas atómicamente ni reintenta automáticamente.
La fecha de creación no se actualiza. Un resultado nulo, negativo o múltiple falla
con un diagnóstico seguro. No hay compatibilidad silenciosa con esquemas sin V104:
las cinco integraciones de usuarios requieren ahora V001 y V104 instaladas.

El adaptador asigna los metadatos al objeto recibido después del cierre correcto de
sus recursos propios. Si participa en una transacción exterior, esos valores siguen
siendo provisionales hasta el commit; un rollback no restaura el objeto y obliga a
descartarlo o recargarlo. Un conflicto marca rollback-only aunque el llamador lo
capture. No se crean coordinadores ni conexiones adicionales para estas operaciones.

Un fallo de comunicación o cierre después de escribir puede dejar una escritura
confirmada y un resultado incierto. No debe interpretarse como ausencia de cambios
ni provocar reintentos automáticos. Los errores conservan operación y SQLSTATE
seguros; los conflictos tampoco exponen detalles SQL suprimidos durante el cierre.

La clave `pg_advisory_xact_lock(1279346008, 1)` queda reservada para el futuro
protocolo compartido de bootstrap y protección del último administrador activo,
reutilizando `JdbcConnectionProvider` y `JdbcTransactionManager`. V104 no implementa
ese protocolo, la revalidación de sesiones ni cambios en autenticación o navegación.

La migración se aplicará una sola vez, manualmente y después de verificar destino,
esquema y autorización. Requiere `public.user_account` de V001; no modifica V001,
V002 ni V100–V103. No necesita los objetos financieros para su propio DDL.

El trigger no sustituye permisos, control de concurrencia, validación de sesión ni
bloqueos administrativos. Un propietario o superusuario puede alterar sus
protecciones. Las aplicaciones antiguas tampoco adquieren control de snapshots por
instalar esta columna. Deben revisarse permisos SQL y orden de despliegue con el equipo.

Las pruebas estáticas solo revisan el alcance y protecciones declaradas del script;
no analizan completamente PL/pgSQL ni prueban su ejecución. Las pruebas JDBC simuladas
verifican mapeo, parámetros, RETURNING, conflictos, recursos y participación
transaccional; no ejecutan el trigger ni demuestran incrementos reales. Quedan pendientes, con
autorización, pruebas PostgreSQL de datos históricos, cambios y no-op, manipulación
manual, nulos, rollback, desbordamiento y actualizaciones concurrentes. Las tablas
temporales creadas con `LIKE INCLUDING ALL` no copian este trigger automáticamente.

## Integraciones V104 preparadas, todavía no ejecutadas

`UserSecurityVersionPostgresIntegrationTest` añade 11 casos previstos, separados de
las cinco integraciones existentes, que se conservan. Los casos SQL directos cubren
INSERT con versión solicitada, cada campo de seguridad, cambios conjuntos, nulos,
no-op, username y versiones impuestas. Los casos del adaptador cubren mapeo,
RETURNING, snapshots obsoletos, rollback y dos escritores independientes con commit
o rollback del primero. Observan la espera real mediante `pg_blocking_pids`, con
latches y tiempos límite, sin `sleep()` como sincronización.

Estas pruebas no aplican migraciones. Exigen autorización mediante
`-Dlaexclusiva.test.postgres.userSecurityVersion=true`, además de las variables
privadas de conexión. La URL solo admite `localhost`, `127.0.0.1` o `[::1]`, puerto
5432 explícito o predeterminado y base exacta `la_exclusiva_test`, sin opciones ni
múltiples hosts. Cada conexión física verifica base real, dirección loopback,
puerto, PostgreSQL 18 y presencia de la columna, CHECK validado y trigger habilitado
con función invocadora y search_path protegido antes de permitir fixtures. Una
instalación incompleta provoca error; no se omite silenciosamente ni se repara.

Se fija search_path a `pg_catalog, public` y se acotan conexión, consultas y esperas.
Los fixtures usan nombres exclusivos registrados antes de insertar y limpieza por
username exacto e ID exacto de vendedor. La limpieza se omite si siguen activos
escritores; ante desconexiones pueden quedar fixtures pendientes de revisión y los
avances de secuencias no se revierten. No se utiliza limpieza global ni se desactiva
el trigger. Se requiere acceso a catálogos, objetos de usuarios/vendedores, identidad
y observación de bloqueos de las sesiones de prueba; no se exige superusuario.

`UserSecurityVersionIntegrationSafetyTest` verifica las barreras con JDBC simulado,
sin abrir sockets. Su aprobación no valida V104 en PostgreSQL. El fixture ficticio
anterior a V104 debe prepararse y comprobarse manualmente durante una aplicación
autorizada; estas pruebas posteriores no demuestran migración de filas preexistentes.
La revocación de sesiones y el último administrador siguen pendientes de implementar.

Solo después de revisión y autorización, usando Temurin Java 17 y variables ya
configuradas de forma privada, se propondrá ejecutar:

```powershell
mvn -o "-Dtest=UserSecurityVersionPostgresIntegrationTest" "-Dlaexclusiva.test.postgres.userSecurityVersion=true" test
```

Este comando es documentación para una ejecución futura; no acredita una ejecución.

## V105 — Exclusividad de vendedor en cuentas activas

Preparada, no aplicada. Carlos confirmó V104 y V105 independientes, sin renombrar
V104 a V003. V004 corresponde a Compras y Recepción, integrada en develop, y
V100–V103 a finanzas. Los demás acuerdos pendientes no se consideran aprobados.

V105 crea `uq_user_account_active_seller` sobre `public.user_account(seller_id)`
con predicado `active = TRUE AND seller_id IS NOT NULL`, sin filtro por rol.
Permite múltiples cuentas inactivas con el mismo vendedor y cuentas con vendedor
NULL. Conserva el índice general `idx_user_account_seller_id` y no corrige filas.

Diagnóstico previo, documentado pero no ejecutado:

```sql
SELECT seller_id, COUNT(*) AS active_account_count
FROM public.user_account
WHERE active = TRUE
  AND seller_id IS NOT NULL
GROUP BY seller_id
HAVING COUNT(*) > 1
ORDER BY seller_id;
```

Debe devolver cero filas; los duplicados requieren resolución humana autorizada.
El diagnóstico no sustituye la unicidad ni impide cambios posteriores concurrentes.
Antes del despliegue deben verificarse destino, migraciones previas y V104, que el
adaptador Java necesita. El DDL de V105 depende directamente de la tabla de V001.
V104 y V105 siguen sin aplicar. Cualquier futura ejecución requiere aprobación
expresa de Carlos y Jesús y se limitará a `la_exclusiva_test`.

El índice ordinario se crea dentro de BEGIN/COMMIT y puede bloquear escrituras;
requiere ventana autorizada y límites de espera acordados. No usa CONCURRENTLY ni
IF NOT EXISTS. Ante duplicados u otro error, detenerse y revertir la transacción;
no reparar datos ni repetir automáticamente. Un índice válido rechazará conflictos
entre escritores externos: el segundo puede esperar al primero; tras commit puede
fallar por unicidad y tras rollback puede continuar si no queda otro conflicto.
La unicidad es inmediata: una transferencia debe liberar primero al propietario.

El adaptador distingue SQLSTATE 23505 y el nombre exacto del índice mediante
PSQLException/ServerErrorMessage, sin analizar mensajes del servidor. Devuelve
«El vendedor ya está asignado a otra cuenta activa.»; los demás índices y los
metadatos ausentes conservan el mensaje genérico de ID o username. No hay reintentos
ni cambios en rollback-only. No se propagan detalles sensibles del servidor.

### Validación PostgreSQL pendiente

Las pruebas estáticas y JDBC simuladas no validan el índice en una base real.
Con fixtures exclusivos y autorización deberán comprobarse: rechazo de duplicados
activos para ambos roles; una activa con varias inactivas; múltiples NULL;
reactivación con vendedor ocupado; escritura SQL directa; dos asignaciones, dos
reactivaciones y operación mixta con conexiones independientes; primer escritor
con commit y con rollback; conservación de active, seller_id y security_version
tras rechazo/rollback; interacción con el trigger V104; índice válido y predicado
correcto en catálogos; diagnóstico sin duplicados y fallo seguro de creación ante
duplicados históricos. No se aplicarán migraciones automáticamente desde pruebas.

## Dependencia adicional del adaptador: V106

La corrección de reutilización de ID del PR #22 incorpora account_instance_id.
El adaptador JDBC necesita V104 **y V106** aplicadas mediante autorización
específica antes de ejecutarse. V105 mantiene su propósito de exclusividad de
vendedor. Ninguna de estas migraciones se ejecutó durante esta corrección.
Las integraciones existentes que usan el adaptador requieren también V106.
Véase [identidad persistente de cuenta](account-instance-identity.md).
