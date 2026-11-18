# Integración JDBC de ventas

## Estado y alcance

Este documento registra el avance desde `feature/sales-jdbc` hasta la rama temporal
`feature/full-jdbc-integration`. El adaptador JDBC de ventas, el puerto de abonos idempotentes, la
consulta de cartera, los repositorios de clientes/vendedores y los ajustadores atómicos ya existen.
La rama de integración conecta esos componentes mediante `JdbcSalesWorkflow` y JavaFX, con un modo
en memoria como valor predeterminado. El flujo simulado y las vistas JavaFX se ejecutaron en Maven;
la prueba PostgreSQL de extremo a extremo está preparada, pero aún no se ejecutó porque faltan las
variables de conexión.

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

## Consulta JDBC de cartera de ventas

El puerto `CustomerDebtQuery.findOutstandingSalesDebt(customerId)` y su adaptador
`CustomerDebtQueryJdbc` calculan la deuda asociada exclusivamente a ventas. El SQL agrupa
`sale_line` por venta para sumar `quantity * unit_price`, agrupa `sale_payment` por venta para
sumar abonos y combina esos dos resultados de una fila por venta antes de sumar por cliente. Así,
varias líneas y varios pagos de una misma venta no multiplican los importes entre sí. Clientes sin
ventas y ventas totalmente pagadas aportan cero. Cada venta aporta
`GREATEST(total histórico de líneas - pagos, 0)`; el límite se aplica antes de sumar las ventas.

Un sobrepago ya persistido es una inconsistencia de datos. El `GREATEST` evita que ese saldo
negativo reduzca la deuda de otra venta, pero no convierte el excedente en crédito transferible ni
repara el historial. El sobrepago debe detectarse mediante conciliación de integridad y corregirse
en los datos de su venta. El flujo normal sigue rechazándolo: `SalePaymentAppender` bloquea la venta,
recalcula el saldo y no inserta un abono superior al pendiente.

La consulta no lee `customer.balance`: ese saldo agregado también puede contener deuda general y no
debe confundirse con la deuda de ventas. Usa el `JdbcConnectionProvider` compartido; si hay una
transacción JDBC activa, la consulta se ejecuta en su misma conexión. Es estrictamente de lectura y
no modifica ventas, líneas ni pagos.

### Sustitución futura del mapa `saleDebts`

`CustomerService` conserva hoy `saleDebts` en memoria. Al integrarlo, `CustomerDebtQuery` será la
fuente de verdad de la parte asignada a ventas y la deuda general se derivará como
`customer.balance - outstandingSalesDebt`. No se debe sumar esa deuda de ventas otra vez al balance
ni actualizarla mediante el adaptador de consulta. La futura unidad de trabajo tendrá que guardar
la venta/pago y ajustar el saldo agregado del cliente dentro de una sola transacción compartida.
Las reglas de `addDebtToCustomer` y `applyPaymentToCustomer` deben seguir limitándose a la parte
general; `addSaleDebtToCustomer` y `applyPaymentToSaleDebt` deberán consultar la deuda derivada y
coordinar sus escrituras con la venta. No se cambia aún el servicio ni se retira su estrategia de
compensación en memoria.

## Política de borrado

`deleteById` del adaptador JDBC lanza `UnsupportedOperationException`. La operación no elimina
ventas, líneas ni pagos. Antes de conectar el adaptador al flujo actual habrá que mover la
coordinación del servicio a una transacción JDBC real; las compensaciones actuales dependen del
borrado permitido por repositorios en memoria.

## Diseño transaccional previsto para el servicio de venta (registro de la fase JDBC inicial)

El trabajo actual de inventario implementa compensaciones en memoria y el saldo de ventas de
`CustomerService` vive en un mapa en memoria. Por ello no es seguro activar parcialmente un
`SaleService` JDBC. El diseño para la siguiente etapa es:

