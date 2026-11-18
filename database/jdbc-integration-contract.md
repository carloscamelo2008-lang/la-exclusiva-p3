# Contrato de integración JDBC

Este documento fija la interfaz de integración entre la infraestructura JDBC de Carlos y los DAO de productos, inventario y movimientos de Jesús. Su objetivo es que ambas ramas puedan avanzar sin duplicar la administración de conexiones ni definir firmas incompatibles.

## Ramas y flujo

- Infraestructura: `feature/jdbc-infrastructure` (Carlos).
- DAO de productos, precios, inventario y movimientos: `feature/products-inventory` (Jesús), PR #16.
- Rama actual de integración temporal: `feature/full-jdbc-integration`.
- Jesús parte inicialmente de `feature/jdbc-infrastructure` para compilar contra las clases compartidas.
- **Estado actualizado el 2026-11-17:** el PR #15 (`feature/jdbc-infrastructure`) se fusionó en
  `develop` (`ea85865`); el PR #16 (`feature/products-inventory`) sigue abierto. La rama
  `feature/full-jdbc-integration` parte del head `426a65e` de #16 e integra la base anterior de
  `develop` (`8d00215`), las protecciones del PR #15 y el `develop` actual (`ea85865`, incluido el
  PR #17) mediante el merge `ecadf8b`. El borrador seguirá basado en #16 hasta que este se fusione.
  Esta rama temporal no reemplaza ni publica cambios en la rama de Jesús.

## Configuración de bases locales

No guardar credenciales en Git. Las variables son:

| Variable | Uso |
|---|---|
| `LAEXCLUSIVA_DB_URL` | URL de desarrollo |
| `LAEXCLUSIVA_TEST_DB_URL` | URL exclusiva para pruebas de integración |
| `LAEXCLUSIVA_DB_USER` | Usuario local PostgreSQL, compartido por ambas URL |
| `LAEXCLUSIVA_DB_PASSWORD` | Contraseña local, compartida por ambas URL |

URL de desarrollo:

```text
jdbc:postgresql://localhost:5432/la_exclusiva
```

URL de pruebas:

```text
jdbc:postgresql://localhost:5432/la_exclusiva_test
```

`DatabaseConfig.fromEnvironment()` lee la URL de desarrollo. `DatabaseConfig.fromTestEnvironment()` lee `LAEXCLUSIVA_TEST_DB_URL`, reutiliza usuario/contraseña y valida que el nombre de la base sea exactamente `la_exclusiva_test`. No se debe usar la URL de desarrollo para pruebas que limpien o preparen datos.

Las pruebas unitarias no requieren PostgreSQL ni variables de entorno. Las pruebas de integración PostgreSQL se omiten cuando `LAEXCLUSIVA_TEST_DB_URL` no está configurada. Si está configurada pero apunta a otra base, la prueba debe fallar antes de ejecutar SQL.

## Conexiones y firma para los DAO

La firma pública del proveedor es:

```java
public <T> T withConnection(SqlFunction<T> operation)
```

El callback es:

```java
@FunctionalInterface
public interface SqlFunction<T> {
    T apply(Connection connection) throws SQLException;
}
```

Ejemplo:

```java
return connectionProvider.withConnection(connection -> {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, productId);
        try (ResultSet resultSet = statement.executeQuery()) {
            // Mapear el resultado.
        }
    }
    return product;
});
```

Reglas obligatorias:

1. El DAO cierra sus `PreparedStatement` y `ResultSet` con try-with-resources.
2. El DAO no cierra la `Connection` recibida.
3. El DAO no ejecuta `commit`, `rollback` ni modifica `autoCommit`.
4. Cuando hay una transacción activa en el hilo, cada llamada a `withConnection` reutiliza exactamente la misma conexión.
5. Fuera de una transacción, el proveedor abre y cierra una conexión por operación. Esa conexión funciona con autocommit.
6. Los errores SQL se traducen a `JdbcPersistenceException`.

## Transacciones y DAO con varias escrituras

