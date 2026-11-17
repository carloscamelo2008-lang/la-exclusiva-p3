package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.InventoryItem;
import com.laexclusiva.model.SalePresentation;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas opcionales contra la base PostgreSQL dedicada a integración. */
class InventoryRepositoryJdbcPostgresIntegrationTest {
    @Test
    void insertsAndUpdatesOneRowPerProductAndReadsZeroStock() {
        JdbcConnectionProvider provider = testProvider();
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        ProductRepositoryJdbc products = new ProductRepositoryJdbc(provider, transactions);
        InventoryRepositoryJdbc inventory = new InventoryRepositoryJdbc(provider, transactions);
        BeerProduct product = product();
        String id = product.getId();
        String missingId = "INVENTORY-MISSING-" + UUID.randomUUID();

        try {
            products.save(product);
            assertTrue(inventory.findByProductId(id).isEmpty());
            assertTrue(inventory.findByProductId(missingId).isEmpty());
            assertThrows(IllegalStateException.class, () -> inventory.findByProductIdForUpdate(id));
            assertTrue(transactions.inTransaction(() -> inventory.findByProductIdForUpdate(id)).isEmpty());

            InventoryItem item = new InventoryItem(product);
            inventory.save(item);
            assertEquals(0, inventory.findByProductId(id).orElseThrow().totalUnits());
            item.add(31);
            inventory.save(item);

            assertEquals(1, countRows(provider, id));
            assertItem(product, inventory.findByProductId(id).orElseThrow(), 31);
            assertItem(product, inventory.findAll().stream()
                    .filter(candidate -> candidate.getProduct().getId().equals(id))
                    .findFirst().orElseThrow(), 31);
        } finally {
            cleanup(provider, id);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void secondTransactionWaitsUntilFirstCommitsOrRollsBack(boolean commit) throws Exception {
        JdbcConnectionProvider provider = testProvider();
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        ProductRepositoryJdbc products = new ProductRepositoryJdbc(provider, transactions);
        InventoryRepositoryJdbc inventory = new InventoryRepositoryJdbc(provider, transactions);
        BeerProduct product = product();
        String id = product.getId();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RuntimeException abort = new IllegalStateException("Rollback de prueba.");
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        CompletableFuture<Future<Integer>> secondResult = new CompletableFuture<>();

        try {
            products.save(product);
            InventoryItem initial = new InventoryItem(product);
            initial.add(3);
            inventory.save(initial);

            Runnable first = () -> transactions.inTransaction(() -> {
                InventoryItem locked = inventory.findByProductIdForUpdate(id).orElseThrow();
                int firstPid = backendPid(provider);
                secondResult.complete(executor.submit(() -> transactions.inTransaction(() -> {
                    try {
                        provider.withConnection(connection -> {
                            try (PreparedStatement timeout = connection.prepareStatement(
                                    "SET LOCAL statement_timeout = '15s'")) {
                                timeout.execute();
                            }
                            return null;
                        });
                        secondPid.complete(backendPid(provider));
                        return inventory.findByProductIdForUpdate(id).orElseThrow().totalUnits();
                    } catch (RuntimeException | Error failure) {
                        secondPid.completeExceptionally(failure);
                        throw failure;
                    }
                })));
                int waitingPid = assertDoesNotThrow(() -> secondPid.get(5, TimeUnit.SECONDS));
                awaitBlockedBy(provider, waitingPid, firstPid);
                assertFalse(secondResult.join().isDone());
                locked.add(4);
                inventory.save(locked);
                if (!commit) {
                    throw abort;
                }
            });

            if (commit) {
                first.run();
            } else {
                assertSame(abort, assertThrows(IllegalStateException.class, first::run));
            }
            int expected = commit ? 7 : 3;
            assertEquals(expected, secondResult.join().get(10, TimeUnit.SECONDS).intValue());
            assertEquals(expected, inventory.findByProductId(id).orElseThrow().totalUnits());
        } finally {
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            } finally {
                cleanup(provider, id);
            }
        }
    }

    private static int backendPid(JdbcConnectionProvider provider) {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        });
    }

    private static void awaitBlockedBy(JdbcConnectionProvider provider, int waitingPid, int blockingPid) {
        boolean blocked = provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))")) {
                statement.setInt(1, blockingPid);
                statement.setInt(2, waitingPid);
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (System.nanoTime() < deadline) {
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        if (rows.getBoolean(1)) {
                            return true;
                        }
                    }
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return fail("Se interrumpió la espera del bloqueo de inventario.", interrupted);
                    }
                }
                return false;
            }
        });
        assertTrue(blocked, "PostgreSQL debe mostrar que la primera transacción bloquea a la segunda.");
    }

    private static JdbcConnectionProvider testProvider() {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        return new JdbcConnectionProvider(DatabaseConfig.fromTestEnvironment());
    }

    private static BeerProduct product() {
        BeerProduct product = new BeerProduct("INVENTORY-TST-" + UUID.randomUUID(),
                "Producto de prueba", 330, 24, ContainerType.RETURNABLE_SHARED, "envases-prueba");
        product.definePrice(SalePresentation.UNIT, new BigDecimal("1250.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("12000.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("22000.00"));
        return product;
    }

    private static void assertItem(BeerProduct expected, InventoryItem item, int totalUnits) {
        assertEquals(totalUnits, item.totalUnits());
        BeerProduct actual = item.getProduct();
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getVolumeMl(), actual.getVolumeMl());
        assertEquals(expected.getUnitsPerBasket(), actual.getUnitsPerBasket());
        assertEquals(expected.getContainerType(), actual.getContainerType());
        assertEquals(expected.getContainerGroup(), actual.getContainerGroup());
        assertEquals(expected.priceFor(SalePresentation.UNIT), actual.priceFor(SalePresentation.UNIT));
        assertEquals(expected.priceFor(SalePresentation.HALF_BASKET), actual.priceFor(SalePresentation.HALF_BASKET));
        assertEquals(expected.priceFor(SalePresentation.FULL_BASKET), actual.priceFor(SalePresentation.FULL_BASKET));
    }

    private static int countRows(JdbcConnectionProvider provider, String id) {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM inventory_item WHERE product_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getInt(1);
                }
            }
        });
    }

    private static void cleanup(JdbcConnectionProvider provider, String id) {
        provider.withConnection(connection -> {
            for (String sql : new String[]{"DELETE FROM inventory_item WHERE product_id = ?",
                    "DELETE FROM product_price WHERE product_id = ?", "DELETE FROM beer_product WHERE id = ?"}) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }
}
