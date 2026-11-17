package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.SalePresentation;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Pruebas opcionales contra la base PostgreSQL dedicada a integración. */
class ProductRepositoryJdbcPostgresIntegrationTest {
    @Test
    void persistsReadsAndChangesPrices() {
        JdbcConnectionProvider provider = testProvider();
        ProductRepositoryJdbc repository = new ProductRepositoryJdbc(
                provider, new JdbcTransactionManager(provider));
        String id = "PRODUCT-TST-" + UUID.randomUUID();
        BeerProduct product = product(id);
        try {
            repository.save(product);
            assertTrue(repository.existsById(id));
            assertEquals(3, countRows(provider, "SELECT count(*) FROM product_price WHERE product_id = ?", id));
            assertProduct(product, repository.findById(id).orElseThrow());
            assertProduct(product, repository.findAll().stream()
                    .filter(candidate -> candidate.getId().equals(id)).findFirst().orElseThrow());

            assertThrows(IllegalArgumentException.class, () -> repository.save(product));
            assertProduct(product, repository.findById(id).orElseThrow());
            assertThrows(IllegalArgumentException.class,
                    () -> repository.updatePrice(id, SalePresentation.UNIT, new BigDecimal("1.001")));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.updatePrice(id, SalePresentation.UNIT, new BigDecimal("100000000000000000.00")));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.addMissingPrice(id, SalePresentation.UNIT, BigDecimal.TEN));

            repository.updatePrice(id, SalePresentation.UNIT, new BigDecimal("1300.25"));
            assertEquals(new BigDecimal("1300.25"),
                    repository.findById(id).orElseThrow().priceFor(SalePresentation.UNIT));

            execute(provider, "DELETE FROM product_price WHERE product_id = ? AND presentation_code = 'FULL_BASKET'", id);
            assertFalse(repository.findById(id).orElseThrow().hasAllPrices());
            assertThrows(IllegalArgumentException.class,
                    () -> repository.updatePrice(id, SalePresentation.FULL_BASKET, BigDecimal.TEN));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.addMissingPrice(id, SalePresentation.FULL_BASKET, new BigDecimal("1.001")));
            repository.addMissingPrice(id, SalePresentation.FULL_BASKET, new BigDecimal("20000.00"));
            BeerProduct repaired = repository.findById(id).orElseThrow();
            assertTrue(repaired.hasAllPrices());
            assertEquals(new BigDecimal("20000.00"), repaired.priceFor(SalePresentation.FULL_BASKET));
        } finally {
            execute(provider, "DELETE FROM product_price WHERE product_id = ?", id);
            execute(provider, "DELETE FROM beer_product WHERE id = ?", id);
        }
        assertFalse(repository.existsById(id));
        assertTrue(repository.findById(id).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> repository.updatePrice(id, SalePresentation.UNIT, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> repository.addMissingPrice(id, SalePresentation.UNIT, BigDecimal.TEN));
    }

    @Test
    void joinsOuterTransactionAndRollsBackProductAndPrices() {
        JdbcConnectionProvider provider = testProvider();
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        ProductRepositoryJdbc repository = new ProductRepositoryJdbc(provider, transactions);
        String id = "PRODUCT-RBK-" + UUID.randomUUID();
        BeerProduct product = product(id);
        try {
            assertThrows(IllegalArgumentException.class, () -> transactions.inTransaction(() -> {
                repository.save(product);
                assertProduct(product, repository.findById(id).orElseThrow());
                repository.addMissingPrice(id, SalePresentation.UNIT, BigDecimal.TEN);
            }));
            assertFalse(repository.existsById(id));
            assertEquals(0, countRows(provider, "SELECT count(*) FROM product_price WHERE product_id = ?", id));
        } finally {
            execute(provider, "DELETE FROM product_price WHERE product_id = ?", id);
            execute(provider, "DELETE FROM beer_product WHERE id = ?", id);
        }
    }

    @Test
    void rollsBackStandaloneSaveWhenTheThirdPriceInsertFails() {
        DatabaseConfig config = testConfig();
        String id = "PRODUCT-FAIL-" + UUID.randomUUID();
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcConnectionProvider failingProvider = new JdbcConnectionProvider(config, () -> {
            var connection = provider.openConnection();
            try (var statement = connection.createStatement()) {
                // La restricción solo existe en esta conexión y falla al insertar el tercer precio.
                statement.execute("""
                        CREATE TEMP TABLE product_price (
                            LIKE product_price INCLUDING ALL,
                            CHECK (presentation_code <> 'FULL_BASKET')
                        )
                        """);
                return connection;
            } catch (SQLException failure) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        });
        ProductRepositoryJdbc repository = new ProductRepositoryJdbc(
                failingProvider, new JdbcTransactionManager(failingProvider));
        try {
            JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class,
                    () -> repository.save(product(id)));
            assertEquals("23514", ((SQLException) failure.getCause()).getSQLState());
            assertEquals(0, countRows(provider, "SELECT count(*) FROM beer_product WHERE id = ?", id));
            assertEquals(0, countRows(provider, "SELECT count(*) FROM product_price WHERE product_id = ?", id));
        } finally {
            execute(provider, "DELETE FROM product_price WHERE product_id = ?", id);
            execute(provider, "DELETE FROM beer_product WHERE id = ?", id);
        }
    }

    private static JdbcConnectionProvider testProvider() {
        return new JdbcConnectionProvider(testConfig());
    }

    private static DatabaseConfig testConfig() {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        return DatabaseConfig.fromTestEnvironment();
    }

    private static BeerProduct product(String id) {
        BeerProduct product = new BeerProduct(id, "Producto de prueba", 330, 24,
                ContainerType.RETURNABLE_SHARED, "envases-prueba");
        product.definePrice(SalePresentation.UNIT, new BigDecimal("1250.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("12000.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("22000.00"));
        return product;
    }

    private static void assertProduct(BeerProduct expected, BeerProduct actual) {
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

    private static int countRows(JdbcConnectionProvider provider, String sql, String id) {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getInt(1);
                }
            }
        });
    }

    private static void execute(JdbcConnectionProvider provider, String sql, String id) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }
}
