package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.laexclusiva.repository.CustomerRepositoryInterface;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Valida en PostgreSQL la unidad transaccional de ventas, inventario, movimientos y cartera. */
class JdbcSalesWorkflowPostgresIntegrationTest {
    private final List<String> extraCustomerIds = new ArrayList<>();
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
            for (String extraCustomerId : extraCustomerIds) {
                delete(connection, "DELETE FROM customer WHERE id = ?", extraCustomerId);
            }
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

    @Test
    void creditSaleAndPaymentSerializeOnCustomerInBothTransactionOrders() throws Exception {
        balanceAdjuster.decreaseBalance(customerId, new BigDecimal("50.00"));
        String paymentFirstCustomerId = customerId + "-PAYFIRST";
        assertEquals(0, count("SELECT COUNT(*) FROM customer WHERE id = ?", paymentFirstCustomerId));
        customers.save(new Customer(paymentFirstCustomerId, "Cliente concurrente ficticio", "3000000103",
                "Dirección ficticia", BigDecimal.ZERO));
        extraCustomerIds.add(paymentFirstCustomerId);
        transactions.inTransaction(() -> {
            stockAdjuster.increaseStock(productId, 1);
            movements.save(new StockMovement(productId, MovementType.ENTRY, 1,
                    "Fixture de concurrencia aislado", java.time.LocalDateTime.now()));
        });

        verifyCreditSaleAndPaymentOrder(customerId, true, "race-sale-first-");
        verifyCreditSaleAndPaymentOrder(paymentFirstCustomerId, false, "race-payment-first-");
    }

    private void verifyCreditSaleAndPaymentOrder(String targetCustomerId, boolean creditSaleFirst,
                                                 String idempotencyPrefix) throws Exception {
        Sale existingSale = workflow.registerSale(
                List.of(new StockRequest(productId, SalePresentation.UNIT, 1)),
                sellerId, targetCustomerId, BigDecimal.ZERO);
        assertEquals(new BigDecimal("100.00"), customers.findById(targetCustomerId).getBalance());
        assertEquals(new BigDecimal("100.00"), debtQuery.findOutstandingSalesDebt(targetCustomerId));

        CountDownLatch firstOperationStaged = new CountDownLatch(1);
        CountDownLatch waiterReachedCustomerLock = new CountDownLatch(1);
        AtomicInteger waiterBackendPid = new AtomicInteger(-1);
        CustomerRepositoryInterface observedCustomers = observingCustomerLock(
                targetCustomerId, waiterReachedCustomerLock, waiterBackendPid);
        SalesWorkflow waitingWorkflow = workflow(saleRepository, observedCustomers);
        String idempotencyKey = idempotencyPrefix + targetCustomerId;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Sale> firstOperation = pool.submit(() -> transactions.inTransaction(() -> {
                Customer locked = customers.findByIdForUpdate(targetCustomerId);
                if (locked == null) throw new IllegalStateException("No existe el cliente del fixture.");
                Sale result = creditSaleFirst
                        ? workflow.registerSale(List.of(new StockRequest(productId, SalePresentation.UNIT, 1)),
                                sellerId, targetCustomerId, new BigDecimal("80.00"))
                        : workflow.registerPayment(existingSale.getId(), new BigDecimal("25.00"), idempotencyKey);
                firstOperationStaged.countDown();
                await(waiterReachedCustomerLock, "La segunda operación no intentó bloquear el cliente.");
                int blockedPid = waiterBackendPid.get();
                if (blockedPid < 0 || !awaitPostgresBlock(blockedPid, 10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("PostgreSQL no observó la espera por el bloqueo del cliente.");
                }
                return result;
            }));

            await(firstOperationStaged, "La primera operación financiera no llegó al punto de coordinación.");
            Future<Sale> secondOperation = pool.submit(() -> creditSaleFirst
                    ? waitingWorkflow.registerPayment(existingSale.getId(), new BigDecimal("25.00"), idempotencyKey)
                    : waitingWorkflow.registerSale(List.of(new StockRequest(productId, SalePresentation.UNIT, 1)),
                            sellerId, targetCustomerId, new BigDecimal("80.00")));

            firstOperation.get(15, TimeUnit.SECONDS);
            secondOperation.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "Las transacciones concurrentes deben terminar.");
        }

        assertEquals(new BigDecimal("95.00"), customers.findById(targetCustomerId).getBalance());
        assertEquals(new BigDecimal("95.00"), debtQuery.findOutstandingSalesDebt(targetCustomerId));
        assertEquals(2, count("SELECT COUNT(*) FROM sale WHERE customer_id = ?", targetCustomerId));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM sale_payment payment JOIN sale ON sale.id = payment.sale_id
                WHERE sale.customer_id = ?
                """, targetCustomerId));
        assertEquals(new BigDecimal("105.00"), decimal("""
                SELECT COALESCE(SUM(payment.amount), 0)
                FROM sale_payment payment JOIN sale ON sale.id = payment.sale_id
                WHERE sale.customer_id = ?
                """, targetCustomerId));
        assertEquals(1, count("SELECT COUNT(*) FROM sale_payment WHERE idempotency_key = ?", idempotencyKey));

        Sale replay = workflow.registerPayment(existingSale.getId(), new BigDecimal("25.00"), idempotencyKey);
        assertEquals(1, replay.getPayments().size());
        assertEquals(new BigDecimal("95.00"), customers.findById(targetCustomerId).getBalance());
        assertEquals(new BigDecimal("95.00"), debtQuery.findOutstandingSalesDebt(targetCustomerId));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM sale_payment payment JOIN sale ON sale.id = payment.sale_id
                WHERE sale.customer_id = ?
                """, targetCustomerId));
    }

    private CustomerRepositoryInterface observingCustomerLock(String targetCustomerId,
                                                               CountDownLatch lockAttempted,
                                                               AtomicInteger backendPid) {
        return new CustomerRepositoryInterface() {
            @Override public void save(Customer customer) { customers.save(customer); }
            @Override public Customer findById(String id) { return customers.findById(id); }
            @Override public Customer findByIdForUpdate(String id) {
                if (targetCustomerId.equals(id)) {
                    backendPid.set(currentBackendPid());
                    lockAttempted.countDown();
                }
                return customers.findByIdForUpdate(id);
            }
            @Override public List<Customer> findAll() { return customers.findAll(); }
            @Override public boolean existsById(String id) { return customers.existsById(id); }
            @Override public void update(Customer customer) { customers.update(customer); }
        };
    }

    private int currentBackendPid() {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()")) {
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("No se obtuvo el PID de PostgreSQL.");
                    return result.getInt(1);
                }
            }
        });
    }

    private boolean awaitPostgresBlock(int blockedPid, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        do {
            boolean blocked = provider.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT cardinality(pg_blocking_pids(?)) > 0")) {
                    statement.setInt(1, blockedPid);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new SQLException("No se consultaron bloqueos PostgreSQL.");
                        return result.getBoolean(1);
                    }
                }
            });
            if (blocked) return true;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while (System.nanoTime() < deadline);
        return false;
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(message);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, interrupted);
        }
    }

    private BigDecimal decimal(String sql, String parameter) {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("No se obtuvo el total monetario del fixture.");
                    return result.getBigDecimal(1);
                }
            }
        });
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
        return workflow(repository, customers);
    }

    private SalesWorkflow workflow(SaleRepository repository, CustomerRepositoryInterface customerRepository) {
        return new JdbcSalesWorkflow(products, customerRepository, sellers, repository, saleRepository,
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