La API del coordinador es:

```java
transactionManager.inTransaction(() -> {
    // Operación que debe confirmarse completa o revertirse completa.
});
```

También permite devolver un resultado:

```java
Sale sale = transactionManager.inTransaction(
        () -> saleService.registerSale(...));
```

Las llamadas anidadas se unen a la transacción exterior. Solo quien abrió la conexión hace `commit` o `rollback`. Si una llamada interna falla, el contexto queda marcado como rollback-only incluso si una capa superior captura la excepción. No trasladar una operación transaccional a otro hilo.

**Todo DAO que realice varias escrituras relacionadas debe recibir ambas dependencias**:

```java
public ProductRepositoryJdbc(
        JdbcConnectionProvider connectionProvider,
        JdbcTransactionManager transactionManager) {
    // Guardar las dos dependencias.
}
```

El método `ProductRepositoryJdbc.save(product)` inserta `beer_product` y sus tres filas `product_price` dentro de una única transacción:

```java
transactionManager.inTransaction(() ->
    connectionProvider.withConnection(connection -> {
        // INSERT del producto y de sus tres precios usando la misma conexión.
        return null;
    })
);
```

Si ya existe una transacción exterior, el método se une a ella y no confirma por separado. Si se invoca sin transacción exterior, abre una transacción propia. Esta regla garantiza que nunca quede un producto insertado solo con uno o dos precios.

Los métodos de una sola sentencia pueden utilizar `withConnection` directamente. Los límites que coordinan más de un repositorio pertenecen al servicio/caso de uso y deberán envolverse en `transactionManager.inTransaction(...)`.

## Contratos de repositorio

### Productos

```java
void save(BeerProduct product);

void updatePrice(
        String productId,
        SalePresentation presentation,
        BigDecimal newPrice);

void addMissingPrice(
        String productId,
        SalePresentation presentation,
        BigDecimal price);
```

- `save(product)`: inserta producto y sus tres precios. Si el ID ya existe, falla; no es upsert.
- `updatePrice(...)`: actualiza únicamente un precio existente. Si no existe, falla.
- `addMissingPrice(...)`: inserta únicamente si falta la presentación. Si ya existe, falla.
- Las operaciones validan producto, presentación y precio. Los importes admiten como máximo dos decimales; no se redondean silenciosamente.

### Inventario

```java
void save(InventoryItem item);

Optional<InventoryItem> findByProductId(String productId);

default Optional<InventoryItem> findByProductIdForUpdate(String productId) {
    return findByProductId(productId);
}
```

- `save(item)`: inserta o actualiza por `product_id` (upsert).
- `findByProductId(...)`: consulta normal sin bloqueo.
- `findByProductIdForUpdate(...)`: el adaptador JDBC sobrescribe el método con `SELECT ... FOR UPDATE`. Debe exigir `transactionManager.isTransactionActive()`; de lo contrario lanza `IllegalStateException`. El método default mantiene compatible la implementación en memoria, que no necesita bloqueo SQL.

### Presentaciones y movimientos

- `SalePresentation.fromCode(String)`: convierte únicamente `UNIT`, `HALF_BASKET` y `FULL_BASKET`. Un valor nulo o desconocido se rechaza.
- `StockMovementRepository.remove(...)`: la implementación JDBC lanza `UnsupportedOperationException` con un mensaje claro. El historial no se borra para compensar una operación; el rollback revierte las inserciones.
- `stock_movement.id` es una clave técnica generada por PostgreSQL. Las lecturas JDBC exponen ese ID y el producto del catálogo asociado en `StockMovement`. El constructor de cinco argumentos sigue disponible para movimientos nuevos y el repositorio en memoria conserva `remove(...)` para la compensación local.
- Las lecturas JDBC ordenan por `occurred_at` ascendente y usan el ID generado para desempatar marcas de tiempo iguales.
- `InventoryService.discountSale(...)` procesa los IDs de producto en orden ascendente antes de bloquear las filas, reduciendo el riesgo de deadlocks.

