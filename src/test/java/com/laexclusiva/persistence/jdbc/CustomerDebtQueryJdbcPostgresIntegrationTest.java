package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.jdbc.CustomerDebtQueryJdbc;
import com.laexclusiva.repository.jdbc.SaleRepositoryJdbc;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Prueba opcional en PostgreSQL real; modifica y limpia únicamente fixtures con IDs aleatorios. */
class CustomerDebtQueryJdbcPostgresIntegrationTest {

    @Test
    void calculatesOnlyOutstandingSalesDebtFromIndependentAggregates() {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");

        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(provider);
        SaleRepositoryJdbc sales = new SaleRepositoryJdbc(provider, transactionManager);
        CustomerDebtQueryJdbc query = new CustomerDebtQueryJdbc(provider);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String sellerId = "SEL-CQ-" + token;
        String customerId = "C-CQ-" + token;
        String otherCustomerId = "C-OTHER-" + token;
        String noSalesCustomerId = "C-NOSALES-" + token;
        String productId = "P-CQ-" + token;
        List<String> saleIds = List.of("SALE-CQ-A-" + token, "SALE-CQ-B-" + token,
                "SALE-CQ-F-" + token, "SALE-CQ-O-" + token, "SALE-CQ-OVERPAID-" + token);
        boolean sellerCreated = false;
        boolean customerCreated = false;
        boolean otherCustomerCreated = false;
        boolean noSalesCustomerCreated = false;
        boolean productCreated = false;
        List<String> createdSales = new ArrayList<>();

        try {
            insertFixture(provider, "INSERT INTO seller (id, name, phone, address) VALUES (?, ?, ?, ?)",
                    sellerId, "Vendedor ficticio", "3000000000", "Dirección de prueba");
            sellerCreated = true;
            insertFixture(provider, """
                    INSERT INTO customer (id, name, phone, address, balance) VALUES (?, ?, ?, ?, ?)
                    """, customerId, "Cliente ficticio", "3000000001", "Dirección de prueba",
                    new BigDecimal("7777.77"));
            customerCreated = true;
            insertFixture(provider, """
                    INSERT INTO customer (id, name, phone, address, balance) VALUES (?, ?, ?, ?, ?)
                    """, otherCustomerId, "Otro cliente ficticio", "3000000002", "Dirección de prueba",
                    new BigDecimal("8888.88"));
            otherCustomerCreated = true;
            insertFixture(provider, """
                    INSERT INTO customer (id, name, phone, address, balance) VALUES (?, ?, ?, ?, ?)
                    """, noSalesCustomerId, "Cliente sin ventas", "3000000003", "Dirección de prueba",
                    new BigDecimal("345.67"));
            noSalesCustomerCreated = true;
            insertFixture(provider, """
                    INSERT INTO beer_product
                        (id, name, volume_ml, units_per_basket, container_type, container_group)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, productId, "Producto ficticio", 330, 24,
                    ContainerType.NON_RETURNABLE_CAN.name(), null);
            productCreated = true;

            Seller seller = new Seller(sellerId, "Vendedor ficticio", "3000000000", "Dirección de prueba");
            Customer customer = new Customer(customerId, "Cliente ficticio", "3000000001",
                    "Dirección de prueba", new BigDecimal("7777.77"));
            Customer otherCustomer = new Customer(otherCustomerId, "Otro cliente ficticio", "3000000002",
                    "Dirección de prueba", new BigDecimal("8888.88"));
            BeerProduct product = new BeerProduct(productId, "Producto ficticio", 330, 24,
                    ContainerType.NON_RETURNABLE_CAN, null);
            LocalDateTime at = LocalDateTime.of(2026, 11, 16, 9, 0);

            List<Sale> fixtures = List.of(
                    sale(saleIds.get(0), at, seller, customer,
                            List.of(line(product, 1, "100.00"), line(product, 1, "100.00")),
                            List.of(payment("50.00", at), payment("25.00", at.plusMinutes(1)))),
                    sale(saleIds.get(1), at.plusDays(1), seller, customer,
                            List.of(line(product, 3, "75.00")),
                            List.of(payment("25.00", at.plusDays(1)), payment("100.00", at.plusDays(1).plusMinutes(1)))),
                    sale(saleIds.get(2), at.plusDays(2), seller, customer,
                            List.of(line(product, 1, "150.00")),
                            List.of(payment("150.00", at.plusDays(2)))),
                    sale(saleIds.get(3), at.plusDays(3), seller, otherCustomer,
                            List.of(line(product, 6, "150.00")), List.of()));

            for (Sale fixture : fixtures) {
                sales.save(fixture);
                createdSales.add(fixture.getId());
            }

            // Representa historial inconsistente; el modelo y SalePaymentAppender rechazan este sobrepago.
            String overpaidSaleId = saleIds.get(4);
            insertFixture(provider, """
                    INSERT INTO sale (id, created_at, seller_id, customer_id) VALUES (?, ?, ?, ?)
                    """, overpaidSaleId, at.plusDays(4), sellerId, customerId);
            createdSales.add(overpaidSaleId);
            insertFixture(provider, """
                    INSERT INTO sale_line
                        (sale_id, product_id, presentation_code, quantity, units_per_basket_snapshot, unit_price)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, overpaidSaleId, productId, SalePresentation.UNIT.code(), 1, 24,
                    new BigDecimal("50.00"));
            insertFixture(provider, """
                    INSERT INTO sale_payment (sale_id, amount, occurred_at) VALUES (?, ?, ?)
                    """, overpaidSaleId, new BigDecimal("80.00"), at.plusDays(4));

            int linesBefore = countRows(provider, "sale_line", saleIds);
            int paymentsBefore = countRows(provider, "sale_payment", saleIds);
            // Las dos ventas pendientes aportan 125 y 100; el sobrepago y la venta pagada aportan cero.
            assertEquals(0, query.findOutstandingSalesDebt(customerId).compareTo(new BigDecimal("225.00")));
            assertEquals(0, query.findOutstandingSalesDebt(otherCustomerId).compareTo(new BigDecimal("900.00")));
            assertEquals(0, query.findOutstandingSalesDebt(noSalesCustomerId).compareTo(BigDecimal.ZERO));
            assertEquals(linesBefore, countRows(provider, "sale_line", saleIds));
            assertEquals(paymentsBefore, countRows(provider, "sale_payment", saleIds));
        } finally {
            cleanup(provider, createdSales, customerId, otherCustomerId, noSalesCustomerId, sellerId, productId,
                    customerCreated, otherCustomerCreated, noSalesCustomerCreated, sellerCreated, productCreated);
        }
    }

