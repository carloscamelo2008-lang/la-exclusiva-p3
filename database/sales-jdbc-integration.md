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
- Al 2026-11-18, `origin/develop` apunta a `e92d874` y contiene, según la comprobación de ancestry,
  el tip `dbbaf28` de productos/inventario. La interfaz del PR #16 inicialmente lo mostraba abierto;
  después se confirmó en el timeline el merge `e92d874` hacia `develop`. El merge local `0936ad8`
  integró `origin/develop` en esta rama sin conflictos. PR #18 usa `develop` como base.

### Verificación local observada

Con Temurin JDK 17.0.21 y Maven 3.10.0, después de integrar `origin/develop` (`ea85865`) mediante
`ecadf8b` y el avance de `origin/feature/products-inventory` hasta `dbbaf28` mediante el merge
`c2ab247`, el 2026-11-17 `mvn clean verify` terminó en `BUILD SUCCESS`: 222 pruebas
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
de abonos y stock. En la revisión inicial del PR #18 quedó omitida por falta de variables; el registro
de validación definitiva más abajo consigna su ejecución real aprobada.

El último merge de la dependencia añadió la exigencia de transacción activa a
`InventoryRepositoryJdbc.save`. Se mantuvo `save` como alta inicial insert-only; para cambios de
stock, `JdbcSalesWorkflow` usa `InventoryStockAdjusterJdbc` por delta y bajo la transacción común,
sin volver a guardar snapshots. Las regresiones PostgreSQL verifican el guardado solo dentro de
transacción, el rechazo de un snapshot obsoleto y la concurrencia de ajustes; esos casos quedaron
entre las 3 omisiones de `InventoryRepositoryJdbcPostgresIntegrationTest` en la ejecución anterior
que no tenía configuración dedicada.

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

### Revisión final del PR #18 (2026-11-18)

Tras integrar `origin/develop` (`e92d874`) con el merge normal `0936ad8`, la revisión de
`JdbcSalesWorkflow`, `ApplicationServices`, `InventoryStockAdjusterJdbc`, los repositorios JDBC,
sus pruebas y las pantallas JavaFX no encontró un defecto reproducible dentro del alcance que
requiriera cambiar código. La rama quedó sin conflictos de merge local. El historial de
`origin/develop` contiene el tip `dbbaf28` de inventario. La interfaz de GitHub inicialmente mostró
el PR #16 abierto; luego se confirmó su merge mediante `e92d874`. No se modificó ninguna rama ni PR
de compañeros.

Con Temurin JDK 17.0.21 y Maven 3.10.0, `mvn clean verify` terminó en `BUILD SUCCESS`: 222 pruebas,
210 aprobadas, 0 fallidas, 0 errores y 12 omitidas. Las 12 omitidas fueron exclusivamente suites
PostgreSQL: cartera (1), clientes/vendedores (1), inventario (3), workflow de ventas (1), productos
(3), ventas (1) y movimientos (2). Las variables PostgreSQL de prueba no estaban presentes, por
lo que no se abrió conexión, no se ejecutaron migraciones ni se escribieron datos. No se afirma
validación real de atomicidad, bloqueos ni concurrencia en PostgreSQL. Las pruebas JavaFX corrieron
bajo Windows; Xvfb no era necesario. `git diff --check` pasó después de actualizar la documentación.

La revisión estática confirma que el workflow comparte proveedor y coordinador transaccional,
aplica ajustes de stock por delta, persiste pagos idempotentes y construye ventas con snapshots
históricos; las consultas de deuda separan cartera de ventas de deuda general. Los tests simulados
y de JavaFX aprobaron. No se realizó un recorrido manual de extremo a extremo con autenticación y
PostgreSQL, así que ese flujo queda pendiente de validación humana. También siguen pendientes el
login/cuentas/asignación de vendedor persistentes, el pago JDBC de deuda general, una clave
idempotente para crear ventas y la unificación del `SaleService` tradicional con
`JdbcSalesWorkflow`.

### Validación PostgreSQL definitiva del PR #18 (2026-11-18)