Los snapshots de líneas de venta deben conservar `unit_price` y `units_per_basket_snapshot` leídos de la fila histórica; nunca se reconstruyen con los precios ni la capacidad actuales del catálogo.

### Consulta de cartera de ventas

```java
BigDecimal findOutstandingSalesDebt(String customerId);
```

El puerto `CustomerDebtQuery` y el adaptador `CustomerDebtQueryJdbc` calculan la deuda pendiente
asociada a ventas. Las líneas y los pagos se agregan por separado por `sale_id` antes de combinarse;
así, varias líneas y varios pagos no producen un producto cartesiano ni inflan los importes. El
resultado suma por venta `GREATEST(SUM(quantity * unit_price) - SUM(amount), 0)` y devuelve cero si
el cliente no tiene ventas. El límite se aplica a cada venta antes de sumar por cliente. Un pago
histórico superior al total de su venta es una inconsistencia que debe detectarse y corregirse; no
se transfiere como crédito para cubrir otras ventas. El flujo normal de `SalePaymentAppender` rechaza
abonos superiores al saldo pendiente. No lee `customer.balance`, porque ese campo también incluye
deuda general.
El adaptador usa el `JdbcConnectionProvider` compartido y, por tanto, reutiliza la conexión de una
transacción exterior. Solo consulta: no cambia saldo, ventas ni pagos.

La futura integración debe usar esta consulta para separar `deuda general = customer.balance -
deuda pendiente de ventas`. La sustitución del mapa en memoria `CustomerService.saleDebts` requiere
coordinar inserción de ventas/abonos y ajustes de `customer.balance` en una unidad de trabajo
compartida; el puerto de consulta no reemplaza esas escrituras. `CustomerService`, `SaleService` y
`Main` todavía no están conectados a este adaptador.

### Repositorios JDBC de clientes y vendedores

`CustomerRepositoryJdbc` implementa `CustomerRepositoryInterface` y `SellerRepositoryJdbc`
implementa `SellerRepositoryInterface`. Ambos leen y escriben las columnas de V001 con sentencias
preparadas y conservan los IDs proporcionados por los modelos históricos. Los límites son los del
esquema: cliente `VARCHAR(64)`, vendedor `VARCHAR(32)` y saldo de cliente `NUMERIC(19,2)` no
negativo. Los importes se validan con `BigDecimal`; no se redondean valores con más de dos decimales.

`save` es siempre una inserción: los IDs duplicados se rechazan y nunca se convierten en upsert.
La búsqueda/listado devuelve entidades reconstituidas, y el repositorio de vendedores no añade una
operación de actualización que su interfaz no define. El contrato compartido tiene una diferencia
intencional entre adaptadores: el repositorio en memoria sigue reemplazando el snapshot completo,
incluido `balance`, mientras que `CustomerRepositoryJdbc.update` solo escribe nombre, teléfono y
dirección. El adaptador JDBC añade `WHERE id = ? AND balance = ?`; el saldo del snapshot funciona
como control optimista, nunca aparece en `SET`. Si el cliente no existe lanza
`IllegalArgumentException`; si existe pero el saldo ya cambió lanza
`ConcurrentModificationException` y solicita recargarlo. Así, una instantánea antigua no puede
restaurar un saldo anterior. `CustomerBalanceAdjusterJdbc` se mantiene como única vía JDBC para
escribir `customer.balance`.

`CustomerService` todavía aplica operaciones de deuda mediante snapshots y usa actualizaciones de
repositorio para compensar fallos. Por tanto, no se debe conectar todavía a `CustomerRepositoryJdbc`.
Antes de habilitar JDBC, el servicio debe separar las modificaciones personales de los ajustes
financieros, usar exclusivamente `CustomerBalanceAdjuster` para estos últimos y ejecutar los cambios
relacionados bajo el mismo `JdbcTransactionManager`; las compensaciones por snapshots deben
reemplazarse por rollback transaccional. La implementación en memoria conserva su semántica actual.

