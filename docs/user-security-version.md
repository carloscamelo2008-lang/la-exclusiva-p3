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

La columna debe desplegarse antes del futuro mapeo Java. Las actualizaciones Java
deberán filtrar por ID y versión esperada y recuperar la versión resultante con
`RETURNING`; no incrementarán otra vez la versión ni reintentarán snapshots antiguos.
Una actualización de cero filas deberá tratarse como ausencia o conflicto.

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
no analizan completamente PL/pgSQL ni prueban su ejecución. Quedan pendientes, con
autorización, pruebas PostgreSQL de datos históricos, cambios y no-op, manipulación
manual, nulos, rollback, desbordamiento y actualizaciones concurrentes. Las tablas
temporales creadas con `LIKE INCLUDING ALL` no copian este trigger automáticamente.