La comprobación se ejecutó con Microsoft JDK 17.0.18, Maven 3.10.0 y PostgreSQL 18.6. La conexión
de solo lectura confirmó `current_database() = la_exclusiva_test`, las diez tablas esperadas de V001,
la columna `sale_payment.idempotency_key` y su restricción única de V002. No se ejecutaron
migraciones. El proceso Maven recibió únicamente la URL de `la_exclusiva_test`; no se conectó a
`la_exclusiva` ni se modificó la base de desarrollo.

La primera ejecución de `mvn clean verify` ejecutó las 222 pruebas, con 0 fallos de aserción,
2 errores y 0 omisiones. Los errores fueron dos casos de concurrencia en
`InventoryRepositoryJdbcPostgresIntegrationTest`: tras adquirir `FOR UPDATE`, el segundo hilo
guardaba un snapshot existente con `InventoryRepositoryJdbc.save`, operación insert-only.
Actualicé solo esa prueba para aplicar el descuento concurrente mediante `InventoryStockAdjusterJdbc`.
La suite enfocada pasó 3/3 contra PostgreSQL; no se modificó código de producción.

La segunda ejecución completa de `mvn clean verify` terminó en `BUILD SUCCESS`: 222 pruebas,
0 fallidas, 0 errores y 0 omitidas. Los reportes Surefire confirman 12/12 integraciones PostgreSQL
reales aprobadas: cartera (1), clientes/vendedores (1), inventario (3), workflow transaccional de
ventas (1), productos/precios (3), ventas/pagos (1) y movimientos (2). Se probaron rollback,
idempotencia, saldos/cartera, persistencia histórica y transacciones, además de bloqueos concurrentes
reales para abonos y stock. La consulta de cartera preservó importes por venta y el flujo coordinado
conservó la consistencia de saldo, inventario y movimientos. Las pruebas usaron fixtures propios y
limpieza por IDs generados; no se ejecutaron `TRUNCATE`, `DROP` ni limpiezas generales. Se generó el
JAR.

Maven informó seis problemas del modelo efectivo para cada artefacto `javafx-controls` y
`javafx-fxml`; JavaFX registró el aviso conocido de módulos no nombrados y advertencias esperadas
de pruebas simuladas. Windows ejecutó las pruebas FXML,
por lo que Xvfb no fue necesario. `git diff --check` se ejecutó después de actualizar la documentación.
Queda pendiente el recorrido manual autenticado completo y la revisión humana del PR; también siguen
los límites de autenticación/asignación en memoria, pago JDBC de deuda general, idempotencia de alta
de ventas y separación del `SaleService` tradicional.

### Validación funcional final JavaFX + PostgreSQL (2026-11-18)

La ejecución manual se realizó en Windows con Microsoft JDK 17.0.18, Maven 3.10.0 y PostgreSQL 18.6.
`LAEXCLUSIVA_PERSISTENCE_MODE=postgres` y `LAEXCLUSIVA_DB_URL` apuntaron exclusivamente a
`jdbc:postgresql://localhost:5432/la_exclusiva_test`. La contraseña se recuperó con DPAPI dentro
del proceso PowerShell y nunca se imprimió ni se escribió en el repositorio. La comprobación de solo
lectura confirmó `current_database() = la_exclusiva_test`, las diez tablas de V001 y la columna y
restricción de clave idempotente de V002. No se ejecutaron migraciones ni se conectó a
`la_exclusiva`.

Con el administrador de demostración se completaron en la interfaz el alta del vendedor
`SEL-45E73A06`, cliente `E2E-CLI-PR18-20261118-01` y producto
`E2E-PR18-PROD-20261118-01` (unidad $2.500, media canasta $14.000, canasta $26.000; 12 unidades
por canasta). Se registró una entrada ficticia de 24 unidades. Una media canasta descontó 6,
dejando 18; el inventario mostró la entrada `ENTRY 24` y la salida `EXIT 6` asociada a la venta.
La venta `SALE-74106632-192E-4583-B1A8-E5C86EAC16C3` quedó por $14.000, con pago inicial de
$4.000. Un abono posterior de $3.000 dejó dos pagos persistidos por $7.000 y saldo de venta y
cartera de $7.000; la deuda general del cliente permaneció en $0.

