# Integración JDBC de ventas

## Estado y alcance

Este documento acompaña el trabajo de `feature/sales-jdbc`. Las capacidades se marcan como
implementadas solo después de contar con código y pruebas ejecutadas. El adaptador JDBC y el puerto
de abonos idempotentes están implementados y verificados con JDBC simulado; la composición JDBC de
`SaleService` todavía no está activa.

## Incompatibilidades encontradas y decisión aplicada

| Contrato Java | Esquema | Solución |
|---|---|---|
| `Sale` genera siempre un UUID y no permitía cargar el ID SQL | `sale.id` es `VARCHAR(48)` y debe conservarse al leer | Se añadió `Sale.reconstitute(...)`, que valida datos y conserva ID, fecha, líneas y pagos sin generar otra identidad. |
| `SaleLine` guardaba el precio aplicado en memoria, pero no exponía la capacidad capturada | `sale_line` conserva `unit_price` y `units_per_basket_snapshot` | Se añadió la fábrica validada `SaleLine.reconstitute(...)` y un accessor para el snapshot de capacidad. El mapeo no sustituirá ninguno por valores actuales del catálogo. |
| `SalePayment` no tiene una clave de solicitud de dominio | La base genera `sale_payment.id` | V002 añade `idempotency_key`; los abonos explícitos se identifican por `(sale_id, idempotency_key)`, nunca por importe y fecha. |
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

`SalePaymentAppender.appendPayment(saleId, amount, idempotencyKey)` es el único contrato JDBC
seguro para anexar pagos. La operación participa en una transacción exterior o crea una propia,
bloquea la cabecera con `SELECT ... FOR UPDATE`, busca primero la clave y compara el importe. Si
coinciden devuelve la fila persistida y su fecha original; un importe distinto con la misma clave
se rechaza. Para una clave nueva calcula el total histórico desde líneas y pagos, valida que el
importe positivo no exceda el saldo y lo inserta con clave. El bloqueo serializa solicitudes sobre
una venta y la restricción única de V002 es una defensa adicional de PostgreSQL. Las filas
anteriores y los pagos iniciales conservan `idempotency_key = NULL`.

`SaleRepositoryJdbc.update(Sale)` rechaza explícitamente todas las actualizaciones por instantánea.
El modelo no transporta la clave de persistencia, así que ni una copia obsoleta ni la tupla
`(importe, occurred_at)` permiten distinguir de manera fiable nuevos abonos y reintentos. Los
abonos deben anexarse por el puerto idempotente. Esta restricción evita borrar o duplicar historia;
no activa ni modifica `SaleService`.

Aplicar `V002__payment_idempotency.sql` manualmente después de V001, solo tras identificar y
verificar el destino. Esta tarea no ejecutó ninguna migración ni alteró una base de datos.

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
- `SaleRepositoryJdbcTest` usa un controlador JDBC simulado para comprobar clave idempotente,
  fechas de reintento, claves distintas con igual importe/fecha, importes inválidos, saldo,
  preservación histórica, rechazo de snapshots, participación transaccional y rollback SQL. No es
  una prueba PostgreSQL y no demuestra bloqueos reales ni restricciones del servidor.
- La prueba PostgreSQL comprueba la restricción única y la concurrencia con dos conexiones cuando
  existe `LAEXCLUSIVA_TEST_DB_URL`; requiere V001 y V002 ya instaladas en `la_exclusiva_test`.
- Ejecución dirigida de esta intervención (Java 17.0.21, Maven 3.10.0): 18 pruebas contabilizadas,
  17 aprobadas y 1 omitida; 0 fallidas y 0 errores. La omitida es la prueba PostgreSQL, ya que
  `LAEXCLUSIVA_TEST_DB_URL` no estaba configurada en ese proceso.
- Verificación completa del 2026-11-13: `mvn clean verify` con Java 17.0.21 finalizó en
  `BUILD SUCCESS`: 157 pruebas contabilizadas, 156 aprobadas, 0 fallidas, 0 errores y 1 omitida
  por falta de `LAEXCLUSIVA_TEST_DB_URL`.
- Verificación posterior observada en `target/surefire-reports` (2026-11-13 11:57, hora local):
  157 pruebas, 0 fallidas, 0 errores y 0 omitidas. El reporte PostgreSQL registra 1 prueba aprobada
  y 0 omitidas; las otras 156 pruebas son simuladas o unitarias. La prueba usa
  `DatabaseConfig.fromTestEnvironment()`, que rechaza cualquier URL cuyo nombre no sea exactamente
  `la_exclusiva_test`, y comprobó la columna/clave única de V002. Esto acredita que V001 y V002
  funcionaban en esa ejecución.
- Después de ese reporte se ampliaron las aserciones PostgreSQL para añadir conflicto de importe,
  dos claves con la misma tupla importe/fecha, reloj posterior en el reintento, pago inicial y
  rollback de una inserción parcial. `mvn test-compile` con Java 17 terminó en `BUILD SUCCESS`, pero
  estas nuevas aserciones aún necesitan ejecutarse contra PostgreSQL: las variables no estaban
  disponibles en el proceso de esta revisión. Por ello, los reportes completos anteriores no se
  presentan como validación de esas aserciones añadidas.
- Maven advirtió 6 problemas de modelo efectivo en cada dependencia JavaFX; las pruebas también
  mostraron el aviso de configuración no modular de JavaFX durante `AuthenticationServiceTest`.
  No hubo fallos de compilación y la verificación completa pasó.
- La integración real solo podrá conectarse mediante `LAEXCLUSIVA_TEST_DB_URL`, validada por
  `DatabaseConfig.fromTestEnvironment()` para que apunte a `la_exclusiva_test`.
- No se ejecutan migraciones desde estas pruebas. La base aislada debe tener V001 y V002 aplicadas
  previamente por una persona que haya verificado el destino `la_exclusiva_test`.
- `SaleRepositoryJdbcPostgresIntegrationTest` se omite si falta la URL dedicada. El reporte
  posterior anterior la ejecutó realmente; la versión ampliada de la prueba todavía requiere una
  nueva ejecución PostgreSQL.
- No se escriben credenciales ni se limpia ninguna tabla compartida.

La tabla `sale_line` conserva únicamente el precio histórico de la presentación vendida. El
producto asociado a una línea reconstituida no ofrece un snapshot histórico de los otros precios
del catálogo; para cálculos históricos se debe usar `SaleLine.getUnitPrice()`.

## Próximos pasos

1. Repetir `mvn clean verify` con la configuración exclusiva de `la_exclusiva_test` para ejecutar la versión ampliada de la prueba PostgreSQL; el reporte previo confirma que V001 y V002 ya se usaban correctamente.
2. Diseñar con Carlos y el equipo cómo `SaleService` obtendrá y conservará una clave estable por operación antes de conectarlo al puerto.
3. Implementar la coordinación del servicio cuando los DAO de inventario y cartera compartan el proveedor transaccional.
