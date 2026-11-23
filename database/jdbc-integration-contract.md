# Contrato de integración JDBC

Este documento fija la interfaz de integración entre la infraestructura JDBC de Carlos y los DAO de productos, inventario y movimientos de Jesús. Su objetivo es que ambas ramas puedan avanzar sin duplicar la administración de conexiones ni definir firmas incompatibles.

## Ramas y flujo

- Infraestructura: `feature/jdbc-infrastructure` (Carlos).
- DAO de productos, precios, inventario y movimientos: `feature/products-inventory` (Jesús), PR #16.
- Rama actual de integración temporal: `feature/full-jdbc-integration`.
- Jesús parte inicialmente de `feature/jdbc-infrastructure` para compilar contra las clases compartidas.
- **Estado actualizado el 2026-11-18:** `origin/develop` apunta a `e92d874`, cuyo historial
  contiene el tip `dbbaf28` de `feature/products-inventory`; la comprobación de ancestry confirma
  que sus cambios están incorporados. Aunque la página del PR #16 inicialmente lo mostraba abierto,
  el timeline confirmó su merge a `develop` mediante `e92d874`; no se modificó ese PR desde esta tarea.
  La rama `feature/full-jdbc-integration` integró `origin/develop` mediante el merge normal
  `0936ad8`, sin conflictos. PR #18 usa `develop` como base; la descripción se actualiza con los
  resultados finales de PostgreSQL en GitHub. No se modificó ni
  publicó ninguna rama de Jesús o Daniel.

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

- `save(item)`: crea la fila inicial de inventario y requiere una transacción JDBC activa. Si ya existe la fila del producto, la rechaza; nunca reemplaza el total con el contenido de un snapshot. Fuera de una transacción lanza `IllegalStateException`.
- Los aumentos y descuentos posteriores se aplican mediante `InventoryStockAdjuster` dentro de la transacción compartida. El SQL incrementa por delta o descuenta solo si hay unidades suficientes; no se guarda un total reconstruido desde un snapshot.
- `findByProductId(...)`: consulta normal sin bloqueo.
- `findByProductIdForUpdate(...)`: el adaptador JDBC sobrescribe el método con `SELECT ... FOR UPDATE` y exige `transactionManager.isTransactionActive()`; de lo contrario lanza `IllegalStateException`. Permite leer la fila bajo bloqueo cuando el caso de uso requiere una comprobación coordinada. El método default mantiene compatible la implementación en memoria, que no necesita bloqueo SQL.

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

