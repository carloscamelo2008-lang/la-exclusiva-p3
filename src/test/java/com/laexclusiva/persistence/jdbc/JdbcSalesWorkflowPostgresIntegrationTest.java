package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.repository.SaleRepository;
import com.laexclusiva.repository.jdbc.CustomerBalanceAdjusterJdbc;
import com.laexclusiva.repository.jdbc.CustomerDebtQueryJdbc;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SaleRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
import com.laexclusiva.service.JdbcSalesWorkflow;
import com.laexclusiva.service.SalesWorkflow;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Valida en PostgreSQL la unidad transaccional de ventas, inventario, movimientos y cartera. */
class JdbcSalesWorkflowPostgresIntegrationTest {
    private JdbcConnectionProvider provider;
    private JdbcTransactionManager transactions;
    private ProductRepositoryJdbc products;
    private InventoryRepositoryJdbc inventories;
    private InventoryStockAdjusterJdbc stockAdjuster;
    private StockMovementRepositoryJdbc movements;
    private CustomerRepositoryJdbc customers;
    private SellerRepositoryJdbc sellers;
    private SaleRepositoryJdbc saleRepository;
    private CustomerDebtQueryJdbc debtQuery;
    private CustomerBalanceAdjusterJdbc balanceAdjuster;
    private SalesWorkflow workflow;
    private String productId;
    private String sellerId;
    private String customerId;
    private boolean productCreated;
    private boolean sellerCreated;
    private boolean customerCreated;

    @BeforeEach
    void connectOnlyToDedicatedTestDatabase() {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");

        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        provider = new JdbcConnectionProvider(config);
        transactions = new JdbcTransactionManager(provider);
        products = new ProductRepositoryJdbc(provider, transactions);
        inventories = new InventoryRepositoryJdbc(provider, transactions);
        stockAdjuster = new InventoryStockAdjusterJdbc(provider);
        movements = new StockMovementRepositoryJdbc(provider);
        customers = new CustomerRepositoryJdbc(provider);
        sellers = new SellerRepositoryJdbc(provider);
        saleRepository = new SaleRepositoryJdbc(provider, transactions);
        debtQuery = new CustomerDebtQueryJdbc(provider);
        balanceAdjuster = new CustomerBalanceAdjusterJdbc(provider);

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        productId = "P-CODEX-" + token;
        sellerId = "S-CODEX-" + token;
        customerId = "C-CODEX-" + token;
        assertEquals(0, count("SELECT COUNT(*) FROM beer_product WHERE id = ?", productId));
        assertEquals(0, count("SELECT COUNT(*) FROM seller WHERE id = ?", sellerId));
        assertEquals(0, count("SELECT COUNT(*) FROM customer WHERE id = ?", customerId));

        BeerProduct product = fixtureProduct(productId);
        products.save(product);
        productCreated = true;
        sellers.save(new Seller(sellerId, "Vendedor ficticio Codex", "3000000101", "Dirección ficticia"));
        sellerCreated = true;
        customers.save(new Customer(customerId, "Cliente ficticio Codex", "3000000102",
                "Dirección ficticia", new BigDecimal("50.00")));
        customerCreated = true;
        transactions.inTransaction(() -> {
            stockAdjuster.increaseStock(productId, 3);
            movements.save(new StockMovement(productId, MovementType.ENTRY, 3,
                    "Fixture aislado de integración Codex " + token, java.time.LocalDateTime.now()));
        });

        workflow = workflow(saleRepository);
    }

    @AfterEach
    void removeOnlyFixturesCreatedByThisTest() {
        if (provider == null) return;
        transactions.inTransaction(() -> provider.withConnection(connection -> {
            if (sellerCreated) {
                delete(connection, """
                            DELETE FROM sale_payment WHERE sale_id IN
                                (SELECT id FROM sale WHERE seller_id = ?)
                            """, sellerId);
                delete(connection, """
                            DELETE FROM sale_line WHERE sale_id IN
                                (SELECT id FROM sale WHERE seller_id = ?)
                            """, sellerId);
                delete(connection, "DELETE FROM sale WHERE seller_id = ?", sellerId);
            }
            if (productCreated) {
                delete(connection, "DELETE FROM stock_movement WHERE product_id = ?", productId);
                delete(connection, "DELETE FROM inventory_item WHERE product_id = ?", productId);
                delete(connection, "DELETE FROM product_price WHERE product_id = ?", productId);
                delete(connection, "DELETE FROM beer_product WHERE id = ?", productId);
            }
            if (customerCreated) delete(connection, "DELETE FROM customer WHERE id = ?", customerId);
            if (sellerCreated) delete(connection, "DELETE FROM seller WHERE id = ?", sellerId);
            return null;
        }));
    }

