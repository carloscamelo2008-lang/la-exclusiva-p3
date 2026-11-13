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
| `SalePayment` no tiene ID de dominio | `sale_payment.id` lo genera PostgreSQL | La actualización implementada anexa pagos bajo bloqueo de venta y compara ocurrencias por importe y fecha; no reconstruye ni borra el historial. |
| El contrato común ofrece `deleteById` para compensaciones en memoria | Las FK comerciales son `ON DELETE RESTRICT` y la historia debe conservarse | El adaptador JDBC rechazará el borrado físico de forma explícita. El rollback se hace con la transacción que insertó la venta, no con un borrado posterior. |

Los importes del esquema son `NUMERIC(19,2)`. El adaptador debe rechazar escalas que no quepan en dos decimales en vez de redondearlas. Las fechas se guardarán con precisión de microsegundos para corresponder a `TIMESTAMP(6) WITHOUT TIME ZONE`.

## Modelo de dominio para snapshots históricos

**Implementado en esta rama:** las ventas nuevas mantienen su constructor actual; la fábrica de
reconstitución es un punto de entrada separado, valida ID, vendedor, líneas, pagos y saldo, y no
invoca el generador de UUID. Una línea reconstituida mantiene su precio histórico y su capacidad
de canasta incluso si difieren del catálogo cargado actualmente.

**Implementado:** lectura JDBC de `sale`, `sale_line` y `sale_payment`, incluyendo fechas
originales de pago y presentaciones por código. La cabecera usa joins de vendedor/cliente; líneas
y pagos se cargan por lote. `findAll()` consulta cabeceras, líneas y pagos en tres consultas, sin
cruzar las relaciones uno-a-muchos. El producto asociado a una línea conserva datos actuales de
identidad/catálogo; precio y capacidad vendidos se leen de los snapshots SQL.

## Repositorio JDBC

El adaptador implementado usa `JdbcConnectionProvider` para todas las consultas y
`JdbcTransactionManager` para las operaciones con varias escrituras. No cierra la conexión
recibida, ni confirma o revierte fuera del coordinador. Cada sentencia y resultado se administra
con try-with-resources y cada dato externo se envía como parámetro JDBC.

La inserción de `sale`, todas sus líneas y su pago inicial positivo pertenece a una sola
transacción. Un ID duplicado se rechaza; no se hace upsert. La consulta completa recupera los
snapshots históricos desde las columnas de venta y no consulta precios actuales para recalcularlos.
El listado ordena por fecha e ID y carga las relaciones en consultas agrupadas.

La actualización implementada representa únicamente anexar abonos. El repositorio bloquea la fila
de venta con `SELECT ... FOR UPDATE`, comprueba que los campos y líneas históricos no cambiaron y
que el conjunto solicitado conserva todas las ocurrencias ya persistidas. Un pago repetido en una
solicitud se reconoce como ya aplicado; una eliminación o modificación histórica se rechaza. La
base no tiene una clave de idempotencia de pago expuesta al dominio: la comparación por
`(importe, occurred_at)` con multiplicidad es un contrato acotado del adaptador, no una identidad
universal de pago. La futura integración de servicio deberá conservar fechas de solicitud estables
y definir el límite de reintentos.

## Política de borrado

`deleteById` del adaptador JDBC lanza `UnsupportedOperationException`. La operación no elimina
ventas, líneas ni pagos. Antes de conectar el adaptador al flujo actual habrá que mover la
coordinación del servicio a una transacción JDBC real; las compensaciones actuales dependen del
borrado permitido por repositorios en memoria.

## Diseño de la transacción futura de `SaleService`

El trabajo actual de inventario implementa compensaciones en memoria y el saldo de ventas de
`CustomerService` vive en un mapa en memoria. Por ello no es seguro activar parcialmente un
`SaleService` JDBC. El diseño para la siguiente etapa es:

1. **Operaciones que comparten transacción.** `registerSale(...)` coordina cabecera, líneas, pago
   inicial, descuento de existencias, movimientos y aumento de saldo del cliente cuando hay
   crédito. `registerPayment(...)` coordina el abono de la venta y la reducción equivalente del
   saldo del cliente. Una excepción en cualquiera de esos pasos debe revertir toda la unidad.
2. **Bloqueos SQL.** El cliente se bloquea al modificar su balance. `InventoryRepositoryJdbc` debe
   ofrecer la consulta de existencias `FOR UPDATE`; se bloquean todas las referencias en orden
   ascendente por `product_id`. Un abono bloquea la venta para serializar pagos y vuelve a calcular
   saldo con las líneas/pagos ya persistidos. Los límites exteriores deben adquirir bloqueos en
   un orden común: cliente, venta si existe, inventario ordenado.