Se rechazó un intento de venta por 24 unidades cuando quedaban 18, sin generar otra venta, pago ni
movimiento y conservando el stock de 18. También se rechazó un pago de $10.001 cuando el saldo era
$10.000; el rechazo no generó un pago adicional. Se rechazaron un producto con precio unitario cero
y una entrada de inventario de -1 canastas. El estado final de PostgreSQL confirma solo los registros
válidos descritos arriba. Estos fixtures ficticios permanecen en `la_exclusiva_test` y no se
eliminaron.

Después de cerrar sesión, cerrar el proceso y reiniciar la aplicación, el administrador volvió a
iniciar sesión y las pantallas de clientes, vendedores, catálogo/inventario, ventas y cartera
mostraron la información persistida. El administrador vio las secciones de administración; el
trabajador no vio vendedores ni gestión de usuarios. La cuenta de demostración del trabajador no
tiene vendedor asignado: la pantalla de ventas lo informó y no permitió registrar una venta. Las
cuentas de demostración y su asignación se inicializan en memoria al arrancar; no son parte de la
persistencia PostgreSQL. Gestión de usuarios continúa deshabilitada.

La prueba visual detectó y corrigió dos textos: el selector de presentaciones mostraba el nombre
interno de la clase Java en lugar de una etiqueta española, y el subtítulo de cartera decía que el
registro de abonos estaba pendiente aunque ya estaba disponible. Se añadió una regresión JavaFX
para Unidad, Media canasta, Canasta completa y el texto de cartera.

Con JDK 17.0.18, `mvn clean verify` terminó en `BUILD SUCCESS`: 223 pruebas, 0 fallidas, 0 errores y
0 omitidas. Surefire confirma 12 pruebas PostgreSQL reales aprobadas en
`CustomerDebtQueryJdbcPostgresIntegrationTest` (1), `CustomerSellerJdbcPostgresIntegrationTest`
(1), `InventoryRepositoryJdbcPostgresIntegrationTest` (3), `JdbcSalesWorkflowPostgresIntegrationTest`
(1), `ProductRepositoryJdbcPostgresIntegrationTest` (3), `SaleRepositoryJdbcPostgresIntegrationTest`
(1) y `StockMovementRepositoryJdbcPostgresIntegrationTest` (2). La regresión dirigida de las
etiquetas JavaFX pasó 1/1. `git diff --check` pasó antes de registrar el cambio.

Advertencias observadas: Maven mostró avisos existentes del modelo efectivo para JavaFX y JavaFX
registró su aviso de clases en un módulo no nombrado. Las trazas de errores simulados de consultas
en `AuthenticationServiceTest` pertenecen a casos que aprobaron. No se ejecutaron operaciones
destructivas, no se usó la base de desarrollo y no se modificó el esquema.

### Correcciones a observaciones de revisión del PR #18 (2026-11-18)

Los controladores JavaFX reciben ahora `UserSession` como fuente de autorización y consultan
`AuthorizationService` en cada escritura: ventas, abonos, altas de productos, entradas de inventario,
clientes y vendedores. Ocultar o deshabilitar controles es solo una ayuda visual. Si la sesión expiró,
la cuenta se desactivó o el permiso falta, el servicio de escritura no se invoca.

En Ventas, un trabajador con `sellerId` válido ve seleccionado automáticamente ese vendedor y el
selector permanece bloqueado. Si no tiene asignación o el ID no corresponde a un vendedor registrado,
la pantalla lo informa y el controlador rechaza el registro. La autorización del vendedor se vuelve a
comprobar al enviar la venta. El administrador conserva la lista completa y la selección manual.

La confirmación de alta de venta se separa de las consultas posteriores. El carrito se limpia solo si
`registerSale` retorna una venta; si la llamada falla con resultado incierto, se conserva y se recomienda
consultar el historial antes de un nuevo intento, ya que el alta todavía no tiene clave idempotente.
Un fallo al actualizar clientes o historial conserva el mensaje de venta confirmada y ofrece
«Actualizar historial», acción que solo consulta.