    @Test
    void salePaymentRetryRollbackAndConcurrentStockRemainAtomic() throws Exception {
        Sale first = workflow.registerSale(
                List.of(new StockRequest(productId, SalePresentation.UNIT, 2)),
                sellerId, customerId, new BigDecimal("50.00"));

        assertEquals(new BigDecimal("200.00"), first.calculateTotal());
        assertEquals(new BigDecimal("150.00"), first.calculateBalance());
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movement WHERE product_id = ? AND movement_type = 'EXIT'",
                productId));
        assertEquals(1, inventories.findByProductId(productId).orElseThrow().totalUnits());
        assertEquals(new BigDecimal("200.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("150.00"), debtQuery.findOutstandingSalesDebt(customerId));

        Sale paid = workflow.registerPayment(first.getId(), new BigDecimal("40.00"), "workflow-retry-key");
        assertEquals(new BigDecimal("110.00"), paid.calculateBalance());
        assertEquals(new BigDecimal("160.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("50.00"),
                customers.findById(customerId).getBalance().subtract(debtQuery.findOutstandingSalesDebt(customerId)));

        Sale replay = workflow.registerPayment(first.getId(), new BigDecimal("40.00"), "workflow-retry-key");
        assertEquals(paid.getPayments().size(), replay.getPayments().size());
        assertEquals(new BigDecimal("160.00"), customers.findById(customerId).getBalance());
        assertThrows(IllegalArgumentException.class,
                () -> workflow.registerPayment(first.getId(), new BigDecimal("41.00"), "workflow-retry-key"));

        CountDownLatch startPayments = new CountDownLatch(1);
        ExecutorService paymentPool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = paymentPool.submit(() -> attemptPayment(first.getId(), "concurrent-pay-a", startPayments));
            Future<Boolean> two = paymentPool.submit(() -> attemptPayment(first.getId(), "concurrent-pay-b", startPayments));
            startPayments.countDown();
            assertEquals(1, (one.get(15, TimeUnit.SECONDS) ? 1 : 0) + (two.get(15, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            paymentPool.shutdownNow();
        }
        assertEquals(new BigDecimal("50.00"), debtQuery.findOutstandingSalesDebt(customerId));
        assertEquals(new BigDecimal("100.00"), customers.findById(customerId).getBalance());

        products.updatePrice(productId, SalePresentation.UNIT, new BigDecimal("999.00"));
        Sale historical = saleRepository.findById(first.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.00"), historical.getLines().get(0).getUnitPrice());
        assertEquals(24, historical.getLines().get(0).getUnitsPerBasketSnapshot());

        SalesWorkflow failingWorkflow = workflow(new SaveThenFailRepository(saleRepository));
        int movementsBeforeRollback = movements.findByProductId(productId).size();
        assertThrows(IllegalStateException.class, () -> failingWorkflow.registerSale(
                List.of(new StockRequest(productId, SalePresentation.UNIT, 1)),
                sellerId, customerId, BigDecimal.ZERO));
        assertEquals(1, inventories.findByProductId(productId).orElseThrow().totalUnits());
        assertEquals(movementsBeforeRollback, movements.findByProductId(productId).size());
        assertEquals(new BigDecimal("100.00"), customers.findById(customerId).getBalance());
        assertEquals(1, count("SELECT COUNT(*) FROM sale WHERE seller_id = ?", sellerId));

        CountDownLatch startSales = new CountDownLatch(1);
        ExecutorService salesPool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = salesPool.submit(() -> attemptCashSale(startSales));
            Future<Boolean> two = salesPool.submit(() -> attemptCashSale(startSales));
            startSales.countDown();
            assertEquals(1, (one.get(15, TimeUnit.SECONDS) ? 1 : 0) + (two.get(15, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            salesPool.shutdownNow();
        }
        assertEquals(0, inventories.findByProductId(productId).orElseThrow().totalUnits());
        assertEquals(2, count("SELECT COUNT(*) FROM stock_movement WHERE product_id = ? AND movement_type = 'EXIT'",
                productId));
    }

    private boolean attemptPayment(String saleId, String key, CountDownLatch start) throws Exception {
        start.await();
        try {
            workflow.registerPayment(saleId, new BigDecimal("60.00"), key);
            return true;
        } catch (IllegalArgumentException insufficientBalance) {
            return false;
        }
    }

    private boolean attemptCashSale(CountDownLatch start) throws Exception {
        start.await();
        try {
            workflow.registerSale(List.of(new StockRequest(productId, SalePresentation.UNIT, 1)),
                    sellerId, null, new BigDecimal("999.00"));
            return true;
        } catch (InsufficientStockException insufficientStock) {
            return false;
        }
    }

    private SalesWorkflow workflow(SaleRepository repository) {
        return new JdbcSalesWorkflow(products, customers, sellers, repository, saleRepository,
                debtQuery, balanceAdjuster, stockAdjuster, movements, transactions);
    }

    private static BeerProduct fixtureProduct(String id) {
        BeerProduct product = new BeerProduct(id, "Producto ficticio Codex", 330, 24,
                ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal("100.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("1100.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("2100.00"));
        return product;
    }

    private int count(String sql, Object... parameters) {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("No se obtuvo el conteo del fixture.");
                    return rows.getInt(1);
                }
            }
        });
    }

    private static void delete(Connection connection, String sql, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private static final class SaveThenFailRepository implements SaleRepository {
        private final SaleRepository delegate;

        private SaveThenFailRepository(SaleRepository delegate) { this.delegate = delegate; }

        @Override public void save(Sale sale) {
            delegate.save(sale);
            throw new IllegalStateException("Fallo simulado después de persistir la cabecera y sus líneas.");
        }
        @Override public void update(Sale sale) { delegate.update(sale); }
        @Override public void deleteById(String id) { delegate.deleteById(id); }
        @Override public Optional<Sale> findById(String id) { return delegate.findById(id); }
        @Override public List<Sale> findAll() { return delegate.findAll(); }
    }
}
