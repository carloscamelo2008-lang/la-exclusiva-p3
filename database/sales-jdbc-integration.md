# Integración JDBC de ventas

## Estado y alcance

Este documento acompaña el trabajo de `feature/sales-jdbc`. Las capacidades se marcan como
implementadas solo después de contar con código y pruebas ejecutadas. La sesión actual prepara
el adaptador del repositorio de ventas; todavía no activa la composición JDBC de `SaleService`.

## Incompatibilidades encontradas y decisión aplicada

| Contrato Java | Esquema | Solución |
|---|---|---|
| `Sale` genera siempre un UUID y no permitía cargar el ID SQL | `sale.id` es `VARCHAR(48)` y debe conservarse al leer | Se añadió `Sale.reconstitute(...)`, que valida datos y conserva ID, fecha, líneas y pagos sin generar otra identidad. |
| `SaleLine` guardaba el precio aplicado en memoria, pero no exponía la capacidad capturada | `sale_line` conserva `unit_price` y `units_per_basket_snapshot` | Se añadió la fábrica validada `SaleLine.reconstitute(...)` y un accessor para el snapshot de capacidad. El mapeo no sustituirá ninguno por valores actuales del catálogo. |
| `SalePayment` no tiene ID de dominio | `sale_payment.id` lo genera PostgreSQL | La operación de actualización se delimitará a anexar pagos bajo bloqueo de la venta y comparará ocurrencias por importe y fecha; no reconstruirá ni borrará el historial. |
| El contrato común ofrece `deleteById` para compensaciones en memoria | Las FK comerciales son `ON DELETE RESTRICT` y la historia debe conservarse | El adaptador JDBC rechazará el borrado físico de forma explícita. El rollback se hace con la transacción que insertó la venta, no con un borrado posterior. |

Los importes del esquema son `NUMERIC(19,2)`. El adaptador debe rechazar escalas que no quepan en dos decimales en vez de redondearlas. Las fechas se guardarán con precisión de microsegundos para corresponder a `TIMESTAMP(6) WITHOUT TIME ZONE`.

## Modelo de dominio para snapshots históricos

**Implementado en esta rama:** las ventas nuevas mantienen su constructor actual; la fábrica de
reconstitución es un punto de entrada separado, valida ID, vendedor, líneas, pagos y saldo, y no
invoca el generador de UUID. Una línea reconstituida mantiene su precio histórico y su capacidad
de canasta incluso si difieren del catálogo cargado actualmente.

**Pendiente de verificar en el adaptador:** lectura JDBC de `sale`, `sale_line` y `sale_payment`,
incluyendo fechas de pagos originales y presentaciones por código.

## Repositorio JDBC

El adaptador debe usar `JdbcConnectionProvider` para todas las consultas y
`JdbcTransactionManager` para las operaciones con varias escrituras. No cierra la conexión
recibida, ni confirma o revierte fuera del coordinador. Cada sentencia y resultado se administra
con try-with-resources y cada dato externo se envía como parámetro JDBC.

La inserción de `sale`, todas sus líneas y su pago inicial positivo pertenece a una sola
transacción. Un ID duplicado se rechaza; no se hace upsert. La consulta completa recupera los
snapshots históricos desde las columnas de venta y no consulta precios actuales para recalcularlos.
El listado debe ordenar por fecha e ID y cargar líneas y pagos en consultas agrupadas, evitando
duplicar filas por el cruce de dos relaciones uno-a-muchos.

La actualización representa únicamente anexar abonos. El repositorio bloquea la fila de venta
con `SELECT ... FOR UPDATE`, comprueba que los campos y líneas históricos no cambiaron y que el
conjunto solicitado conserva todas las ocurrencias ya persistidas. Un pago repetido en una
solicitud se reconoce como ya aplicado; una eliminación o modificación histórica se rechaza. La
base no tiene una clave de idempotencia de pago expuesta al dominio: la comparación por
`(importe, occurred_at)` es un contrato acotado del adaptador, no una identidad universal de pago.
La integración de servicio deberá conservar fechas de solicitud estables y definir el límite de
reintentos.

## Política de borrado

`deleteById` del adaptador JDBC lanza `UnsupportedOperationException`. La operación no elimina
ventas, líneas ni pagos. Antes de conectar el adaptador al flujo actual habrá que mover la
coordinación del servicio a una transacción JDBC real; las compensaciones actuales dependen del
borrado permitido por repositorios en memoria.

## Transacción futura de `SaleService`

El trabajo actual de inventario implementa compensaciones en memoria y el saldo de ventas de
`CustomerService` vive en un mapa en memoria. Por ello no es seguro activar parcialmente un
`SaleService` JDBC. El caso de registro debe coordinar en una unidad de trabajo: bloqueo del
cliente cuando aplique, venta, líneas, pago inicial, existencias y movimientos, más actualización
del saldo del cliente. Todo confirma o revierte con la misma conexión.

El abono debe bloquear la fila de venta, recalcular su saldo desde líneas y pagos, bloquear la
fila del cliente y anexar el pago junto con la reducción del saldo. Cuando los DAO de inventario
y movimientos de Jesús estén disponibles, deben recibir la conexión transaccional compartida y
bloquear productos en orden ascendente para reducir interbloqueos. No se deben retirar las
compensaciones en memoria hasta que la composición use de forma coherente un límite transaccional
común.

## Pruebas y limitaciones

- Las pruebas unitarias de modelo no requieren PostgreSQL.
- Las pruebas de repositorio deben distinguir mocks JDBC de integración real.
- La integración real solo podrá conectarse mediante `LAEXCLUSIVA_TEST_DB_URL`, validada por
  `DatabaseConfig.fromTestEnvironment()` para que apunte a `la_exclusiva_test`.
- No se ejecutan migraciones desde estas pruebas. La base de pruebas debe tener el esquema V001
  aplicado previamente.
- Sin URL de prueba configurada, los resultados no deben describirse como pruebas de PostgreSQL.
- No se escriben credenciales ni se limpia ninguna tabla compartida.

## Próximos pasos

1. Terminar inserción atómica, lecturas agrupadas y anexado seguro de pagos en `SaleRepositoryJdbc`.
2. Probar rollback, IDs duplicados, historial y concurrencia simulada.
3. Preparar una prueba PostgreSQL aislada que se omita claramente si falta la configuración.
4. Diseñar y documentar el puerto transaccional del caso de uso, sin reemplazar los DAO de inventario.
5. Revisar con Carlos el contrato de identidad/idempotencia de pagos antes de activar JDBC en producción.