En Cartera, un abono confirmado no se informa como fallido si la actualización visual no responde. La
clave y los datos del intento se conservan ante un resultado incierto; el usuario solo puede reintentar
la misma venta con el mismo importe hasta resolverlo, usando la misma clave. Al confirmar el flujo,
se libera el intento antes de refrescar y se muestra el resultado confirmado aunque falle la consulta.
«Actualizar cartera» vuelve a consultar clientes, saldos y ventas sin volver a registrar un abono.
Una consulta incompleta limpia las tablas, selección y saldos mostrados y los identifica como no
disponibles. No se alteró `JdbcSalesWorkflow` ni su unidad transaccional.

Las regresiones JavaFX cubren: abono confirmado con error al refrescar y un único pago; resultado
incierto que reusa la clave y bloquea cambios de operación; error de consulta con limpieza y reintento;
venta confirmada con error del historial sin repetir el alta; escrituras rechazadas antes de llegar a
los servicios por sesión/permisos; y rechazo de una venta bajo un vendedor distinto al asignado. La
prueba existente de asignación también verifica selección automática, asignación ausente/desconocida
y el selector del administrador.

La validación se ejecutó el 2026-11-18 con Microsoft JDK 17.0.18, Maven 3.10.0 y PostgreSQL 18.6.
Una consulta de solo lectura confirmó `la_exclusiva_test`, las diez tablas V001 y la columna/restricción
V002. La contraseña se recuperó localmente mediante DPAPI y no se mostró ni guardó. `mvn clean verify`
terminó en `BUILD SUCCESS`: 229 pruebas, 0 fallidas, 0 errores y 0 omitidas; las 12 pruebas
PostgreSQL reales aprobaron (cartera 1, clientes/vendedores 1, inventario 3, flujo de ventas 1,
productos/precios 3, ventas/pagos 1 y movimientos 2). La suite dirigida de regresiones y autenticación
pasó 38/38. No hubo migraciones ni operaciones de esquema; cada fixture automatizado usa IDs propios y
limpieza limitada a sus registros. No se realizó un recorrido manual adicional de escritorio en esta
intervención; los handlers y FXML afectados sí se ejercitaron en JavaFX sobre Windows.

Los commits de código y pruebas son `f96747d` (`fix(ui): make JavaFX writes session-aware and
refresh-safe`) y `5ed54e6` (`test(ui): cover controller write and refresh regressions`). Se documenta
esta intervención también en `docs/ai-usage/leader-ai-log.md`. Sigue pendiente la revisión humana de
Daniel y Jesús. La alta de ventas aún no es idempotente; ante una respuesta incierta, el usuario debe
consultar el historial antes de volver a iniciar una alta.

### Revalidación después de integrar el PR #19 (2026-11-18)

La rama `feature/full-jdbc-integration` incorporó `origin/develop` en
`be9ebda27ce6327fb3374f865eaaffc40d206d08` mediante el merge normal
`4a8b4ad83313b5561342f509e045c504cc542a17`. El merge no tuvo conflictos y conservó el registro de
actividad de Daniel integrado desde `develop`, junto con las correcciones JavaFX/JDBC de esta rama.
Los commits `f96747d`, `5ed54e6` y `3adf6f9` siguen siendo ancestros de HEAD.

Tras el merge, Microsoft JDK 17.0.18 y Maven 3.10.0 ejecutaron nuevamente `mvn clean verify` el
2026-11-18. Resultado observado: `BUILD SUCCESS`, 229 pruebas, 0 fallidas, 0 errores y 0 omitidas;
Surefire generó 30 reportes. Las 12 pruebas PostgreSQL reales pasaron en `la_exclusiva_test`:
cartera (1), clientes/vendedores (1), inventario (3), flujo de ventas (1), productos (3),
ventas/pagos (1) y movimientos (2). La consulta de solo lectura previa confirmó PostgreSQL 18.6,
las diez tablas de V001 y la columna/restricción idempotente de V002. No se ejecutaron migraciones,
cambios de esquema ni operaciones destructivas y no se usó `la_exclusiva`. La contraseña se obtuvo
con DPAPI solo dentro del proceso local y no se imprimió ni se escribió en el repositorio.

