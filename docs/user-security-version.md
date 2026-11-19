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
