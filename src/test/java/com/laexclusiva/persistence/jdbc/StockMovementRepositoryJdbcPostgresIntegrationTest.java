package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.InventoryItem;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.StockMovement;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Pruebas opcionales contra la base PostgreSQL dedicada a integración. */
class StockMovementRepositoryJdbcPostgresIntegrationTest {
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 2, 12, 0, 0, 123_456_789);

    @Test
    void persistsEntriesAndExitsWithGeneratedIdsAndMicrosecondPrecision() {
        JdbcConnectionProvider provider = testProvider();
        ProductRepositoryJdbc products = new ProductRepositoryJdbc(provider, new JdbcTransactionManager(provider));
        StockMovementRepositoryJdbc movements = new StockMovementRepositoryJdbc(provider);
        BeerProduct product = product();
        BeerProduct otherProduct = product();
        String id = product.getId();
        StockMovement entry = new StockMovement(id, MovementType.ENTRY, 12, "Recepción", OCCURRED_AT);

        try {
            products.save(product);
            products.save(otherProduct);
            long exitId = movements.saveAndReturnId(
                    new StockMovement(id, MovementType.EXIT, 3, "Venta", OCCURRED_AT.plusSeconds(1)));
            long entryId = movements.saveAndReturnId(entry);
            long sameTimeId = movements.saveAndReturnId(
                    new StockMovement(id, MovementType.ENTRY, 2, "Ajuste", OCCURRED_AT.plusNanos(100)));
            movements.save(new StockMovement(otherProduct.getId(), MovementType.ENTRY, 1, "Otro", OCCURRED_AT));

            List<StockMovement> history = movements.findByProductId(id);
            assertEquals(List.of(entryId, sameTimeId, exitId), history.stream().map(StockMovement::id).toList());
            assertEquals(history, movements.findAll().stream().filter(row -> row.productId().equals(id)).toList());
            assertEquals(1, movements.findByProductId(otherProduct.getId()).size());
            assertTrue(movements.findByProductId("MISSING-" + UUID.randomUUID()).isEmpty());
            StockMovement stored = history.get(0);
            assertEquals(MovementType.ENTRY, stored.type());
            assertEquals(12, stored.units());
            assertEquals("Recepción", stored.reason());
            assertEquals(OCCURRED_AT.withNano(123_456_000), stored.timestamp());
            assertEquals(stored.timestamp(), history.get(1).timestamp());
            assertEquals(MovementType.EXIT, history.get(2).type());
            assertEquals(3, history.get(2).units());
            assertEquals(id, stored.product().getId());
            assertEquals(product.getName(), stored.product().getName());
            assertEquals(product.priceFor(SalePresentation.UNIT), stored.product().priceFor(SalePresentation.UNIT));
            assertTrue(stored.product().hasAllPrices());
            assertThrows(UnsupportedOperationException.class, () -> movements.remove(stored));
            assertEquals(3, movements.findByProductId(id).size());
        } finally {
            cleanup(provider, id);
            cleanup(provider, otherProduct.getId());
        }
    }

    @Test
    void rollsBackInventoryAndMovementTogether() {
        JdbcConnectionProvider provider = testProvider();
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        ProductRepositoryJdbc products = new ProductRepositoryJdbc(provider, transactions);
        InventoryRepositoryJdbc inventory = new InventoryRepositoryJdbc(provider, transactions);
        StockMovementRepositoryJdbc movements = new StockMovementRepositoryJdbc(provider);
        BeerProduct product = product();
        String id = product.getId();
        IllegalStateException abort = new IllegalStateException("Rollback de prueba.");

        try {
            products.save(product);
            InventoryItem item = new InventoryItem(product);
            item.add(5);
            transactions.inTransaction(() -> inventory.save(item));
            movements.save(new StockMovement(id, MovementType.ENTRY, 5, "Inicial", OCCURRED_AT));

            assertSame(abort, assertThrows(IllegalStateException.class, () -> transactions.inTransaction(() -> {
                InventoryItem locked = inventory.findByProductIdForUpdate(id).orElseThrow();
                locked.remove(2);
                inventory.save(locked);
                movements.save(new StockMovement(id, MovementType.EXIT, 2, "Venta", OCCURRED_AT.plusSeconds(1)));
                assertEquals(3, inventory.findByProductId(id).orElseThrow().totalUnits());
                assertEquals(2, movements.findByProductId(id).size());
                throw abort;
            })));

            assertEquals(5, inventory.findByProductId(id).orElseThrow().totalUnits());
            assertEquals(List.of("Inicial"), movements.findByProductId(id).stream().map(StockMovement::reason).toList());
        } finally {
            cleanup(provider, id);
        }
    }

    private static JdbcConnectionProvider testProvider() {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        return new JdbcConnectionProvider(DatabaseConfig.fromTestEnvironment());
    }

    private static BeerProduct product() {
        BeerProduct product = new BeerProduct("MOVEMENT-TST-" + UUID.randomUUID(),
                "Producto de prueba", 330, 24, ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal("1250.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("12000.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("22000.00"));
        return product;
    }

    private static void cleanup(JdbcConnectionProvider provider, String id) {
        provider.withConnection(connection -> {
            for (String sql : List.of("DELETE FROM stock_movement WHERE product_id = ?",
                    "DELETE FROM inventory_item WHERE product_id = ?",
                    "DELETE FROM product_price WHERE product_id = ?",
                    "DELETE FROM beer_product WHERE id = ?")) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }
}