`git diff --check` pasó. Maven mostró advertencias del modelo efectivo de JavaFX; JavaFX avisó que
las clases se cargaron desde un módulo no nombrado. Las trazas de errores simulados en pruebas de
controladores fueron esperadas y esas pruebas aprobaron. El PR #18 permanece sin fusionar y queda
pendiente que Daniel y Jesús completen su segunda revisión.

## Correcciones finales de revisión de Jesús (2026-11-18)

### Entrada de inventario y refresco JavaFX

`InventoryController.handleAddStock` ahora separa validación local, llamada de escritura confirmada
y consulta visual. Cuando `addStock` retorna, el controlador limpia los campos de entrada y conserva
el mensaje de éxito; un fallo posterior de `refresh()` se muestra como advertencia aparte. El botón
«Actualizar inventario» vuelve a consultar catálogo, existencias y movimientos y no invoca ninguna
escritura. Ante una excepción durante la llamada de escritura, la interfaz indica que el resultado no
se pudo confirmar y pide verificar los datos con esa consulta antes de intentar otra entrada; no
afirma que la operación haya fallado.

Se aplicó la misma separación a `handleRegisterProduct`: tras un retorno normal se limpian los
campos y se conserva el éxito, aunque falle el refresco. Si la llamada de registro termina con una
excepción, se recomienda comprobar el catálogo antes de intentar repetirla. Ambos handlers siguen
consultando `AuthorizationService` con la sesión vigente al momento de ejecutar la escritura.

Las pruebas JavaFX `InventoryControllerReviewRegressionTest` simulan entrada de stock confirmada y
error de consulta: el saldo y movimiento se guardan una vez; el mensaje sigue siendo exitoso y un
refresco manual solo lee. Otra regresión cubre el alta de producto confirmada con fallo de consulta,
y la tercera invalida la sesión antes de invocar ambos handlers y confirma que no llegan a los
servicios de escritura.

### Venta a crédito concurrente con abono

Antes de modificar el flujo, una prueba diagnóstica temporal en PostgreSQL reprodujo la observación:
el cliente tenía saldo y deuda de ventas de $100; el abono capturó el snapshot de $100, una venta a
crédito concurrente añadió $20 y confirmó saldo/deuda de $120, y el abono válido de $10 fue rechazado
por comparar la cartera actualizada con el snapshot antiguo. El pago no quedó insertado.

La corrección serializa las operaciones financieras por fila de cliente. `registerSale` bloquea al
cliente (`SELECT ... FOR UPDATE`) antes de consultar la deuda o modificar el saldo. `registerPayment`
obtiene únicamente la clave foránea del cliente de la venta, bloquea esa fila y luego lee el saldo y
anexa el pago. `SaleRepositoryJdbc.appendPaymentOnce` también aplica cliente→venta antes de tomar el
bloqueo de la venta, incluso cuando se invoca dentro de una transacción compartida. Las dos rutas
conservan la comprobación de consistencia, el límite de abonos, la clave idempotente y el rollback de
`JdbcTransactionManager`. Los bloqueos de inventario en ventas siguen ordenados por ID de producto;
no se cambiaron los contratos del modo `memory` ni las migraciones.

La regresión `JdbcSalesWorkflowPostgresIntegrationTest.creditSaleAndPaymentSerializeOnCustomerInBothTransactionOrders`
utiliza dos transacciones reales. Sin depender solo de temporizadores, coordina los hilos con latches,
espera limitada y observación PostgreSQL de `pg_blocking_pids`. Prueba venta primero y abono primero.
En ambos órdenes quedan dos ventas, pagos por $105, deuda de ventas de $95 y saldo agregado de $95;
repetir la misma clave no inserta otro pago. Las pruebas existentes siguen cubriendo abonos excesivos,
reintentos y rollback.

### Validación definitiva

La ejecución final fue el 2026-11-18 con Microsoft JDK 17.0.18, Maven 3.10.0 y PostgreSQL 18.6 en
`la_exclusiva_test`. La inspección de solo lectura confirmó las diez tablas de V001, la columna
`sale_payment.idempotency_key` y la restricción única de V002. `mvn clean verify` terminó en
`BUILD SUCCESS`: **233 pruebas, 0 fallidas, 0 errores y 0 omitidas**. Surefire generó 31 reportes.
Las **13 pruebas PostgreSQL reales** aprobaron en siete clases: cartera (1), clientes/vendedores (1),
inventario (3), workflow de ventas (2), productos (3), repositorio de ventas (1) y movimientos (2).
Las tres pruebas JavaFX nuevas aprobaron; la suite dirigida combinada aprobó 27/27.