1. **Operaciones que comparten transacción.** `registerSale(...)` coordina cabecera, líneas, pago
   inicial, descuento de existencias, movimientos y aumento de saldo del cliente cuando hay
   crédito. `registerPayment(...)` coordina el abono de la venta y la reducción equivalente del
   saldo del cliente. Debe usar `CustomerBalanceAdjusterJdbc.increaseBalance(...)` o
   `decreaseBalance(...)`, no guardar un `Customer` reconstruido desde un snapshot. Una excepción en
   cualquiera de esos pasos debe revertir toda la unidad.
2. **Bloqueos SQL.** `CustomerBalanceAdjusterJdbc` modifica la fila con `UPDATE`, que toma el bloqueo
   de fila de PostgreSQL; para reducir saldo, la misma sentencia exige `balance >= amount`. Esta
   condición se evalúa dentro de la escritura y protege ante abonos concurrentes. `InventoryRepositoryJdbc` debe
   ofrecer la consulta de existencias `FOR UPDATE`; se bloquean todas las referencias en orden
   ascendente por `product_id`. Un abono bloquea la venta para serializar pagos y vuelve a calcular
   saldo con las líneas/pagos ya persistidos. Los límites exteriores deben adquirir bloqueos en
   un orden común: cliente, venta si existe, inventario ordenado.
3. **Dependencias de `SaleService`.** La futura composición inyecta el repositorio de ventas,
   servicios/repositorios JDBC de catálogo e inventario, movimientos, `CustomerRepositoryJdbc`,
   `SellerRepositoryJdbc`, `CustomerBalanceAdjusterJdbc` y un límite de unidad de trabajo
   (`JdbcTransactionManager` o abstracción de transacción). Todas las implementaciones deben
   compartir el mismo `JdbcConnectionProvider` y coordinador.
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
8. **Consulta de deuda pendiente.** `CustomerDebtQuery.findOutstandingSalesDebt(customerId)` y
   `CustomerDebtQueryJdbc` derivan el pendiente desde líneas y pagos agregados por venta. El puerto
   no incluye el balance general del cliente ni modifica historial. La futura sustitución del mapa
   `saleDebts` debe mantener `customer.balance - deuda de ventas` como deuda general y coordinar
   ajustes de balance con la venta bajo la misma unidad de trabajo.
9. **Contrato de snapshot y saldo.** La implementación en memoria de
   `CustomerRepositoryInterface.update` conserva el reemplazo del snapshot completo. En JDBC, la
   operación solo actualiza nombre, teléfono y dirección con `WHERE id = ? AND balance = ?`; nunca
   escribe el saldo. Si el cliente desapareció se lanza `IllegalArgumentException`, y si el saldo
   cambió desde la lectura se rechaza con `ConcurrentModificationException`. Por tanto, una copia
   antigua no puede restaurar un saldo después de un abono. `CustomerBalanceAdjusterJdbc` es la única
   vía de escritura financiera JDBC: incrementa mediante suma SQL y reduce con una condición atómica
   que impide saldos negativos.
   `CustomerService` aún actualiza snapshots al cambiar deuda y al compensar fallos; no debe
   conectarse al adaptador JDBC hasta adaptar esas rutas para usar el ajustador y compartir una
   transacción JDBC, sustituyendo compensaciones manuales por rollback. La ruta en memoria no cambia.
   La regresión simulada cubre la guarda y el rollback. También se añadió el escenario de integración
   PostgreSQL, que no se ejecutó en esta intervención porque faltaban las variables del entorno
   aislado de pruebas.
10. **Coordinación con los DAO de Jesús.** Cuando estén disponibles, confirmar firmas de consulta
   y bloqueo, su uso del proveedor compartido y contratos de rollback. Después integrar bajo el
   límite de servicio, añadir pruebas de concurrencia/integración en la base exclusiva de pruebas
   y revisar el cambio con el equipo antes de conectar `Main` a PostgreSQL.