Después de integrar el tip `dbbaf28` de `origin/feature/products-inventory` mediante el merge
`c2ab247`, `mvn clean verify` con Temurin JDK 17.0.21 y Maven 3.10.0 terminó en `BUILD SUCCESS`:
222 pruebas, 210 aprobadas, 0 fallidas, 0 errores y 12 omitidas. Las omitidas corresponden a las
suites PostgreSQL de cartera, clientes/vendedores, inventario (3), workflow de ventas, productos
(3), ventas y movimientos (2). Las tres variables de conexión no estaban presentes; no se abrió una
conexión, no se aplicaron migraciones ni se modificó una base. `git diff --check` pasó después de
resolver los conflictos. Maven mostró avisos existentes del modelo JavaFX y JavaFX informó su
configuración no modular durante las pruebas de interfaz.

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
total_units = EXCLUDED.total_units` observado en #16. El PR #16 ya está integrado en `develop`
mediante `e92d874`; esta rama conserva además la regla insert-only para impedir que snapshots
obsoletos sustituyan existencias. Ese endurecimiento se revisa como parte del PR #18. El commit
`2676023` llegó antes a `develop` al fusionarse el PR #15.

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
  separación de deuda general, reintento idempotente y concurrencia real de pagos/stock.

### Validación PostgreSQL real del PR #18 (2026-11-18)

La ejecución se realizó con Microsoft JDK 17.0.18, Maven 3.10.0 y PostgreSQL 18.6 en
`localhost:5432`. La consulta de solo lectura confirmó que la base activa era exactamente
`la_exclusiva_test`, que estaban presentes las diez tablas de V001 y que V002 estaba aplicada por
la columna `sale_payment.idempotency_key` y la restricción única
`uq_sale_payment_sale_idempotency_key`. No se ejecutaron migraciones. La URL JDBC de desarrollo no
se configuró y no se abrió ni modificó `la_exclusiva`.

La primera ejecución completa encontró dos errores en
`InventoryRepositoryJdbcPostgresIntegrationTest`: sus pruebas concurrentes intentaban guardar con
`InventoryRepositoryJdbc.save` un snapshot de una fila ya existente. Ese método es insert-only por
diseño; los ajustes posteriores deben usar `InventoryStockAdjusterJdbc`. Se corrigió únicamente la
prueba para que el segundo hilo aplique su descuento mediante el ajustador atómico, preservando la
lectura `FOR UPDATE`, la espera del bloqueo y la comprobación de commit/rollback. No se cambió código
de producción. La suite enfocada pasó 3/3 contra PostgreSQL.

Después del arreglo, `mvn clean verify` terminó en `BUILD SUCCESS`: 222 pruebas, 0 fallidas,
0 errores y 0 omitidas. Surefire reportó 29 archivos; las siete suites PostgreSQL ejecutaron 12/12:
cartera 1, clientes/vendedores 1, inventario 3, workflow de ventas 1, productos 3, ventas 1 y
movimientos 2. Las doce aprobaron en PostgreSQL real. Las pruebas comprobaron persistencia de ventas,
líneas, pagos, productos, precios y movimientos; rollback transaccional; idempotencia de abonos;
consulta de cartera y saldos; y bloqueos/ajustes concurrentes de inventario y pagos. Las pruebas
JavaFX se ejecutaron bajo Windows, por lo que Xvfb no fue necesario. Maven informó seis problemas
del modelo efectivo para cada artefacto `javafx-controls` y `javafx-fxml`; las pruebas JavaFX también
registraron advertencias esperadas de escenarios simulados. El JAR se generó.

Las pruebas usaron fixtures con IDs propios y limpieza acotada a dichos registros. No se usaron
`TRUNCATE`, `DROP` ni migraciones. La autenticación/asignación de vendedores sigue en memoria, el
pago JDBC de deuda general no está implementado, el alta de venta carece de clave idempotente y
`SaleService` sigue separado de `JdbcSalesWorkflow`; queda pendiente la revisión humana del PR y un
recorrido manual autenticado completo.

La autenticación y la asignación de vendedores siguen en memoria, el pago de deuda general no tiene
flujo JDBC completo, el alta de ventas no posee clave idempotente persistida y `SaleService` continúa
separado de `JdbcSalesWorkflow`. El modo PostgreSQL debe seguir siendo explícito hasta validar las
integraciones contra la base aislada y revisar estos límites con el equipo.

## División de responsabilidades

- Carlos: coordina la integración transaccional, composición PostgreSQL/JavaFX y documentación.
- Jesús: mantiene sus entregas de infraestructura y DAO de productos, precios, inventario y
  movimientos; el PR #16 ya está integrado en `develop`, y la protección insert-only contra
  snapshots se revisa en el PR #18.
- Daniel: revisa permisos, navegación y pantallas JavaFX integradas con los contratos JDBC.

La aplicación selecciona `JdbcSalesWorkflow` solo cuando el modo `postgres` se configura
explícitamente. No se habilita por defecto ni se cambia la autenticación en memoria. La validación
de componentes PostgreSQL quedó aprobada en `la_exclusiva_test`; la rama aún requiere revisión
humana y recorrido manual de la interfaz antes de usarla con una instalación local.

## Autorización y confirmación de operaciones JavaFX (2026-11-18)

Los controladores de clientes, vendedores, inventario, ventas y cartera se configuran con el objeto
`UserSession`, no con indicadores booleanos de permiso calculados al abrir la pantalla. Cada handler
de escritura consulta `AuthorizationService.hasPermission(session, Permission)` inmediatamente antes
de invocar el servicio; el método también exige una sesión vigente y una cuenta activa. La visibilidad
de campos sigue la misma fuente de permisos, pero no reemplaza la comprobación al ejecutar. Las
operaciones protegidas son `REGISTER_CUSTOMER`, `REGISTER_SELLERS`, `MANAGE_PRODUCTS`,
`MANAGE_INVENTORY_ENTRIES`, `REGISTER_SALE` más `VIEW_SALES`, y `REGISTER_PAYMENT`.

La sesión del trabajador limita ventas al vendedor asignado y válido. El controlador selecciona el
vendedor automáticamente, bloquea el selector y vuelve a verificar su asociación antes de escribir.
Las asignaciones faltantes o desconocidas se rechazan; el administrador conserva selección libre.

El retorno normal de `SalesWorkflow.registerSale` confirma el alta, y solo entonces el controlador
limpia el carrito. Las consultas visuales posteriores son operaciones separadas; fallos de refresco
no cambian el estado confirmado ni vuelven a llamar al caso de uso. No existe todavía una clave
idempotente persistente para el alta de ventas, por lo que ante una excepción de resultado incierto se
conserva el carrito y se requiere consultar el historial antes de otro intento.

Para los abonos, el retorno normal de `SalesWorkflow.registerPayment` confirma la escritura. Si la
llamada falla con resultado incierto, Cartera conserva venta, importe y clave y permite volver a enviar
solo esa operación con la misma clave; no acepta otro importe/venta hasta resolverla. Una vez
confirmada, la clave del intento se libera antes de refrescar la vista. El botón de actualización
consulta clientes y cartera sin registrar pagos. Si una consulta falla, se vacían los datos parciales,
se muestran saldos como no disponibles y se ofrece reintentar solo la consulta. `JdbcSalesWorkflow`,
su idempotencia durable y su transacción compartida no se modificaron.

Estas reglas de presentación no amplían los contratos JDBC ni cambian el almacenamiento. Las
regresiones dirigidas se ejecutaron con JavaFX/FXML sobre Windows; `mvn clean verify` aprobó 229/229,
incluidas las 12 integraciones PostgreSQL reales en `la_exclusiva_test`. Los detalles verificables y
límites están en `database/sales-jdbc-integration.md` y en el Registro 25 de la bitácora IA.

## Serialización financiera por cliente

Las operaciones JDBC que modifican la deuda asociada a ventas adquieren primero el bloqueo de fila
del cliente y lo conservan en la transacción compartida hasta el commit o rollback. El puerto
`CustomerRepositoryInterface.findByIdForUpdate` expresa este contrato; `CustomerRepositoryJdbc` lo
implementa con `SELECT ... FOR UPDATE` y exige una conexión transaccional (`autoCommit=false`). La
implementación en memoria conserva el método predeterminado de lectura y no altera su contrato.

`JdbcSalesWorkflow.registerSale` bloquea al cliente antes de consultar la cartera histórica, validar
la consistencia del saldo o ajustar inventario y saldo. Los bloqueos posteriores de inventario siguen
el orden ascendente de ID de producto. Para un abono, el flujo obtiene primero solo el `customer_id`
de la venta, sin tomar decisiones sobre datos financieros; después bloquea al cliente. Solo entonces
lee el snapshot del saldo y llama a `SaleRepositoryJdbc.appendPaymentOnce`. El adaptador de pagos
vuelve a resolver la clave foránea y también respeta el orden cliente→venta antes de comprobar la
clave idempotente, bloquear la venta, calcular el saldo pendiente e insertar el pago.

Las lecturas de cartera, la inserción idempotente y el ajuste atómico del saldo siguen participando
en la misma unidad de trabajo de `JdbcTransactionManager`. Se conserva la comprobación de que la deuda
por ventas posterior al abono más el importe abonado no exceda el saldo agregado bloqueado; no se
elimina la validación ni se agrega una transferencia de crédito. Ventas a crédito y abonos del mismo
cliente quedan serializados y observan el saldo/deuda confirmados por la operación anterior. Un
abono excesivo se rechaza antes del ajuste; cualquier excepción revierte inserción y saldo juntos.

Las escrituras financieras deben pasar por `JdbcSalesWorkflow` y utilizar los adaptadores que
comparten proveedor y coordinador transaccional. No se debe bloquear primero la venta en un flujo
externo y después intentar bloquear al cliente, porque invertiría el orden común. El orden fijo
cliente→venta/inventario evita el ciclo de espera entre los flujos soportados; para productos con
varias existencias, `JdbcSalesWorkflow` conserva además el orden ascendente de ID.

La prueba PostgreSQL `creditSaleAndPaymentSerializeOnCustomerInBothTransactionOrders` observa con
`pg_blocking_pids` la espera real de la segunda transacción y utiliza latches y límites de tiempo.
Ejecuta los órdenes venta a crédito→abono y abono→venta, y valida saldo agregado, deuda histórica,
ventas, pagos, importe total, reintento idempotente y terminación de los hilos. La solución no cambia
V001/V002 ni introduce migraciones.

## Finanzas y caja (`feature/carlos-finance-close`, 2026-11-18)

Esta rama agrega contratos propios en `CustomerGeneralDebtWorkflow`, `CashWorkflow`,
`CustomerGeneralDebtMovementRepository`, `CashMovementRepository` e `IdempotentSaleRepository`.
Los adaptadores usan el proveedor y el coordinador JDBC ya existentes; no sustituyen ni editan los
DAO de inventario de Jesús ni la autenticación de Daniel.

`SaleRepositoryJdbc.saveOnce` asigna a la solicitud de venta una clave durable y una huella de carga
útil. El flujo JDBC confirma la cabecera, precios históricos de líneas, pago inicial, ajustes de stock,
movimientos, saldo y recibo inicial en una sola transacción. El replay exacto devuelve la venta
original y no aplica esos efectos de nuevo. Reutilizar la clave con datos distintos falla. El
controlador conserva clave/carga ante error incierto, y el modo memoria solo puede deduplicar durante
la vida del proceso.

La cartera de deuda general se calcula como saldo agregado menos deuda pendiente por venta. Las
operaciones que la cambian bloquean la fila del cliente, comprueban consistencia y aplican un delta
SQL atómico sobre el saldo. El registro auditable del cargo/pago, el delta y, para un pago, su recibo
de caja, usan la misma transacción. Un abono general se limita a la diferencia disponible y no puede
reducir `sale_payment`; una inconsistencia de sobrepago requiere corrección explícita, nunca una
transferencia implícita a crédito del cliente.

El libro de caja acepta entradas manuales de otros ingresos y categorías de egresos. Los recibos por
venta inicial, abono de venta y pago de deuda general son derivados e idempotentes y se insertan en la
transacción de origen. La facturación se suma desde `sale_line` usando sus precios históricos, sin
sumar pagos por joins, y se presenta como métrica distinta al efectivo recibido. Esto evita duplicar
ingresos y no interpreta ventas pendientes como recaudo.

La pantalla actualiza tabla e indicadores mediante `CashWorkflow.loadSnapshot(...)`, cuyo resultado
`CashSnapshot` agrupa ambos. `CashWorkflowJdbc` ejecuta sus consultas con el `TransactionBoundary`
compartido mediante `inReadOnlyRepeatableRead`: el `JdbcTransactionManager` configura la misma
conexión de lectura en `REPEATABLE_READ`, y `JdbcConnectionProvider` la reutiliza para movimientos,
resumen y facturación. Así un commit concurrente entre las consultas queda fuera de toda esa
instantánea y aparece completo en la siguiente actualización. Una transacción exterior incompatible
se rechaza explícitamente; no se presenta `READ_COMMITTED` como una lectura consistente. El
workflow en memoria conserva la implementación predeterminada del puerto.

V102 crea `cash_movement` vacío y no guarda la fecha de activación/cobertura del libro. Por ello no
se puede afirmar qué cobros históricos contiene ni se reconstruyen recibos automáticamente. Caja
advierte que los cobros anteriores a la activación podrían faltar, indica que no existe una fecha
verificable y mantiene la facturación histórica separada del efectivo registrado.

Migraciones nuevas y exclusivas de la rama: V100 (clave/huella de venta), V101 (libro auditable de
deuda general), V102 (libro de caja) y V103 (refuerzo del `CHECK` de identidad de venta para bases
donde V100 ya se aplicó). V100/V101/V102 se aplicaron manualmente solo en `la_exclusiva_test` luego
de verificar columnas/tablas ausentes. V103 se aplicó después, también exclusivamente en
`la_exclusiva_test`, tras comprobar que no había identidades parciales ni claves duplicadas; su
transacción reemplaza y valida el `CHECK` sin transformar filas. La base conservó la fila histórica
con ambas columnas nulas. Ninguna migración se ejecuta automáticamente. Cada entorno destino debe
revisar y aplicar cada versión una sola vez después de V001/V002; antes de V103 debe confirmar que
no hay identidades parciales, pues la validación falla de forma transaccional si las encuentra.

La pantalla de Caja requiere `VIEW_CASH` y `REGISTER_CASH_MOVEMENT`; el rol trabajador vigente no
incluye esos permisos. Crear deuda general requiere el permiso específico `REGISTER_GENERAL_DEBT`
(incluido en el rol administrador, no en trabajador), en vez de inferirlo desde el permiso de alta de
clientes. Abonar deuda general utiliza `REGISTER_PAYMENT`, el mismo permiso de abonos financieros,
y el handler lo vuelve a comprobar al momento de escribir.

La evidencia específica de transacciones, consultas, FXML, límites del modo memoria y suites
PostgreSQL se mantiene en [sales-jdbc-integration.md](sales-jdbc-integration.md). Las pruebas
permanecen restringidas a fixtures ficticios propios de `la_exclusiva_test`; ninguna limpieza de
estas suites afecta filas ajenas.

## Cartera asistida, avisos y auditoría (V107/V108)

V107 agrega `customer_collection_profile`, `customer_collection_follow_up` y
`customer_collection_reminder`; V108 agrega `administrative_audit_event`. Son tablas aditivas y no
reescriben cliente, venta o pago históricos. La aplicación no aplica migraciones automáticamente.
Antes de habilitar esta parte de la aplicación, el esquema destino debe tener las versiones
prerrequisito de clientes, ventas, libros financieros y usuarios, además de V107 y V108. Un esquema
incompleto debe fallar cerrado.

En modo PostgreSQL, `ApplicationServices.collectionAssistant()` compone adaptadores JDBC sobre el
mismo `JdbcConnectionProvider` y la transacción compartida. Los avisos semanales son únicos por
cliente/semana ISO; el claim y los cambios de estado usan comparación de estado/contador para evitar
dos entregas concurrentes. La entrega externa de correo no es atómica con PostgreSQL: `SENDING`
representa un resultado que requiere reconciliación y nunca se reintenta automáticamente.

El perfil y el seguimiento no modifican el modelo histórico de clientes. El detalle de contacto
requiere `MANAGE_COLLECTION`; dashboard/PDF requieren `VIEW_PORTFOLIO`; lectura de auditoría requiere
`VIEW_ADMIN_AUDIT`. La guía de consentimiento, variables de correo, política horaria, contadores del
dashboard, estado de cuenta y límites conocidos está en
[`docs/collection-reminders-security.md`](../docs/collection-reminders-security.md).

`CustomerCollectionPostgresIntegrationTest` solo se activa en el laboratorio efímero de GitHub
Actions. Una omisión Surefire local sin las propiedades/URL de prueba no acredita validación
PostgreSQL. Las pruebas no envían correos reales y no se deben ejecutar contra una base compartida.