Para operaciones financieras se añade el puerto `CustomerBalanceAdjuster` y su adaptador
`CustomerBalanceAdjusterJdbc`:

```java
BigDecimal increaseBalance(String customerId, BigDecimal amount);
BigDecimal decreaseBalance(String customerId, BigDecimal amount);
```

El adaptador suma en SQL y aplica los abonos mediante una única sentencia condicional:
`UPDATE customer SET balance = balance - ? WHERE id = ? AND balance >= ? RETURNING balance`.
PostgreSQL serializa las actualizaciones de la fila y vuelve a comprobar la condición tras esperar
por otra escritura; así, dos abonos concurrentes no pueden producir un saldo negativo. El puerto
usa el mismo `JdbcConnectionProvider`: si existe una transacción exterior, los ajustes comparten su
conexión y se confirman o revierten junto con las demás escrituras. Fuera de ella, cada ajuste es una
sentencia atómica independiente.

La futura integración debe envolver el registro de venta/abono y el ajuste correspondiente en
`JdbcTransactionManager.inTransaction(...)`, inyectando repositorios y adaptadores construidos con
el mismo proveedor. El pago de una venta debe persistirse y reducir el saldo agregado dentro de esa
misma unidad; el registro de una venta a crédito debe insertar la venta y aumentar ese saldo dentro
de la misma unidad. Las lecturas de cartera siguen derivándose del historial de líneas y pagos. No
se cambia todavía `CustomerService`, `SaleService`, `Main` ni la composición JavaFX; la ruta en
memoria continúa activa.

La validación definitiva informada por Carlos el 2026-11-16 a las 22:45 (Colombia) ejecutó
`mvn clean verify` con Java 17 y PostgreSQL en `la_exclusiva_test`: 190 pruebas aprobadas, sin
fallos, errores ni omisiones. Los reportes Surefire locales registran aprobadas las pruebas reales
`CustomerSellerJdbcPostgresIntegrationTest`, `CustomerDebtQueryJdbcPostgresIntegrationTest` y
`SaleRepositoryJdbcPostgresIntegrationTest` (una prueba cada una). Codex verificó las 22 suites XML
y sus marcas de tiempo, sin repetir Maven ni conectarse a la base. No se registraron credenciales.

## Nombres de columnas acordados

Se evitan nombres genéricos para que los DAO lean el esquema sin ambigüedad:

| Tabla | Columna SQL | Significado |
|---|---|---|
| `stock_movement` | `movement_type` | `ENTRY` o `EXIT` |
| `stock_movement` | `occurred_at` | Fecha/hora del movimiento |
| `sale_payment` | `occurred_at` | Fecha/hora del pago |

Los nombres del modelo Java actual no necesitan cambiar: `StockMovement.type()`, `StockMovement.timestamp()` y el accessor temporal de `SalePayment` se mapean desde estos nombres SQL.

Las columnas temporales usan `TIMESTAMP(6) WITHOUT TIME ZONE`. Normalizar `LocalDateTime` a microsegundos al persistir para que las comparaciones de pruebas sean consistentes.

## Migración SQL

El esquema ejecutable está en:

`database/migrations/V001__initial_schema.sql`

`database/migrations/V002__payment_idempotency.sql` se aplica después de V001. Añade
`sale_payment.idempotency_key VARCHAR(64)` y la unicidad `(sale_id, idempotency_key)`; PostgreSQL
permite varias filas con `NULL`, por lo que conserva pagos anteriores y pagos iniciales sin clave,
pero impide repetir una clave no nula dentro de la misma venta. No ejecutar migraciones desde Maven
ni contra una base de desarrollo. Antes de cualquier prueba que escriba, confirmar que la URL es
exclusivamente `la_exclusiva_test`.