    private static Sale sale(String id, LocalDateTime at, Seller seller, Customer customer,
                             List<SaleLine> lines, List<SalePayment> payments) {
        return Sale.reconstitute(id, at, seller, customer, lines, payments);
    }

    private static SaleLine line(BeerProduct product, int quantity, String price) {
        return SaleLine.reconstitute(product, SalePresentation.UNIT, quantity, new BigDecimal(price), 24);
    }

    private static SalePayment payment(String amount, LocalDateTime at) {
        return new SalePayment(new BigDecimal(amount), at);
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

    private static int countRows(JdbcConnectionProvider provider, String table, List<String> saleIds) {
        if (!List.of("sale_line", "sale_payment").contains(table) || saleIds.isEmpty()) {
            throw new IllegalArgumentException("La tabla o las ventas no son válidas para contar fixtures.");
        }
        String placeholders = String.join(", ", Collections.nCopies(saleIds.size(), "?"));
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + table + " WHERE sale_id IN (" + placeholders + ")")) {
                for (int index = 0; index < saleIds.size(); index++) {
                    statement.setString(index + 1, saleIds.get(index));
                }
                try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) throw new SQLException("No se obtuvo el conteo del fixture.");
                    return resultSet.getInt(1);
                }
            }
        });
    }

    private static void cleanup(JdbcConnectionProvider provider, List<String> createdSales,
                                String customerId, String otherCustomerId, String noSalesCustomerId,
                                String sellerId, String productId,
                                boolean customerCreated, boolean otherCustomerCreated, boolean noSalesCustomerCreated,
                                boolean sellerCreated, boolean productCreated) {
        provider.withConnection(connection -> {
            for (String saleId : createdSales) {
                deleteById(connection, "DELETE FROM sale_payment WHERE sale_id = ?", saleId);
                deleteById(connection, "DELETE FROM sale_line WHERE sale_id = ?", saleId);
                deleteById(connection, "DELETE FROM sale WHERE id = ?", saleId);
            }
            if (customerCreated) deleteById(connection, "DELETE FROM customer WHERE id = ?", customerId);
            if (otherCustomerCreated) deleteById(connection, "DELETE FROM customer WHERE id = ?", otherCustomerId);
            if (noSalesCustomerCreated) deleteById(connection, "DELETE FROM customer WHERE id = ?", noSalesCustomerId);
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
