# Contrato de integración JDBC

Este documento fija el contrato mínimo entre la infraestructura de Carlos y los DAO de productos e inventario de Jesús. El objetivo es que ambos puedan trabajar en ramas separadas sin inventar firmas ni administrar conexiones de forma incompatible.

## Rama e integración

- Infraestructura: **feature/jdbc-infrastructure** (Carlos).
- DAO de productos e inventario: **feature/product-inventory-jdbc** (Jesús).
- Base de integración: **develop**.
- La rama de Jesús debe partir de develop una vez integrada la infraestructura. Si necesita comenzar antes, puede partir temporalmente de feature/jdbc-infrastructure; después debe actualizar su rama contra develop cuando se fusione la infraestructura.

## Configuración

Se utilizan estas variables de entorno, sin credenciales en archivos versionados:

- LAEXCLUSIVA_DB_URL
- LAEXCLUSIVA_DB_USER
- LAEXCLUSIVA_DB_PASSWORD

URL local de desarrollo: jdbc:postgresql://localhost:5432/la_exclusiva.

La configuración Java queda en com.laexclusiva.persistence.jdbc.DatabaseConfig. Falla al iniciar si falta alguna variable o la URL no corresponde a PostgreSQL.

## Firma acordada para los DAO

La firma pública es:

```java
public <T> T withConnection(SqlFunction<T> operation)
```

El callback tiene esta forma:

```java
@FunctionalInterface
public interface SqlFunction<T> {
    T apply(Connection connection) throws SQLException;
}
```

Ejemplo de uso:

```java
return connectionProvider.withConnection(connection -> {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        // Configurar parámetros y ejecutar la sentencia.
        try (ResultSet resultSet = statement.executeQuery()) {
            // Mapear el resultado.
        }
    }
    return result;
});
```

Reglas obligatorias:

1. El DAO cierra sus PreparedStatement y ResultSet con try-with-resources.
2. El DAO no cierra la Connection recibida.
3. El DAO no ejecuta commit, rollback ni modifica autoCommit.
4. Si existe una transacción activa en el hilo, todas las llamadas a withConnection reutilizan exactamente la misma conexión.
5. Fuera de una transacción, el proveedor abre y cierra una conexión para la operación.
6. Los errores SQL se traducen a JdbcPersistenceException.

## Transacciones

La API pública del coordinador es:

```java
transactionManager.inTransaction(() -> {
    // Llamadas a servicios y repositorios que deban confirmarse juntas.
});
```

También permite devolver un resultado:

```java
Sale sale = transactionManager.inTransaction(() -> saleService.registerSale(...));
```

Las llamadas anidadas se unen a la transacción exterior. Solo la operación que abrió la conexión ejecuta el commit o rollback. Si una operación interna falla, la transacción queda marcada para rollback incluso si una capa superior captura la excepción. No trasladar una operación transaccional a otro hilo.

Límites de atomicidad que deben respetarse al integrar servicios:

- Alta de producto y sus tres precios.
- Entrada de inventario y su movimiento.
- Descuento de inventario y todos sus movimientos.
- Venta, líneas, pago inicial, inventario, movimientos y saldo del cliente.
- Abono y actualización de saldo del cliente.

La infraestructura ya proporciona JdbcTransactionManager; la integración de los límites de transacción en los casos de uso debe realizarse antes de activar los DAO en la aplicación.

## Contratos de repositorio y reglas acordadas

- ProductRepository.save(product): inserta un producto nuevo y sus tres precios iniciales. Si el ID ya existe, debe fallar; no es un upsert.
- ProductRepository.updatePrice(...): cambia un precio existente. No crea silenciosamente la fila que falta.
- ProductRepository.addMissingPrice(...): crea un precio solo si no existe. Si ya existe, debe fallar.
- InventoryRepository.save(item): inserta o actualiza por product_id (upsert).
- InventoryRepository.findByProductId(...): consulta normal, sin bloqueo.
- InventoryRepository.findByProductIdForUpdate(...): ejecuta SELECT ... FOR UPDATE y requiere transacción activa.
- SalePresentation.fromCode(String): convierte únicamente UNIT, HALF_BASKET y FULL_BASKET; un código nulo o desconocido se rechaza.
- StockMovementRepository.remove(...): la implementación JDBC lanza UnsupportedOperationException con explicación. El historial no se borra para compensar una operación; la transacción revierte inserciones.
- InventoryService.discountSale(...) procesa las referencias en orden ascendente por ID de producto antes de bloquearlas.

Los precios admiten como máximo dos decimales. No redondear silenciosamente valores con más de dos decimales. Usar BigDecimal y comparar importes con compareTo.

La rehidratación de líneas de venta debe conservar unit_price y units_per_basket_snapshot leídos de la fila histórica; nunca reconstruirlos con precios/capacidades actuales del catálogo.

## Migración SQL

El esquema inicial ejecutable está en:

database/migrations/V001__initial_schema.sql

Ejecutarlo una sola vez desde pgAdmin Query Tool sobre una base vacía llamada la_exclusiva. No ejecutarlo dos veces ni sobre una base que ya tenga tablas del proyecto. La migración no inserta productos de catálogo; ese trabajo corresponde al inicializador Java idempotente.

## Pruebas de integración

Las pruebas que requieren PostgreSQL deben usar una base separada llamada exactamente la_exclusiva_test, nunca la base local de desarrollo. Reutilizan las variables LAEXCLUSIVA_DB_URL, LAEXCLUSIVA_DB_USER y LAEXCLUSIVA_DB_PASSWORD, pero el URL debe apuntar a /la_exclusiva_test.

Si falta alguna variable, las pruebas de integración se omiten. Si las variables están presentes pero el URL no apunta a la_exclusiva_test, la prueba debe fallar antes de ejecutar SQL destructivo. Nunca ejecutar TRUNCATE, DROP o limpieza de fixtures contra una base que no tenga ese nombre.

## División de responsabilidades

- Carlos entrega migración, dependencia, configuración, proveedor de conexiones y coordinador de transacciones.
- Jesús implementa los DAO de producto, inventario y movimientos, SalePresentation.fromCode, las operaciones de precios y el inicializador de catálogo.
- La integración de transacciones en servicios y la sustitución de compensaciones manuales se revisa después, sobre la misma API.
- Daniel puede continuar el esqueleto JavaFX sin ejecutar SQL ni asumir tareas DAO/JDBC.