Los abonos JDBC deben usar el puerto `SalePaymentAppender.appendPayment(saleId, amount,
idempotencyKey)`. La operación se une a la transacción exterior o abre una propia, bloquea la venta,
consulta primero la clave y luego valida el saldo calculado desde las filas persistidas. Repetir
clave e importe devuelve el pago existente con su fecha original; reutilizar la clave con otro
importe se rechaza. `SaleRepositoryJdbc.update(Sale)` rechaza instantáneas, porque `SalePayment` no
lleva la clave persistida y una instantánea obsoleta no permite distinguir de forma segura un
reintento de un pago nuevo. La restricción única de PostgreSQL respalda el bloqueo ante carreras.

Ejecutar V001 una sola vez desde pgAdmin Query Tool sobre una base vacía. Crea las diez tablas, restricciones e índices. No inserta productos ni existencias iniciales. La composición PostgreSQL de la rama de integración no ejecuta un inicializador; un administrador crea el catálogo desde la pantalla de inventario. La migración falla si se intenta ejecutar por segunda vez sobre la misma base.

## Pruebas y seguridad de los datos

- Las pruebas unitarias se ejecutan sin PostgreSQL.
- Las pruebas de integración con PostgreSQL usan exclusivamente `LAEXCLUSIVA_TEST_DB_URL` y la base `la_exclusiva_test`.
- Si falta esa variable, las pruebas de integración se omiten.
- Si está definida pero la ruta de base no termina exactamente en `/la_exclusiva_test`, las pruebas fallan antes de limpiar o modificar datos.
- No ejecutar `DROP`, `TRUNCATE` ni limpieza de fixtures en `la_exclusiva`.
- Utilizar únicamente datos ficticios.

## Integración JavaFX y unidad de trabajo completa (2026-11-17)

La rama `feature/full-jdbc-integration` añade una composición seleccionable mediante
`LAEXCLUSIVA_PERSISTENCE_MODE=memory|postgres`; si falta la variable conserva `memory`. La
composición PostgreSQL crea un `JdbcConnectionProvider` y un `JdbcTransactionManager` compartidos
por repositorios y ajustadores, sin abrir conexiones durante el arranque.

`JdbcSalesWorkflow` coordina venta, descuentos atómicos de inventario, movimientos, pago inicial,
saldo de cliente, líneas históricas y consulta posterior dentro de una sola transacción. Agrupa
unidades por producto y ajusta inventario por ID ascendente. Para abonos,
`IdempotentSalePaymentAppender.AppendResult.inserted` distingue inserciones nuevas de reintentos;
solo la primera reduce el saldo agregado. Un fallo en cualquier paso revierte venta, líneas, pagos,
existencias, movimientos y saldo.

`InventoryStockAdjusterJdbc` aplica sumas SQL atómicas y descuentos condicionados por existencias.
En esta rama, `InventoryRepositoryJdbc.save` solo inserta y rechaza sobrescribir una fila existente;
los cambios de stock usan el ajustador. Esto mitiga el riesgo del `ON CONFLICT ... DO UPDATE SET
total_units = EXCLUDED.total_units` observado en #16, pero la corrección solo está en esta rama de
integración y Jesús debe revisarla antes de fusionar #16. El endurecimiento de conexiones indirectas
del commit `2676023` ya llegó a `develop` al fusionarse el PR #15.

Las pantallas JavaFX consultan productos, existencias, movimientos, clientes y vendedores; permiten
registrar productos y sus tres precios, entradas de inventario, ventas por unidad/media canasta/
canasta, pagos iniciales y abonos a ventas específicas. El modo en memoria conserva su catálogo y
servicios anteriores. PostgreSQL se activa solo con configuración explícita.

Límites que requieren revisión antes de habilitarlo al equipo:

- El login y las cuentas siguen en `InMemoryUserRepository`; no se conectó `user_account` ni se
  cambió el flujo de Daniel. Cuentas y asignación de vendedor no son persistentes.
- La pantalla de ventas respeta `VIEW_SALES` y `VIEW_SELLERS`: un trabajador solo ve su propio
  historial y solo puede usar su vendedor asignado. Si la cuenta no tiene `sellerId`, el selector
  queda vacío; el usuario demo que crea `App` actualmente no tiene asignación y requiere revisión.