En la rama de integración no se conectó `SaleService` ni se cambiaron las operaciones de deuda de
`CustomerService`; siguen en memoria. `InventoryService` se amplió para registrar entradas JDBC
con el ajustador de inventario y la misma transacción, y rechaza su flujo antiguo de compensación
para descuentos JDBC. `InventoryRepositoryJdbc.save` ahora es insert-only; no se modificaron ni
publicaron las ramas de los DAO de Jesús. La integración requiere revisión del equipo y validación
con PostgreSQL real antes de habilitar el modo PostgreSQL para uso del equipo.

## Pruebas y limitaciones

- Las pruebas unitarias de modelo no requieren PostgreSQL.
- `SaleRepositoryJdbcTest` usa un controlador JDBC simulado para comprobar clave idempotente,
  fechas de reintento, claves distintas con igual importe/fecha, importes inválidos, saldo,
  preservación histórica, rechazo de snapshots, participación transaccional y rollback SQL. No es
  una prueba PostgreSQL y no demuestra bloqueos reales ni restricciones del servidor.
- `CustomerDebtQueryJdbcTest` usa JDBC simulado para comprobar cliente sin ventas, ventas totalmente
  pagadas y parciales, varios pagos/líneas sin multiplicación, exclusión de saldo general,
  el sobrepago en una venta sin compensación sobre otra venta pendiente, validación de ID,
  traducción de errores SQL y reutilización de conexión transaccional.
- `CustomerDebtQueryJdbcPostgresIntegrationTest` inserta fixtures aleatorios para ventas del cliente
  consultado, incluida una venta sobrepagada mediante SQL directo para representar datos históricos
  inconsistentes, una venta de otro cliente y un cliente sin ventas. Comprueba que el sobrepago no
  reduce el pendiente de las otras ventas y que líneas/pagos no cambian. La limpieza se limita a sus
  propios IDs. Se omite si falta la URL segura.
- `SaleRepositoryJdbcTest` y `SaleRepositoryJdbcPostgresIntegrationTest` mantienen la comprobación
  preventiva de `SalePaymentAppender`: un abono normal que supera el saldo se rechaza.
- Revisión del riesgo de sobrepago (2026-11-16): el SQL ahora suma `GREATEST(total - pagos, 0)`
  por venta antes de agregar por cliente. La prueba simulada deja una venta de 50 pagada por 80 junto
  a otra con total 200 y pagos 75; el resultado permanece en 125, no en 95. La prueba PostgreSQL
  incorpora el mismo historial inconsistente con un fixture aleatorio insertado directamente.
- Ejecución dirigida de esta revisión con Java 17.0.21 y Maven 3.10.0: 25 pruebas contabilizadas,
  23 aprobadas, 0 fallidas, 0 errores y 2 omitidas. Las omitidas son las integraciones PostgreSQL
  de ventas y cartera: `LAEXCLUSIVA_TEST_DB_URL`, `LAEXCLUSIVA_DB_USER` y
  `LAEXCLUSIVA_DB_PASSWORD` no estaban configuradas en el proceso.
- `mvn clean verify` con Java 17.0.21 terminó en `BUILD SUCCESS`: 164 pruebas contabilizadas,
  162 aprobadas, 0 fallidas, 0 errores y 2 omitidas por falta de la configuración PostgreSQL.
  El JAR se generó correctamente. No se conectó a una base ni se ejecutaron migraciones.