No se ejecutaron migraciones, cambios de esquema, `DROP`, `TRUNCATE` ni limpiezas generales. Cada
prueba PostgreSQL generó IDs propios y limpió solo sus fixtures. No se usó la base `la_exclusiva`.
Maven mostró los avisos conocidos del modelo de JavaFX; JavaFX emitió el aviso de módulo no nombrado.
Una ejecución completa intermedia detectó que el fake JDBC de `SaleRepositoryJdbcTest` no reconocía
las consultas nuevas; se amplió el fake y se añadió una aserción del orden de bloqueos. La suite
dirigida y la ejecución completa final pasaron después de ese ajuste. No se hizo un recorrido manual
adicional de escritorio en esta intervención.

## Finanzas y cierre operativo (`feature/carlos-finance-close`, 2026-11-18)

Esta sección registra la ampliación de la rama de Carlos posterior al merge del PR #18. Las notas
anteriores describen el estado de aquella rama en sus respectivas fechas; para venta idempotente,
deuda general y caja, esta sección es el contrato vigente de la nueva rama.

### Creación de ventas con identidad durable

`IdempotentSaleRepository` y `SaleRepositoryJdbc.saveOnce` reservan una clave de hasta 64 caracteres
y la huella SHA-256 de la carga útil en la cabecera `sale`, dentro de la misma transacción que sus
líneas, pago inicial, salidas y movimientos de inventario y delta del saldo. La repetición con la
misma clave y los mismos productos, presentaciones, cantidades, vendedor, cliente y pago inicial
devuelve la venta original sin repetir efectos. Si cambia cualquier dato de negocio, la clave se
rechaza. La huella no contiene precios actuales: las líneas de la venta conservan el precio histórico
tomado al primer alta.

`JdbcSalesWorkflow` comprueba la clave antes de leer el estado mutable de stock/saldo y la vuelve a
comprobar después de obtener el bloqueo del cliente. La restricción única resuelve además la carrera
en solicitudes sin cliente. Inventario, pago inicial, cartera y recibos de caja solo se aplican cuando
la venta se insertó realmente. `SalesController` muestra la referencia y conserva clave y carga útil
si una excepción deja el resultado incierto; permite resolverla repitiendo exactamente esos datos.
En modo `memory`, la deduplicación evita repeticiones concurrentes durante la vida del proceso, pero
no sobrevive a un reinicio.

La migración `V100__sale_creation_idempotency.sql` agrega `sale.idempotency_key` y
`sale.request_fingerprint`, con unicidad y consistencia entre ambas columnas. No hay ejecución
automática desde Maven o la aplicación. Se aplicó manualmente en el esquema ya aislado de
`la_exclusiva_test` después de verificar que faltaban solo esas columnas; antes de desplegar la rama
debe revisarse y aplicarse ordenadamente en el entorno destino.

### Deuda general auditable, separada de ventas

El puerto `CustomerGeneralDebtWorkflow` informa el saldo agregado, la deuda pendiente de ventas y la
diferencia general según el contrato:

`deuda general = saldo agregado del cliente - suma de saldos pendientes de sus ventas`.

`CustomerGeneralDebtWorkflowJdbc` bloquea primero la fila del cliente y conserva el bloqueo hasta el
fin de la transacción compartida. Comprueba que la deuda de ventas no supere el saldo agregado,
reserva o recupera la clave idempotente y cambia `customer.balance` mediante el puerto de delta
atómico. Un pago general se limita al importe general disponible; no inserta `sale_payment` ni
disminuye la deuda de una venta. Si el saldo agregado y la cartera están inconsistentes, el flujo
rechaza cambios y exige corregir los datos de origen. No trata un sobrepago como crédito transferible.

