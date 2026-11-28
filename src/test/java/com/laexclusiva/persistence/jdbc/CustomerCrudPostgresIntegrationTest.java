package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerDeletionBlockedException;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.service.CustomerService;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomerCrudPostgresIntegrationTest {
    @Test
    void editsAndDeletesWithPhoneAndReferenceConstraints() {
        String url = System.getenv(DatabaseConfig.TEST_URL_ENV);
        assumeTrue(url != null && !url.isBlank(), "Configure la base aislada la_exclusiva_test.");
        var provider = new JdbcConnectionProvider(DatabaseConfig.fromTestEnvironment());
        boolean migrated = provider.withConnection(connection -> {
            try (var query = connection.prepareStatement("SELECT to_regclass('uq_customer_phone') IS NOT NULL");
                 var rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getBoolean(1);
            }
        });
        assumeTrue(migrated, "V110 debe estar instalada previamente.");
        boolean archivedCreditColumn = provider.withConnection(connection -> {
            try (var query = connection.prepareStatement("""
                    SELECT EXISTS (SELECT FROM information_schema.columns
                                   WHERE table_schema='public' AND table_name='customer'
                                     AND column_name='legacy_credit_limit')
                    """); var rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getBoolean(1);
            }
        });
        assumeTrue(archivedCreditColumn, "V113 debe conservar el cupo histórico fuera del modelo comercial.");
        var repository = new CustomerRepositoryJdbc(provider);
        var service = new CustomerService(repository);
        UUID uuid = UUID.randomUUID();
        String prefix = "CRUD-" + uuid.toString().replace("-", "").substring(0, 16);
        String phone = Long.toUnsignedString(uuid.getMostSignificantBits());
        String first = prefix + "-a";
        String second = prefix + "-b";
        try {
            service.registerCustomer("  " + first + "  ", "  Ána   María ", "+" + phone + " 1",
                    " Calle   compartida ", new BigDecimal("5000.00"));
            assertEquals(first, service.findSimilarNames("ANA MARIA").get(0).getId());
            service.registerCustomer(second, "Beto", phone + "2", "Calle compartida");
            service.updateCustomer(first, "Ana María", phone + "1", "Calle compartida");
            assertEquals("Ana María", service.getCustomerById(first).getName());
            assertEquals(0, service.getCustomerById(first).getBalance().compareTo(BigDecimal.ZERO),
                    "El saldo inicial debe ser numéricamente cero, sin depender de la escala JDBC.");
            assertEquals(0, queryDecimal(provider,
                    "SELECT legacy_credit_limit FROM customer WHERE id = ?", first).compareTo(BigDecimal.ZERO),
                    "El contrato legado no debe asignar cupos a altas nuevas.");
            assertEquals(service.getCustomerById(first).getAddress(), service.getCustomerById(second).getAddress());

            assertThrows(IllegalArgumentException.class, () ->
                    service.registerCustomer(prefix + "-c", "C", phone + "1", "Calle"));
            assertThrows(IllegalArgumentException.class, () ->
                    service.updateCustomer(second, "Beto", phone + "1", "Calle"));
            assertThrows(IllegalArgumentException.class, () ->
                    service.updateCustomer(second, "Beto", "-123", "Calle"));
            assertThrows(IllegalArgumentException.class, () ->
                    service.registerCustomer(prefix + "-d", "D", "abc", "Calle"));
            // Omite el servicio para comprobar el rechazo del índice en INSERT y UPDATE.
            assertTrue(assertThrows(IllegalArgumentException.class, () ->
                    repository.save(new Customer(prefix + "-c", "C", phone + "1", "Calle")))
                    .getMessage().contains("teléfono"));
            assertTrue(assertThrows(IllegalArgumentException.class, () ->
                    repository.update(new Customer(second, "Beto", phone + "1", "Calle")))
                    .getMessage().contains("teléfono"));

            service.deleteCustomer(first);
            assertNull(repository.findById(first));
            var balances = new com.laexclusiva.repository.jdbc.CustomerBalanceAdjusterJdbc(provider);
            balances.increaseBalance(second, BigDecimal.ONE);
            assertThrows(CustomerDeletionBlockedException.class, () -> service.deleteCustomer(second));
            balances.decreaseBalance(second, BigDecimal.ONE);

            execute(provider, """
                    INSERT INTO seller(id,name,phone,address) VALUES (?, 'Vendedor ficticio', '1', 'Calle')
                    """, prefix);
            execute(provider, """
                    INSERT INTO sale(id,created_at,seller_id,customer_id) VALUES (?, CURRENT_TIMESTAMP, ?, ?)
                    """, prefix, prefix, second);
            assertThrows(CustomerDeletionBlockedException.class, () -> service.deleteCustomer(second));
            execute(provider, "DELETE FROM sale WHERE id = ?", prefix);

            execute(provider, """
                    INSERT INTO customer_collection_reminder(customer_id,week_start,channel,balance_snapshot,generated_at)
                    VALUES (?, DATE '2026-01-05', 'PHONE', 1, CURRENT_TIMESTAMP)
                    """, second);
            assertThrows(CustomerDeletionBlockedException.class, () -> service.deleteCustomer(second));
        } finally {
            execute(provider, "DELETE FROM customer_collection_reminder WHERE customer_id = ?", second);
            execute(provider, "DELETE FROM sale WHERE id = ?", prefix);
            execute(provider, "DELETE FROM seller WHERE id = ?", prefix);
            execute(provider, "DELETE FROM customer WHERE id IN (?, ?, ?, ?)", first, second, prefix + "-c", prefix + "-d");
        }
    }

    private static void execute(JdbcConnectionProvider provider, String sql, String... args) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) statement.setString(i + 1, args[i]);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private static BigDecimal queryDecimal(JdbcConnectionProvider provider, String sql, String... args) {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) statement.setString(i + 1, args[i]);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    return rows.getBigDecimal(1);
                }
            }
        });
    }
}