- Tras integrar el merge de PR #13 (`0a9f72f`) en la rama JDBC, `mvn clean verify` con Java
  17.0.21 y Maven 3.10.0 terminó en `BUILD SUCCESS`: 181 pruebas, 179 aprobadas, 0 fallidas,
  0 errores y 2 omitidas. Las dos omitidas son las pruebas PostgreSQL de ventas y cartera; las
  variables `LAEXCLUSIVA_TEST_DB_URL`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD` no estaban
  configuradas en este proceso. El JAR se generó y no se conectó ni modificó ninguna base.
- Se conservó la validación de `SalePaymentAppender`; su prueba simulada rechaza los abonos mayores
  al saldo. Las advertencias observadas fueron las 6 advertencias de modelo efectivo de JavaFX y el
  aviso de configuración JavaFX no modular durante `AuthenticationServiceTest`.
- Ejecución dirigida del 2026-11-16 con Java 17.0.21 y Maven 3.10.0: 6 pruebas contabilizadas,
  5 simuladas aprobadas, 0 fallidas/errores y 1 prueba PostgreSQL omitida porque no están
  configuradas las variables de conexión en el proceso.
- Verificación completa del 2026-11-16: `mvn clean verify` con Java 17.0.21 terminó en
  `BUILD SUCCESS`: 163 pruebas contabilizadas, 161 aprobadas, 0 fallidas, 0 errores y 2 omitidas.
  Las dos omitidas son las pruebas PostgreSQL opcionales de ventas y cartera, porque las variables
  de conexión no estaban disponibles en este proceso. No hubo conexión ni cambios en bases de datos.
- Validación definitiva del 2026-11-16 a las 13:08 (Colombia), ejecutada por Carlos con Java 17.0.18
  y Maven 3.10.0 contra exclusivamente `la_exclusiva_test`: `mvn clean verify` terminó en
  `BUILD SUCCESS`, con 163 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas. Los reportes
  Surefire locales registran 1 prueba PostgreSQL aprobada en `CustomerDebtQueryJdbcPostgresIntegrationTest`
  y 1 en `SaleRepositoryJdbcPostgresIntegrationTest`; las 161 restantes también pasaron. El JAR
  `target/la-exclusiva-p3-1.0-SNAPSHOT.jar` fue generado. Codex verificó los reportes y sus fechas;
  no leyó credenciales ni repitió la ejecución.
- Maven reportó 6 advertencias de modelo efectivo para cada dependencia JavaFX y el aviso de
  configuración no modular durante `AuthenticationServiceTest`; no afectaron la compilación ni las
  pruebas.
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
  en esa revisión inicial estas nuevas aserciones todavía necesitaban ejecución real.
- Validación definitiva del 2026-11-13 a las 12:13 (Colombia), ejecutada por Carlos en PowerShell
  con Microsoft JDK 17.0.18 y `C:\apache-maven-3.10.0\bin\mvn.cmd clean verify`, usando únicamente
  `jdbc:postgresql://localhost:5432/la_exclusiva_test`: `BUILD SUCCESS`, 157 pruebas, 157
  aprobadas, 0 fallidas, 0 errores y 0 omitidas. Surefire confirma 1 prueba PostgreSQL real
  aprobada y 156 pruebas unitarias/simuladas aprobadas. El JAR también fue generado. Esta ejecución
  posterior incluye las aserciones ampliadas en `b54db35`, por lo que cierra la validación pendiente.
- Maven advirtió 6 problemas de modelo efectivo en cada dependencia JavaFX; las pruebas también
  mostraron el aviso de configuración no modular de JavaFX durante `AuthenticationServiceTest`.
  No hubo fallos de compilación y la verificación completa pasó.
- La integración real solo podrá conectarse mediante `LAEXCLUSIVA_TEST_DB_URL`, validada por
  `DatabaseConfig.fromTestEnvironment()` para que apunte a `la_exclusiva_test`.
- No se ejecutan migraciones desde estas pruebas. La base aislada debe tener V001 y V002 aplicadas
  previamente por una persona que haya verificado el destino `la_exclusiva_test`.
- `SaleRepositoryJdbcPostgresIntegrationTest` se omite si falta la URL dedicada. La última
  ejecución confirmada la corrió contra `la_exclusiva_test` y pasó con las aserciones ampliadas.
