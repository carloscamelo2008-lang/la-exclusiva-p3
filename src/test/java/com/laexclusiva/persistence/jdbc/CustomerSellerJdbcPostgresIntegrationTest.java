package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.jdbc.CustomerBalanceAdjusterJdbc;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.ConcurrentModificationException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Prueba opcional de clientes, vendedores y saldo sobre la base PostgreSQL aislada. */
class CustomerSellerJdbcPostgresIntegrationTest {

    @Test
    void persistsRecordsAndAdjustsCustomerBalanceAtomically() throws Exception {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");

        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(provider);
        CustomerRepositoryJdbc customers = new CustomerRepositoryJdbc(provider);
        SellerRepositoryJdbc sellers = new SellerRepositoryJdbc(provider);
        CustomerBalanceAdjusterJdbc balances = new CustomerBalanceAdjusterJdbc(provider);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String customerId = "C-JDBC-" + token;
        String sellerId = "SEL-JDBC-" + token;
        boolean customerCreated = false;
        boolean sellerCreated = false;

        try {
            Customer customer = new Customer(customerId, "Cliente de prueba", "3000000000",
                    "Dirección ficticia", new BigDecimal("100.00"));
            customers.save(customer);
            customerCreated = true;

            Seller seller = new Seller(sellerId, "Vendedor de prueba", "3000000001",
                    "Dirección ficticia");
            sellers.save(seller);
            sellerCreated = true;

            assertTrue(customers.existsById(customerId));
            assertEquals(customerId, customers.findById(customerId).getId());
            assertTrue(customers.findAll().stream().anyMatch(row -> row.getId().equals(customerId)));
            assertFalse(customers.existsById(null));
            assertThrows(IllegalArgumentException.class,
                    () -> customers.save(new Customer(customerId, "Otro nombre", "3000000002",
                            "Otra dirección", new BigDecimal("900.00"))));
            assertEquals("Cliente de prueba", customers.findById(customerId).getName());

            customers.update(new Customer(customerId, "Cliente actualizado", "3000000003",
                    "Dirección actualizada", new BigDecimal("100.00")));
            assertEquals("Cliente actualizado", customers.findById(customerId).getName());

            Customer staleSnapshot = customers.findById(customerId);
            assertMoney("20.00", balances.decreaseBalance(customerId, new BigDecimal("80.00")));
            assertThrows(ConcurrentModificationException.class, () -> customers.update(staleSnapshot));
            Customer afterStaleUpdate = customers.findById(customerId);
            assertEquals("Cliente actualizado", afterStaleUpdate.getName());
            assertMoney("20.00", afterStaleUpdate.getBalance());

            assertThrows(IllegalArgumentException.class,
                    () -> customers.update(new Customer("C-AUSENTE-" + token, "Ausente", "3000000004",
                            "Dirección ficticia", BigDecimal.ZERO)));

            assertEquals(sellerId, sellers.findById(sellerId).orElseThrow().getId());
            assertTrue(sellers.findAll().stream().anyMatch(row -> row.getId().equals(sellerId)));
            assertTrue(sellers.findById("SEL-AUSENTE-" + token).isEmpty());
            assertThrows(IllegalArgumentException.class,
                    () -> sellers.save(new Seller(sellerId, "No reemplazar", "3000000005",
                            "Otra dirección")));
            assertEquals("Vendedor de prueba", sellers.findById(sellerId).orElseThrow().getName());

            assertThrows(IllegalStateException.class, () -> transactionManager.inTransaction(() -> {
                customers.update(new Customer(customerId, "Nombre temporal", "3000000099",
                        "Dirección temporal", new BigDecimal("20.00")));
                balances.increaseBalance(customerId, new BigDecimal("13.00"));
                throw new IllegalStateException("Rollback de prueba");
            }));
            Customer afterRollback = customers.findById(customerId);
            assertEquals("Cliente actualizado", afterRollback.getName());
            assertMoney("20.00", afterRollback.getBalance());

            assertMoney("100.00", balances.increaseBalance(customerId, new BigDecimal("80.00")));

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> first = executor.submit(() -> attemptPayment(balances, customerId, start));
                Future<Boolean> second = executor.submit(() -> attemptPayment(balances, customerId, start));
                start.countDown();
                assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            } finally {
                executor.shutdownNow();
            }
            assertMoney("20.00", customers.findById(customerId).getBalance());
            assertThrows(IllegalArgumentException.class,
                    () -> balances.decreaseBalance(customerId, new BigDecimal("20.01")));
            assertMoney("20.00", customers.findById(customerId).getBalance());
        } finally {
            if (sellerCreated) {
                deleteOwnedFixture(provider, "DELETE FROM seller WHERE id = ?", sellerId);
            }
            if (customerCreated) {
                deleteOwnedFixture(provider, "DELETE FROM customer WHERE id = ?", customerId);
            }
        }
    }

    private static boolean attemptPayment(CustomerBalanceAdjusterJdbc balances, String customerId,
            CountDownLatch start) throws Exception {
        start.await();
        try {
            balances.decreaseBalance(customerId, new BigDecimal("80.00"));
            return true;
        } catch (IllegalArgumentException insufficientBalance) {
            return false;
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static void deleteOwnedFixture(JdbcConnectionProvider provider, String sql, String id) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }
}