`customer_general_debt_movement` conserva cliente, tipo, importe, fecha/hora, categoría, descripción,
clave y huella. El aumento de deuda se registra junto al incremento del saldo; el pago se registra
junto al decremento del saldo y al recibo `GENERAL_DEBT_PAYMENT` de caja. Las tres escrituras
comparten commit/rollback. Un reintento devuelve el movimiento original y la misma clave con otros
datos se rechaza. La pantalla de Cartera mantiene separados los abonos por venta y deuda general y
permite consultar el historial por cliente, fechas y categoría.

### Caja: facturación frente a efectivo recibido

`CashWorkflow` y `CashMovementRepository` exponen movimientos fechados/categorizados y el resumen.
`cash_movement` clasifica `SALE_INITIAL_PAYMENT`, `SALE_INSTALLMENT`, `GENERAL_DEBT_PAYMENT`, otros
ingresos y egresos (servicios, hielo, trabajadores, retiros y otros). La restricción única del origen
y la clave idempotente evitan contar dos veces el recibo generado por una operación comercial. El
usuario solo puede crear `OTHER_INCOME` y categorías de egreso; recibos de ventas y cartera se insertan
desde la operación de origen, dentro de su transacción, y no se pueden ingresar manualmente otra vez.

El total facturado se calcula directamente desde `sale_line.quantity * unit_price`, con precio
histórico y sin unir pagos a las líneas. El resumen muestra ese importe por separado y calcula el
ingreso real solo desde recibos de ventas, pagos de deuda general y otros ingresos; resta los egresos
para obtener el neto. Así una venta a crédito no se presenta como efectivo recaudado. La pantalla
JavaFX de Caja permite filtrar por fechas y categoría y registrar movimientos con referencia estable;
el administrador dispone de permisos de consulta y escritura. La configuración de caja comprueba
`VIEW_CASH` antes de cargar tablas o resumen, y el handler vuelve a comprobar el permiso de escritura.

`V101__general_debt_ledger.sql` crea el historial de deuda general. `V102__cash_ledger.sql` crea el
libro de caja y sus reglas de clasificación/unicidad. Ambas se aplicaron manualmente, sin limpieza,
después de comprobar que sus tablas no existían en `la_exclusiva_test`. No se ejecutan al arrancar
Maven ni la aplicación. La ruta segura de despliegue es V001, V002, V100, V101 y V102 una sola vez,
tras respaldar/revisar el entorno y comprobar la inexistencia de las tablas/columnas añadidas.

El modo `memory` conserva ventas y permite una demostración de cartera/caja en proceso; sus claves y
libros no son durables. La captura de caja no se conecta aún a entradas de compra/proveedores de
Jesús, y no añade pagos de proveedores ni conciliación bancaria. Usuarios y asignación de vendedor
siguen bajo la autenticación existente, fuera de esta rama. Antes de habilitar PostgreSQL en un
entorno compartido deben revisarse las tres migraciones nuevas y sus permisos operativos.

### Regresiones financieras añadidas

`JdbcSalesWorkflowPostgresIntegrationTest` cubre replay, mismo intento concurrente y rollback de una
venta con la misma clave. `FinancialOperationsPostgresIntegrationTest` usa fixtures `C-FIN-`,
`S-FIN-` y `P-FIN-` propios para probar pago general idempotente, límite del saldo disponible,
serialización concurrente con dos conexiones, reversión conjunta, caja sin doble conteo y pago de
venta junto a recibo de caja. Su limpieza se limita a esos IDs y claves. Las pruebas no ejecutan las
migraciones: verifican que las tablas de V101/V102 existan y omiten la suite si el entorno está sin
configurar o no es exactamente `la_exclusiva_test`.

`FinancialControllersTest` carga los FXML reales sobre JavaFX: simula respuesta perdida y verifica
que Caja conserva clave/fecha y bloquea una carga útil diferente; comprueba que un trabajador no
consulta ni registra caja; y confirma que un abono general cambia el saldo general sin tocar la
venta pendiente. El modo memoria queda cubierto por `InMemoryFinanceWorkflowsTest`.

### Validación completa de la rama (2026-11-18)