- No se escriben credenciales ni se limpia ninguna tabla compartida.
- En `feature/customers-sellers-jdbc`, `CustomerSellerJdbcTest` simula altas, búsquedas, listados,
  actualización, IDs duplicados, errores SQL, ajustes de saldo, rechazo de saldo negativo,
  rollback y dos abonos concurrentes. Estas verificaciones simuladas no prueban bloqueos reales de
  PostgreSQL. `CustomerSellerJdbcPostgresIntegrationTest` usa IDs aleatorios propios y borra solo
  los registros que insertó; comprueba CRUD, duplicados, rollback y dos conexiones concurrentes.
- Validación de `feature/customers-sellers-jdbc` (2026-11-16, JDK Temurin 17.0.21 y Maven 3.10.0):
  la ejecución dirigida contabilizó 9 pruebas, con 8 simuladas aprobadas, 0 fallidas/errores y
  1 integración PostgreSQL omitida. `mvn clean verify` terminó en `BUILD SUCCESS`: 190 pruebas,
  187 aprobadas, 0 fallidas, 0 errores y 3 omitidas. Las tres omitidas son las integraciones
  PostgreSQL de ventas, cartera y clientes/vendedores; las variables de conexión no estaban
  configuradas en el proceso. No se conectó a PostgreSQL ni se ejecutaron migraciones. El JAR se
  generó correctamente.
- La compilación mostró las advertencias preexistentes de modelo efectivo para JavaFX; la prueba
  JavaFX informó que las clases se cargaron desde un módulo no nombrado y produjo mensajes de error
  simulado de consulta dentro de `AuthenticationServiceTest`. No afectaron el resultado de la suite.
- Validación definitiva informada por Carlos el 2026-11-16 a las 22:45 (Colombia): ejecutó
  `mvn clean verify` con Java 17 contra exclusivamente `la_exclusiva_test`. `BUILD SUCCESS`, 190
  pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas. Codex inspeccionó los reportes Surefire:
  22 suites suman 190, y cada una de `CustomerSellerJdbcPostgresIntegrationTest`,
  `CustomerDebtQueryJdbcPostgresIntegrationTest` y `SaleRepositoryJdbcPostgresIntegrationTest`
  registra 1 aprobada y 0 omitidas, con marcas de las 22:45. Las otras 187 pruebas también
  pasaron. Esta evidencia reemplaza los resultados anteriores de esta rama en los que las
  integraciones opcionales se omitieron. Codex no repitió la ejecución ni abrió una conexión; no se
  registraron credenciales ni se ejecutaron migraciones.

### Ejecutar las integraciones PostgreSQL desde PowerShell

Configurar previamente `LAEXCLUSIVA_TEST_DB_URL`, `LAEXCLUSIVA_DB_USER` y
`LAEXCLUSIVA_DB_PASSWORD` en la sesión privada de PowerShell. La URL debe usar exactamente la base
`la_exclusiva_test`; no mostrar ni copiar la contraseña a logs. Este comando verifica el destino y
ejecuta únicamente las tres pruebas de integración, sin migrar el esquema:

```powershell
if ($env:LAEXCLUSIVA_TEST_DB_URL -notmatch '^jdbc:postgresql://[^/]+/la_exclusiva_test(?:\?.*)?$') {
    throw 'LAEXCLUSIVA_TEST_DB_URL debe apuntar exclusivamente a la_exclusiva_test.'
}
if ([string]::IsNullOrWhiteSpace($env:LAEXCLUSIVA_DB_USER) -or
    [string]::IsNullOrWhiteSpace($env:LAEXCLUSIVA_DB_PASSWORD)) {
    throw 'Configura las credenciales de prueba en esta sesión sin imprimirlas.'
}
$javaVersion = java -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "17\.') { throw 'Se requiere Java 17.' }
& 'C:\apache-maven-3.10.0\bin\mvn.cmd' `
    '-Dtest=CustomerDebtQueryJdbcPostgresIntegrationTest,CustomerSellerJdbcPostgresIntegrationTest,SaleRepositoryJdbcPostgresIntegrationTest' test