3. **Dependencias de `SaleService`.** La futura composición inyecta el repositorio de ventas,
   servicios/repositorios JDBC de catálogo e inventario, movimientos, clientes y vendedores, más
   un límite de unidad de trabajo (`JdbcTransactionManager` o abstracción de transacción). Todas
   las implementaciones deben compartir el mismo `JdbcConnectionProvider` y coordinador.
4. **Compensaciones que se retiran al activar JDBC.** Dentro de una unidad JDBC ya no se deben
   llamar `SaleRepository.deleteById`, `InventoryService.rollbackDiscount` ni restaurar snapshots
   de deuda: el `rollback` de la transacción restablece las filas. La ruta en memoria conserva su
   estrategia actual hasta que la abstracción soporte ambos modos.
5. **Comportamiento en memoria.** Mantener los constructores y casos de uso actuales de los
   repositorios en memoria. Un puerto de unidad de trabajo puede tener una implementación sin
   JDBC que ejecute la operación directamente; las compensaciones en memoria solo se eliminan
   cuando las pruebas de servicio confirmen que su semántica sigue protegida.
6. **Evitar dobles descuentos.** No confiar en `canFulfill(...)` como protección concurrente.
   Agrupar cantidades repetidas por producto, bloquear existencias en orden estable, validar el
   total bloqueado y descontar una sola vez por producto. Opcionalmente reforzar con
   `UPDATE ... WHERE total_units >= ?` y verificar una fila afectada.
7. **Coordinar inventario y ventas.** El caso de uso establece el orden global de bloqueos y
   mantiene bloqueos hasta confirmar la venta y registrar cada movimiento. Los DAO no abren ni
   confirman conexiones por separado. Esto reduce interbloqueos y evita movimientos sin descuento
   o descuentos sin venta.
8. **Consulta de deuda pendiente.** Falta un puerto para obtener desde PostgreSQL la deuda de
   ventas asignada a un cliente. `CustomerService` hoy consulta `saleDebts`, un `HashMap` en
   memoria. Se propone `CustomerDebtQuery.findOutstandingSalesDebt(customerId)` y un adaptador que
   agregue líneas y pagos por venta antes de sumarlos; el balance agregado del cliente no debe
   confundirse con deuda general.
9. **Coordinación con los DAO de Jesús.** Cuando estén disponibles, confirmar firmas de consulta
   y bloqueo, su uso del proveedor compartido y contratos de rollback. Después integrar bajo el
   límite de servicio, añadir pruebas de concurrencia/integración en la base exclusiva de pruebas
   y revisar el cambio con el equipo antes de conectar `Main` a PostgreSQL.

No se modificaron `SaleService`, `InventoryService`, `CustomerService` ni los DAO de productos o
inventario. La integración final depende de esos contratos y requiere revisión del equipo.

## Pruebas y limitaciones

- Las pruebas unitarias de modelo no requieren PostgreSQL.
- `SaleRepositoryJdbcTest` usa un controlador JDBC simulado para comprobar SQL parametrizado,
  rollback, anexado idempotente y mapeo. No es una prueba PostgreSQL.
- La ejecución dirigida del 2026-11-13 pasó 26 pruebas y omitió la única prueba PostgreSQL porque
  `LAEXCLUSIVA_TEST_DB_URL` no estaba configurada. No se estableció conexión ni se modificó base
  de datos alguna.
- La integración real solo podrá conectarse mediante `LAEXCLUSIVA_TEST_DB_URL`, validada por
  `DatabaseConfig.fromTestEnvironment()` para que apunte a `la_exclusiva_test`.
- No se ejecutan migraciones desde estas pruebas. La base de pruebas debe tener el esquema V001
  aplicado previamente.
- `SaleRepositoryJdbcPostgresIntegrationTest` ejecuta un round-trip real solo si se configura la
  URL dedicada; si falta, se omite y no debe describirse como prueba PostgreSQL ejecutada.
- No se escriben credenciales ni se limpia ninguna tabla compartida.

La tabla `sale_line` conserva únicamente el precio histórico de la presentación vendida. El
producto asociado a una línea reconstituida no ofrece un snapshot histórico de los otros precios
del catálogo; para cálculos históricos se debe usar `SaleLine.getUnitPrice()`.

## Próximos pasos

1. Ejecutar `mvn clean verify` y comprobar advertencias de compilación.
2. Revisar con Carlos el contrato de identidad/idempotencia de pagos antes de activar JDBC en producción.
3. Implementar la coordinación de servicio cuando los DAO de inventario y cartera compartan el proveedor transaccional.