- El formulario de catálogo usa `MANAGE_PRODUCTS` y la entrada de existencias usa
  `MANAGE_INVENTORY_ENTRIES`; son permisos separados.
- `CustomerService` se utiliza para alta y consulta; sus operaciones de deuda general siguen
  usando `saleDebts`. En el modo PostgreSQL las ventas y abonos pasan por `JdbcSalesWorkflow`; la
  deuda general se muestra como `customer.balance - deuda de ventas`, pero no se registra un pago
  de deuda general en esta rama. Ese caso debe diseñarse antes de ofrecerlo desde JavaFX.
- `SaleService` conserva el camino en memoria y no se conecta a JDBC. En PostgreSQL la pantalla
  usa `SalesWorkflow`/`JdbcSalesWorkflow`; `InventoryService.discountSale` y `rollbackDiscount`
  rechazan el modo JDBC para evitar compensaciones basadas en snapshots.
- No se sembraron productos ni se ejecutaron migraciones desde la aplicación. Para pruebas, V001 y
  V002 deben estar aplicadas previamente a `la_exclusiva_test`; un administrador puede registrar
  el catálogo desde la pantalla después de configurar la conexión.
- El registro de ventas no tiene una clave idempotente de solicitud. Los reintentos persistentes
  cubren abonos; una respuesta incierta al registrar venta podría crear una segunda venta.
- `JdbcSalesWorkflowPostgresIntegrationTest` cubre fixtures propios, rollback, snapshots históricos,
  separación de deuda general, reintento idempotente y concurrencia de pagos/stock, pero requiere
  ejecutar contra PostgreSQL real para probar bloqueos y comportamiento del servidor.

Con Temurin JDK 17.0.21 y Maven 3.10.0, la última ejecución `mvn clean verify` terminó en
`BUILD SUCCESS`: 222 pruebas contabilizadas, 210 aprobadas, 0 fallidas, 0 errores y 12 omitidas.
Las 12 omitidas son las integraciones PostgreSQL porque `LAEXCLUSIVA_TEST_DB_URL`,
`LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD` no estaban configuradas. No se abrió conexión,
no se ejecutaron migraciones ni escrituras de base. Las pruebas JavaFX cargaron las vistas FXML bajo
Windows y verificaron un alta simulada de producto; Xvfb no fue necesario. Las advertencias fueron
los problemas existentes del modelo efectivo de JavaFX, el aviso de configuración JavaFX no modular
y registros esperados de las pruebas de fallo simulado de consultas. Se omitieron
`CustomerDebtQueryJdbcPostgresIntegrationTest` (1), `CustomerSellerJdbcPostgresIntegrationTest` (1),
`InventoryRepositoryJdbcPostgresIntegrationTest` (3), `JdbcSalesWorkflowPostgresIntegrationTest` (1),
`ProductRepositoryJdbcPostgresIntegrationTest` (3), `SaleRepositoryJdbcPostgresIntegrationTest` (1)
y `StockMovementRepositoryJdbcPostgresIntegrationTest` (2).

Mientras #16 siga abierto, el PR de integración debe ser borrador contra
`feature/products-inventory`. Después de que Jesús integre ese PR, actualizarlo para que compare con
`develop` y ejecutar las pruebas PostgreSQL reales antes de habilitar el modo `postgres` al equipo.

## División de responsabilidades

- Carlos: coordina la integración transaccional, composición PostgreSQL/JavaFX y documentación.
- Jesús: mantiene sus entregas de infraestructura y DAO de productos, precios, inventario y
  movimientos; revisa la protección contra snapshots de inventario descrita para el PR #16.
- Daniel: revisa permisos, navegación y pantallas JavaFX integradas con los contratos JDBC.

La aplicación selecciona `JdbcSalesWorkflow` solo cuando el modo `postgres` se configura
explícitamente. No se habilita por defecto ni se cambia la autenticación en memoria. La rama aún
requiere revisión humana y una ejecución PostgreSQL real antes de usarla con una instalación local.
