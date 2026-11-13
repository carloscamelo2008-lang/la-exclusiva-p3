package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.jdbc.SaleRepositoryJdbc;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Prueba opcional contra PostgreSQL real y únicamente sobre la base aislada de pruebas. */
class SaleRepositoryJdbcPostgresIntegrationTest {

    @Test
    void persistsAndAppendsPaymentsIdempotentlyAgainstTheDedicatedTestDatabase() throws Exception {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");

        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(provider);
        SaleRepositoryJdbc repository = new SaleRepositoryJdbc(provider, transactionManager);

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String sellerId = "SEL-TST-" + token;
        String customerId = "C-TST-" + token;
        String productId = "P-TST-" + token;
        String saleId = "SALE-TST-" + token;
        boolean sellerCreated = false;
        boolean customerCreated = false;
        boolean productCreated = false;
        boolean saleCreated = false;

        try {
            insertFixture(provider, """
                    INSERT INTO seller (id, name, phone, address) VALUES (?, ?, ?, ?)
                    """, sellerId, "Vendedor de prueba", "3000000000", "Dirección ficticia");
            sellerCreated = true;
            insertFixture(provider, """
                    INSERT INTO customer (id, name, phone, address, balance) VALUES (?, ?, ?, ?, ?)
                    """, customerId, "Cliente de prueba", "3000000001", "Dirección ficticia",
                    BigDecimal.ZERO.setScale(2));
            customerCreated = true;
            insertFixture(provider, """
                    INSERT INTO beer_product
                        (id, name, volume_ml, units_per_basket, container_type, container_group)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, productId, "Producto de prueba", 330, 24,
                    ContainerType.NON_RETURNABLE_CAN.name(), null);
            productCreated = true;

            Seller seller = new Seller(sellerId, "Vendedor de prueba", "3000000000", "Dirección ficticia");
            Customer customer = new Customer(customerId, "Cliente de prueba", "3000000001",
                    "Dirección ficticia", BigDecimal.ZERO);
            BeerProduct product = new BeerProduct(productId, "Producto de prueba", 330, 24,
                    ContainerType.NON_RETURNABLE_CAN, null);
            LocalDateTime createdAt = LocalDateTime.now().withNano(0);
            Sale sale = Sale.reconstitute(saleId, createdAt, seller, customer,
                    List.of(SaleLine.reconstitute(product, SalePresentation.UNIT, 2,
                            new BigDecimal("1250.00"), 24)), List.of());

            repository.save(sale);
            saleCreated = true;
            Sale loaded = repository.findById(saleId).orElseThrow();
            assertEquals(saleId, loaded.getId());
            assertEquals(new BigDecimal("1250.00"), loaded.getLines().get(0).getUnitPrice());
            assertEquals(24, loaded.getLines().get(0).getUnitsPerBasketSnapshot());

            SalePayment first = repository.appendPayment(saleId, new BigDecimal("500.00"), "retry-" + token);
            SalePayment replay = repository.appendPayment(saleId, new BigDecimal("500.00"), "retry-" + token);
            assertEquals(first.getTimestamp(), replay.getTimestamp());
            assertEquals(1, repository.findById(saleId).orElseThrow().getPayments().size());

            assertEquals("23505", sqlStateInCause(tryDuplicate(provider, saleId, first, "retry-" + token)));

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<SalePayment> one = pool.submit(() -> { start.await();
                    return repository.appendPayment(saleId, new BigDecimal("100.00"), "concurrent-" + token); });
                Future<SalePayment> two = pool.submit(() -> { start.await();
                    return repository.appendPayment(saleId, new BigDecimal("100.00"), "concurrent-" + token); });
                start.countDown();
                assertEquals(one.get().getTimestamp(), two.get().getTimestamp());
            } finally {
                pool.shutdownNow();
            }
            assertEquals(2, repository.findById(saleId).orElseThrow().getPayments().size());

            CountDownLatch overpayStart = new CountDownLatch(1);
            ExecutorService overpayPool = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> one = overpayPool.submit(() -> attemptPayment(repository, saleId,
                        "race-a-" + token, overpayStart));
                Future<Boolean> two = overpayPool.submit(() -> attemptPayment(repository, saleId,
                        "race-b-" + token, overpayStart));
                overpayStart.countDown();
                assertEquals(1, (one.get() ? 1 : 0) + (two.get() ? 1 : 0));
            } finally {
                overpayPool.shutdownNow();
            }
            Sale afterConcurrentPayments = repository.findById(saleId).orElseThrow();
            assertEquals(3, afterConcurrentPayments.getPayments().size());
            assertTrue(afterConcurrentPayments.calculateBalance().compareTo(BigDecimal.ZERO) >= 0);
            assertThrows(UnsupportedOperationException.class, () -> repository.deleteById(saleId));
        } finally {
            cleanupFixture(provider, saleId, customerId, sellerId, productId,
                    saleCreated, customerCreated, sellerCreated, productCreated);
        }
    }

    private static boolean attemptPayment(SaleRepositoryJdbc repository, String saleId,
                                          String key, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            repository.appendPayment(saleId, new BigDecimal("1000.00"), key);
            return true;
        } catch (IllegalArgumentException overBalance) {
            return false;
        }
    }

    private static RuntimeException tryDuplicate(JdbcConnectionProvider provider, String saleId,
                                                  SalePayment original, String key) {
        return assertThrows(JdbcPersistenceException.class, () -> insertFixture(provider, """
                INSERT INTO sale_payment (sale_id, amount, occurred_at, idempotency_key)
                VALUES (?, ?, ?, ?)
                """, saleId, original.getAmount(), original.getTimestamp(), key));
    }

    private static String sqlStateInCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) return sqlException.getSQLState();
            current = current.getCause();
        }
        return null;
    }

    private static void insertFixture(JdbcConnectionProvider provider, String sql, Object... values) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) {
                    statement.setObject(index + 1, values[index]);
                }
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void cleanupFixture(JdbcConnectionProvider provider, String saleId, String customerId,
                                       String sellerId, String productId, boolean saleCreated,
                                       boolean customerCreated, boolean sellerCreated, boolean productCreated) {
        provider.withConnection(connection -> {
            if (saleCreated) {
                deleteById(connection, "DELETE FROM sale_payment WHERE sale_id = ?", saleId);
                deleteById(connection, "DELETE FROM sale_line WHERE sale_id = ?", saleId);
                deleteById(connection, "DELETE FROM sale WHERE id = ?", saleId);
            }
            if (customerCreated) deleteById(connection, "DELETE FROM customer WHERE id = ?", customerId);
            if (sellerCreated) deleteById(connection, "DELETE FROM seller WHERE id = ?", sellerId);
            if (productCreated) deleteById(connection, "DELETE FROM beer_product WHERE id = ?", productId);
            return null;
        });
    }

    private static void deleteById(java.sql.Connection connection, String sql, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }
}
