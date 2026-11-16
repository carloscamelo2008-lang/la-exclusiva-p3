# Contrato de integración JDBC

Este documento fija la interfaz de integración entre la infraestructura JDBC de Carlos y los DAO de productos, inventario y movimientos de Jesús. Su objetivo es que ambas ramas puedan avanzar sin duplicar la administración de conexiones ni definir firmas incompatibles.

## Ramas y flujo

- Infraestructura: `feature/jdbc-infrastructure` (Carlos).
- DAO de productos e inventario: `feature/product-inventory-jdbc` (Jesús).
- Rama de integración: `develop`.
- Jesús parte inicialmente de `feature/jdbc-infrastructure` para poder compilar contra las clases nuevas. Cuando la infraestructura se integre en `develop`, actualizará su rama con esa base y abrirá su PR hacia `develop`.

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
- `InventoryService.discountSale(...)` procesa los IDs de producto en orden ascendente antes de bloquear las filas, reduciendo el riesgo de deadlocks.

Los snapshots de líneas de venta deben conservar `unit_price` y `units_per_basket_snapshot` leídos de la fila histórica; nunca se reconstruyen con los precios ni la capacidad actuales del catálogo.

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

Ejecutar V001 una sola vez desde pgAdmin Query Tool sobre una base vacía. Crea las diez tablas, restricciones e índices. No inserta productos ni existencias iniciales: esa responsabilidad corresponde al inicializador Java idempotente. La migración falla si se intenta ejecutar por segunda vez sobre la misma base.

## Pruebas y seguridad de los datos

- Las pruebas unitarias se ejecutan sin PostgreSQL.
- Las pruebas de integración con PostgreSQL usan exclusivamente `LAEXCLUSIVA_TEST_DB_URL` y la base `la_exclusiva_test`.
- Si falta esa variable, las pruebas de integración se omiten.
- Si está definida pero la ruta de base no termina exactamente en `/la_exclusiva_test`, las pruebas fallan antes de limpiar o modificar datos.
- No ejecutar `DROP`, `TRUNCATE` ni limpieza de fixtures en `la_exclusiva`.
- Utilizar únicamente datos ficticios.

## División de responsabilidades

- Carlos: migración, dependencia, configuración, proveedor de conexiones, coordinador transaccional e integración de límites transaccionales en servicios.
- Jesús: DAO de producto, precios, inventario y movimientos; `SalePresentation.fromCode`; inicializador idempotente; pruebas unitarias de JDBC y pruebas con driver simulado.
- Daniel: interfaz JavaFX, FXML, controladores y CSS, sin tareas SQL/DAO/JDBC.

La conexión de los DAO a `Main` se realizará después de revisar la sustitución de las compensaciones manuales por transacciones reales.