La verificación final se ejecutó con Microsoft JDK 17.0.18, Maven 3.10.0 y PostgreSQL 18.6. La
consulta previa de solo lectura conectó explícitamente con `la_exclusiva_test`; las tablas de los
libros V101/V102 y las columnas de clave/huella de V100 estaban presentes. V001/V002 ya estaban
aplicadas. `LAEXCLUSIVA_DB_URL` se mantuvo sin configurar. No se ejecutaron migraciones durante
`mvn clean verify`, ni se accedió a `la_exclusiva`, ni se ejecutaron `DROP`, `TRUNCATE` o limpiezas
generales.

Resultado final observado: **BUILD SUCCESS, 253 pruebas, 0 fallidas, 0 errores y 0 omitidas**, con
38 informes Surefire y JAR generado. Las **21 pruebas PostgreSQL reales** pasaron: cartera de ventas
(1), clientes/vendedores (1), operaciones financieras (5), inventario (3), workflow de ventas (5),
productos/precios (3), repositorio de ventas (1) y movimientos de inventario (2). Las otras 232
pruebas fueron unitarias, simuladas o JavaFX sobre Windows. `FinancialControllersTest` cargó los FXML
reales y aprobó 4/4; comprobó claves de reintento de caja y cartera, acceso denegado al trabajador y
que un abono general no cambie el saldo de una venta. Xvfb no era necesario en Windows.

Las pruebas de PostgreSQL verificaron repetición/concurrencia/rollback de venta, ausencia de salidas
duplicadas, rollback de pagos/caja/cartera, abonos generales serializados con dos conexiones,
exclusión mutua del saldo disponible y separación de la facturación respecto a recaudos efectivos.
`FinancialOperationsPostgresIntegrationTest` creó fixtures con prefijos `C-FIN-`, `S-FIN-` y
`P-FIN-`, y eliminó solo las filas asociadas a su cliente, vendedor, producto, venta y claves propias.
Los otros tests también usan sus propias claves/IDs y rutinas de limpieza. No se modificaron datos de
la base de desarrollo.

Las primeras pruebas dirigidas de pantalla detectaron el atributo `wrapText` inválido en un `HBox`
de `cash_view.fxml` y un fixture de producto sin sus tres precios obligatorios; se corrigieron y las
pruebas posteriores pasaron. La revisión adicional encontró que el rol trabajador heredaba permiso
para crear deuda general desde `REGISTER_CUSTOMER`, y que la fecha editada no se comparaba durante un
reintento incierto de Caja. Se agregó `REGISTER_GENERAL_DEBT`, solo otorgado por el rol administrador
actual, y la fecha participa en la huella de la solicitud visual; se cubren ambas reglas con pruebas.

`git diff --check` pasó antes de preparar los commits documentales. Maven mostró seis advertencias
del modelo efectivo para JavaFX Controls y FXML; JavaFX avisó que se ejecutó desde el módulo no
nombrado. También se imprimen trazas intencionales de pruebas que simulan errores de consulta; todas
las pruebas correspondientes aprobaron.

Los once commits de código y pruebas son: `120a5b7` (contrato del libro de caja), `c0eeb09`
(repositorio/workflow JDBC de caja), `bf7868c` (idempotencia durable de altas de venta), `b7ee846`
(clave de reintento en la pantalla de ventas), `d1a99fc` (contrato del libro de deuda general),
`53198ea` (delta atómico JDBC para deuda general), `84ec0b4` (composición de servicios memoria/JDBC),
`818a3c0` (pruebas PostgreSQL financieras), `14df18e` (cartera y permisos de deuda general),
`612f753` (pantalla JavaFX de caja) y `5ac6a18` (regresiones JavaFX de caja/cartera). El registro de
Codex y el estado de publicación se encuentran en `docs/ai-usage/leader-ai-log.md`.

Queda pendiente validar las migraciones V100–V102 en el entorno de despliegue compartido y la
integración futura de caja con compras/proveedores de Jesús. Usuarios y asignación de vendedores
siguen en el almacenamiento de autenticación existente. Esta validación fue automatizada con
PostgreSQL y JavaFX/FXML; no constituye una revisión manual del equipo ni una prueba bancaria o de
producción.