if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas PostgreSQL.' }
```

La tabla `sale_line` conserva únicamente el precio histórico de la presentación vendida. El
producto asociado a una línea reconstituida no ofrece un snapshot histórico de los otros precios
del catálogo; para cálculos históricos se debe usar `SaleLine.getUnitPrice()`.

## Integración JavaFX y flujo JDBC de extremo a extremo (2026-11-17)

La rama `feature/full-jdbc-integration` implementa `SalesWorkflow`, con adaptadores en memoria y
`JdbcSalesWorkflow`. Este último coordina en una sola transacción el inventario, movimientos,
venta/cabecera, líneas con precio y capacidad históricos, pago inicial y saldo agregado del cliente.
Las filas de inventario se descuentan en orden ascendente por ID mediante `InventoryStockAdjusterJdbc`;
el SQL impide resultados negativos. Si falla la persistencia de la venta o cualquier operación
posterior, PostgreSQL revierte toda la transacción sin borrar registros ni restaurar snapshots.

Para pagos, el adaptador JDBC de `SaleRepositoryJdbc` devuelve si `appendPaymentOnce` insertó una
clave o si respondió a un reintento. Solo una clave nueva reduce `customer.balance`; repetir la misma
clave e importe no duplica `sale_payment` ni vuelve a bajar el saldo. La deuda de ventas se consulta
desde `sale_line` y `sale_payment`; la diferencia respecto al saldo del cliente sigue siendo deuda
general y no se convierte en cartera de ventas.

JavaFX ofrece configuración explícita `LAEXCLUSIVA_PERSISTENCE_MODE=memory|postgres` y mantiene
`memory` como valor por defecto. La pantalla de inventario consulta catálogo/existencias/movimientos
y permite registrar productos con tres precios y entradas de stock según permisos. La pantalla de
ventas permite unidad, media canasta o canasta completa, selección de cliente/vendedor y pago inicial.
La cartera permite elegir una venta pendiente y registrar un abono con clave conservada para reintento.
Clientes y vendedores pasan por los repositorios seleccionados en la composición.

### Límites y acciones previas a producción

- `App` conserva el login en `InMemoryUserRepository`; esta fase no persiste cuentas ni asignaciones
  de vendedores en `user_account`.
- La pantalla de ventas requiere `REGISTER_SALE` y `VIEW_SALES`; el trabajador solo consulta su
  propio historial y solo selecciona su vendedor asignado. Si una cuenta de trabajador no tiene
  `sellerId`, el selector queda vacío y no puede registrar ventas. Las cuentas demo actuales se
  crean sin vendedor asignado, por lo que esa asignación debe resolverse antes de usar esa cuenta.
- El catálogo y las entradas de inventario se controlan con `MANAGE_PRODUCTS` y
  `MANAGE_INVENTORY_ENTRIES` por separado.
- Las operaciones generales `CustomerService.addDebtToCustomer` y
  `applyPaymentToCustomer` aún usan el mapa `saleDebts`/snapshots. El modo PostgreSQL no las invoca;
  para deudas generales muestra el cálculo `balance agregado - deuda de ventas`, pero no ofrece un
  pago general.
- `SaleService` no se conectó a JDBC y conserva su estrategia de compensación en memoria. La ruta
  JavaFX PostgreSQL usa `JdbcSalesWorkflow`. `InventoryService.discountSale` y `rollbackDiscount`
  rechazan su uso en modo JDBC; así se evita mezclar la unidad transaccional con compensaciones.
- La creación de venta no tiene clave idempotente propia. La idempotencia durable cubre pagos, no
  reintentos de registro de venta con respuesta incierta.
- No se aplican V001/V002 ni se inicializa el catálogo al arrancar. La aplicación no escribe en la
  base hasta una acción del usuario; la pantalla de administración puede registrar productos.
- El PR #15 de infraestructura se fusionó en `develop` (`ea85865`) y el PR #16 de
  productos/inventario continúa abierto según el estado consultado el 2026-11-17. La rama de
  integración conserva el endurecimiento `2676023` y protege los ajustes de inventario contra
  snapshots; Jesús debe revisar esa corrección en #16. El borrador depende de #16 y no sustituye su
  entrega.

### Verificación local observada

Con Temurin JDK 17.0.21 y Maven 3.10.0, después de integrar `origin/develop` (`ea85865`) mediante
el merge `ecadf8b`, el 2026-11-17 `mvn clean verify` terminó en `BUILD SUCCESS`: 222 pruebas
contabilizadas, 210 aprobadas, 0 fallidas, 0 errores y 12 omitidas.
Las 12 omitidas son las pruebas PostgreSQL opcionales; el proceso no tenía
`LAEXCLUSIVA_TEST_DB_URL`, `LAEXCLUSIVA_DB_USER` ni
`LAEXCLUSIVA_DB_PASSWORD`. No se abrió conexión, se aplicaron migraciones ni se tocaron bases de
datos. Las pruebas aprobadas incluyeron carga e interacción FXML, alta simulada de catálogo, ajustes
de entradas con puertos simulados, el workflow con repositorios simulados y las suites anteriores.
Maven informó las advertencias de modelo JavaFX existentes y JavaFX mostró el aviso no modular
durante sus pruebas. Se omitieron las suites PostgreSQL de cartera, clientes/vendedores, inventario
(3 casos), ventas coordinadas, productos (3 casos), ventas JDBC y movimientos de inventario (2 casos).

La navegación JavaFX también aplica `REGISTER_SALE` junto con `VIEW_SALES`, limita las ventas de
trabajadores al vendedor asignado, deja el selector vacío cuando no hay asignación y separa
`MANAGE_PRODUCTS` de `MANAGE_INVENTORY_ENTRIES`. La prueba de interfaz de trabajador asignado pasó
dentro de las 210 pruebas aprobadas. También pasaron las regresiones de cierre de sesión y navegación
incorporadas por el PR #17; se combinaron con la composición de persistencia y las pantallas de ventas
e inventario.

La prueba PostgreSQL nueva `JdbcSalesWorkflowPostgresIntegrationTest` utiliza únicamente la
configuración validada por `DatabaseConfig.fromTestEnvironment()`, que exige la base
`la_exclusiva_test`. Genera IDs aleatorios, verifica que no existan antes de crear fixtures y limpia
ventas y filas relacionadas por el vendedor único de la prueba, más el producto/cliente/vendedor
con IDs exactos propios. La prueba cubre rollback, saldo general, pagos idempotentes y carreras reales
de abonos y stock. No pudo ejecutarse sin las variables dedicadas; no debe citarse como aprobada.

Después de configurar la base aislada con V001 y V002 aplicadas, ejecutar desde PowerShell:

```powershell
$javaVersion = java -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "17\.') { throw 'Se requiere Java 17.' }
if ($env:LAEXCLUSIVA_TEST_DB_URL -notmatch '^jdbc:postgresql://[^/]+/la_exclusiva_test(?:\?.*)?$') {
    throw 'La URL de prueba debe apuntar exclusivamente a la_exclusiva_test.'
}
if ([string]::IsNullOrWhiteSpace($env:LAEXCLUSIVA_DB_USER) -or
    [string]::IsNullOrWhiteSpace($env:LAEXCLUSIVA_DB_PASSWORD)) {
    throw 'Configura las credenciales de prueba en esta sesión sin imprimirlas.'
}
& 'C:\apache-maven-3.10.0\bin\mvn.cmd' `
    '-Dtest=JdbcSalesWorkflowPostgresIntegrationTest,InventoryRepositoryJdbcPostgresIntegrationTest,StockMovementRepositoryJdbcPostgresIntegrationTest,ProductRepositoryJdbcPostgresIntegrationTest,CustomerSellerJdbcPostgresIntegrationTest,CustomerDebtQueryJdbcPostgresIntegrationTest,SaleRepositoryJdbcPostgresIntegrationTest' test
if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas PostgreSQL.' }
```
