package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.PaymentMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.CashSnapshot;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.repository.CashMovementRepository;
import com.laexclusiva.repository.CashMovementRepository.SaveResult;
import com.laexclusiva.repository.CustomerBalanceAdjuster;
import com.laexclusiva.repository.CustomerDebtQuery;
import com.laexclusiva.repository.CustomerGeneralDebtMovementRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.repository.InventoryStockAdjuster;
import com.laexclusiva.repository.ProductRepository;
import com.laexclusiva.repository.SaleRepository;
import com.laexclusiva.repository.SellerRepositoryInterface;
import com.laexclusiva.repository.StockMovementRepository;
import com.laexclusiva.repository.jdbc.CashMovementRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerBalanceAdjusterJdbc;
import com.laexclusiva.repository.jdbc.CustomerDebtQueryJdbc;
import com.laexclusiva.repository.jdbc.CustomerGeneralDebtMovementRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SaleRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
import com.laexclusiva.service.CashWorkflow;
import com.laexclusiva.service.CashWorkflowJdbc;
import com.laexclusiva.service.CustomerGeneralDebtWorkflow;
import com.laexclusiva.service.CustomerGeneralDebtWorkflowJdbc;
import com.laexclusiva.service.JdbcSalesWorkflow;
import com.laexclusiva.service.SalesWorkflow;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica deuda general, caja, idempotencia y bloqueo del cliente en PostgreSQL real. */
class FinancialOperationsPostgresIntegrationTest {
    private JdbcConnectionProvider connections;
    private JdbcTransactionManager transactions;
    private CustomerRepositoryJdbc customers;
    private SellerRepositoryJdbc sellers;
    private ProductRepositoryJdbc products;
    private InventoryRepositoryJdbc inventoryRepository;
    private InventoryStockAdjusterJdbc inventory;
    private StockMovementRepositoryJdbc stockMovements;
    private SaleRepositoryJdbc salesRepository;
    private CustomerDebtQueryJdbc salesDebt;
    private CustomerBalanceAdjusterJdbc balances;
    private CustomerGeneralDebtMovementRepositoryJdbc generalLedger;
    private CashMovementRepositoryJdbc cashRepository;
    private CustomerGeneralDebtWorkflow generalDebt;
    private CashWorkflow cash;
    private SalesWorkflow sales;
    private String customerId;
    private String sellerId;
    private String productId;
    private String saleId;
    private String manualKeyPrefix;
    private boolean customerCreated;
    private boolean sellerCreated;
    private boolean productCreated;

    @BeforeEach
    void connectOnlyToTestDatabaseAndCreateOwnedFixtures() {
        String url = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        connections = new JdbcConnectionProvider(config);
        transactions = new JdbcTransactionManager(connections);
        String database = connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT current_database()")) {
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("No se obtuvo el nombre de la base conectada.");
                    return rows.getString(1);
                }
            }
        });
        Assumptions.assumeTrue("la_exclusiva_test".equals(database),
                "Omitida: la conexión no apunta exclusivamente a la base de pruebas.");
        boolean debtSchema = tableExists("customer_general_debt_movement");
        boolean cashSchema = tableExists("cash_movement");
        Assumptions.assumeTrue(debtSchema && cashSchema, "Omitida: faltan las migraciones V101/V102 en la base de pruebas.");

        customers = new CustomerRepositoryJdbc(connections);
        sellers = new SellerRepositoryJdbc(connections);
        products = new ProductRepositoryJdbc(connections, transactions);
        inventoryRepository = new InventoryRepositoryJdbc(connections, transactions);
        inventory = new InventoryStockAdjusterJdbc(connections);
        stockMovements = new StockMovementRepositoryJdbc(connections);
        salesRepository = new SaleRepositoryJdbc(connections, transactions);
        salesDebt = new CustomerDebtQueryJdbc(connections);
        balances = new CustomerBalanceAdjusterJdbc(connections);
        generalLedger = new CustomerGeneralDebtMovementRepositoryJdbc(connections, transactions);
        cashRepository = new CashMovementRepositoryJdbc(connections, transactions);
        generalDebt = new CustomerGeneralDebtWorkflowJdbc(customers, salesDebt, balances,
                generalLedger, cashRepository, transactions);
        cash = new CashWorkflowJdbc(cashRepository, transactions);

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        customerId = "C-FIN-" + token;
        sellerId = "S-FIN-" + token;
        productId = "P-FIN-" + token;
        manualKeyPrefix = "cash-manual-" + token;
        assertEquals(0, count("SELECT COUNT(*) FROM customer WHERE id = ?", customerId));
        assertEquals(0, count("SELECT COUNT(*) FROM seller WHERE id = ?", sellerId));
        assertEquals(0, count("SELECT COUNT(*) FROM beer_product WHERE id = ?", productId));

        customers.save(new Customer(customerId, "Cliente ficticio finanzas", "3000000301",
                "Dirección ficticia aislada", new BigDecimal("30.00"), new BigDecimal("10000000")));
        customerCreated = true;
        sellers.save(new Seller(sellerId, "Vendedor ficticio finanzas", "3000000302", "Dirección ficticia"));
        sellerCreated = true;
        products.save(fixtureProduct(productId));
        productCreated = true;
        transactions.inTransaction(() -> {
            inventory.increaseStock(productId, 2);
            stockMovements.save(new StockMovement(productId, MovementType.ENTRY, 2,
                    "Fixture propio de integración financiera " + token, LocalDateTime.now()));
        });

        sales = new JdbcSalesWorkflow(products, customers, sellers, salesRepository, salesRepository,
                salesDebt, balances, inventory, stockMovements, transactions, cashRepository);
        Sale sale = sales.registerSale(List.of(new StockRequest(productId, SalePresentation.UNIT, 1)),
                sellerId, customerId, new BigDecimal("20.00"), "finance-sale-" + token);
        saleId = sale.getId();
    }

    @AfterEach
    void removeOnlyFixturesCreatedByThisTest() {
        if (connections == null || customerId == null) return;
        transactions.inTransaction(() -> connections.withConnection(connection -> {
            delete(connection, """
                    DELETE FROM cash_movement WHERE source_type = 'SALE_INITIAL'
                      AND source_reference IN (SELECT id FROM sale WHERE seller_id = ?)
                    """, sellerId);
            delete(connection, """
                    DELETE FROM cash_movement WHERE source_type = 'SALE_INSTALLMENT'
                      AND split_part(source_reference, ':', 1) IN (SELECT id FROM sale WHERE seller_id = ?)
                    """, sellerId);
            delete(connection, """
                    DELETE FROM cash_movement WHERE source_type = 'GENERAL_DEBT_PAYMENT'
                      AND source_reference IN (SELECT idempotency_key FROM customer_general_debt_movement
                                               WHERE customer_id = ?)
                    """, customerId);
            delete(connection, "DELETE FROM cash_movement WHERE idempotency_key LIKE ?", manualKeyPrefix + "%");
            delete(connection, "DELETE FROM customer_general_debt_movement WHERE customer_id = ?", customerId);
            if (sellerCreated) {
                delete(connection, "DELETE FROM sale_payment WHERE sale_id IN (SELECT id FROM sale WHERE seller_id = ?)", sellerId);
                delete(connection, "DELETE FROM sale_line WHERE sale_id IN (SELECT id FROM sale WHERE seller_id = ?)", sellerId);
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
    void generalPaymentIsIdempotentAuditableAndCannotReduceSalesDebt() {
        assertEquals(new BigDecimal("110.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("80.00"), salesDebt.findOutstandingSalesDebt(customerId));
        assertEquals(new BigDecimal("30.00"), generalDebt.findBalance(customerId).generalDebt());
        String key = "general-pay-" + UUID.randomUUID();
        var first = generalDebt.registerPayment(customerId, new BigDecimal("20.00"),
                "Pago semanal", "Abono general ficticio", key, PaymentMethod.TRANSFERENCIA);
        var replay = generalDebt.registerPayment(customerId, new BigDecimal("20.00"),
                "Pago semanal", "Abono general ficticio", key, PaymentMethod.TRANSFERENCIA);
        assertThrows(IllegalArgumentException.class, () -> generalDebt.registerPayment(customerId,
                new BigDecimal("20.00"), "Pago semanal", "Abono general ficticio", key, PaymentMethod.EFECTIVO));

        assertEquals(first.id(), replay.id());
        assertEquals(GeneralDebtMovementType.PAYMENT, first.type());
        assertEquals(new BigDecimal("90.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("80.00"), salesDebt.findOutstandingSalesDebt(customerId));
        assertEquals(new BigDecimal("10.00"), generalDebt.findBalance(customerId).generalDebt());
        assertEquals(1, count("SELECT COUNT(*) FROM customer_general_debt_movement WHERE idempotency_key = ?", key));
        assertEquals(1, count("SELECT COUNT(*) FROM cash_movement WHERE source_type = 'GENERAL_DEBT_PAYMENT' "
                + "AND source_reference = ?", key));
        var receipt = cashRepository.findByIdempotencyKey("GENERAL-DEBT-PAYMENT:" + key).orElseThrow().movement();
        assertEquals(PaymentMethod.TRANSFERENCIA, receipt.paymentMethod());
        assertEquals(new BigDecimal("20.00"), receipt.amount());
        assertEquals(first.receivedByUserId(), receipt.userId());
        assertEquals(1, sales.findById(saleId).getPayments().size(),
                "El pago general no se transforma en abono de una venta.");
        assertThrows(IllegalArgumentException.class, () -> generalDebt.registerPayment(customerId,
                new BigDecimal("19.00"), "Pago semanal", "Importe distinto", key));
        assertThrows(IllegalArgumentException.class, () -> generalDebt.registerPayment(customerId,
                new BigDecimal("11.00"), "Pago semanal", "Supera deuda general", "general-overpay-" + UUID.randomUUID()));

        generalDebt.registerDebt(customerId, new BigDecimal("5.00"), "Cargo general",
                "Cargo ficticio", "general-debt-" + UUID.randomUUID());
        assertEquals(new BigDecimal("15.00"), generalDebt.findBalance(customerId).generalDebt());
        assertEquals(1, generalDebt.listMovements(customerId, LocalDate.now(), LocalDate.now(), "Pago semanal").size());
    }

    @Test
    void simultaneousGeneralPaymentsSerializeAndOnlyOneCanSpendTheAvailableDebt() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String firstKey = "general-concurrent-a-" + UUID.randomUUID();
        String secondKey = "general-concurrent-b-" + UUID.randomUUID();
        try {
            Future<Boolean> first = pool.submit(() -> attemptGeneralPayment(start, firstKey));
            Future<Boolean> second = pool.submit(() -> attemptGeneralPayment(start, secondKey));
            start.countDown();
            assertEquals(1, (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(new BigDecimal("90.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("80.00"), salesDebt.findOutstandingSalesDebt(customerId));
        assertEquals(new BigDecimal("10.00"), generalDebt.findBalance(customerId).generalDebt());
        assertEquals(1, count("SELECT COUNT(*) FROM customer_general_debt_movement "
                + "WHERE customer_id = ? AND movement_type = 'PAYMENT'", customerId));
        assertEquals(1, count("SELECT COUNT(*) FROM cash_movement WHERE source_type = 'GENERAL_DEBT_PAYMENT' "
                + "AND source_reference IN (?, ?)", firstKey, secondKey));
    }

    @Test
    void cashSummarySeparatesInvoiceFromReceiptsAndManualMovementsAreAuditable() {
        LocalDateTime occurredAt = LocalDateTime.now().minusMinutes(1);
        LocalDate today = occurredAt.toLocalDate();
        var baseline = cash.summarize(today, today);
        assertEquals(new BigDecimal("20.00"), decimal(
                "SELECT amount FROM cash_movement WHERE source_type = 'SALE_INITIAL' AND source_reference = ?", saleId));
        String incomeKey = manualKeyPrefix + "-income";
        CashMovement income = cash.registerMovement(CashDirection.INCOME, CashCategory.OTHER_INCOME,
                new BigDecimal("15.00"), occurredAt, "Ingreso ficticio", incomeKey);
        CashMovement replay = cash.registerMovement(CashDirection.INCOME, CashCategory.OTHER_INCOME,
                new BigDecimal("15.00"), occurredAt, "Ingreso ficticio", incomeKey);
        cash.registerMovement(CashDirection.EXPENSE, CashCategory.ICE, new BigDecimal("10.00"),
                occurredAt.plusSeconds(1), "Compra de hielo ficticia", manualKeyPrefix + "-expense");
        assertEquals(income.id(), replay.id());
        assertThrows(IllegalArgumentException.class, () -> cash.registerMovement(CashDirection.INCOME,
                CashCategory.OTHER_INCOME, new BigDecimal("16.00"), occurredAt,
                "Datos distintos", incomeKey));
        assertEquals(1, cash.listMovements(today, today, CashCategory.OTHER_INCOME).size());
        var summary = cash.summarize(today, today);
        assertEquals(baseline.invoicedSales(), summary.invoicedSales());
        assertEquals(baseline.saleReceipts(), summary.saleReceipts());
        assertEquals(baseline.generalDebtReceipts(), summary.generalDebtReceipts());
        assertEquals(baseline.otherIncome().add(new BigDecimal("15.00")), summary.otherIncome());
        assertEquals(baseline.expenses().add(new BigDecimal("10.00")), summary.expenses());
        assertEquals(baseline.cashIn().add(new BigDecimal("15.00")), summary.cashIn());
        assertEquals(baseline.netCash().add(new BigDecimal("5.00")), summary.netCash());
        assertEquals(summary.saleReceipts().add(summary.generalDebtReceipts()).add(summary.otherIncome()),
                summary.cashIn(), "El valor facturado no se suma al efectivo de caja.");
    }

    @Test
    void cashSnapshotUsesOneRepeatableReadWhenMovementCommitsBetweenQueries() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        CashSnapshot baseline = cash.loadSnapshot(today, today, CashCategory.OTHER_INCOME);
        String key = manualKeyPrefix + "-snapshot-concurrent";
        CountDownLatch firstReadComplete = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        AtomicBoolean pauseAfterFirstRead = new AtomicBoolean(true);
        CashMovementRepository coordinatedRepository = new CashMovementRepository() {
            @Override public java.util.Optional<RequestRecord> findByIdempotencyKey(String candidate) {
                return cashRepository.findByIdempotencyKey(candidate);
            }
            @Override public SaveResult saveOnce(CashMovement movement, String fingerprint) {
                return cashRepository.saveOnce(movement, fingerprint);
            }
            @Override public List<CashMovement> find(LocalDate from, LocalDate through, String category) {
                List<CashMovement> found = cashRepository.find(from, through, category);
                if (pauseAfterFirstRead.compareAndSet(true, false)) {
                    firstReadComplete.countDown();
                    awaitWriter(writerCommitted);
                }
                return found;
            }
            @Override public BigDecimal findInvoicedSales(LocalDate from, LocalDate through) {
                return cashRepository.findInvoicedSales(from, through);
            }
        };
        CashWorkflow consistentReads = new CashWorkflowJdbc(coordinatedRepository, transactions);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<CashSnapshot> pendingRead = pool.submit(() ->
                    consistentReads.loadSnapshot(today, today, CashCategory.OTHER_INCOME));
            assertTrue(firstReadComplete.await(15, TimeUnit.SECONDS),
                    "La primera consulta debe detenerse con la instantánea ya iniciada.");

            cash.registerMovement(CashDirection.INCOME, CashCategory.OTHER_INCOME, new BigDecimal("17.00"),
                    now, "Fixture concurrente de caja", key);
            writerCommitted.countDown();

            CashSnapshot consistent = pendingRead.get(15, TimeUnit.SECONDS);
            assertFalse(consistent.movements().stream().anyMatch(row -> row.idempotencyKey().equals(key)),
                    "La tabla no debe incorporar un movimiento que su resumen todavía no puede observar.");
            assertEquals(baseline.summary().otherIncome(), consistent.summary().otherIncome(),
                    "La consulta del resumen debe usar la misma instantánea que la tabla.");

            CashSnapshot nextRefresh = consistentReads.loadSnapshot(today, today, CashCategory.OTHER_INCOME);
            assertTrue(nextRefresh.movements().stream().anyMatch(row -> row.idempotencyKey().equals(key)));
            assertEquals(baseline.summary().otherIncome().add(new BigDecimal("17.00")),
                    nextRefresh.summary().otherIncome());
        } finally {
            writerCommitted.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void failureAfterBalanceDeltaRollsBackGeneralLedgerAndCustomerBalance() {
        CashMovementRepository failingCash = new CashMovementRepository() {
            @Override public java.util.Optional<RequestRecord> findByIdempotencyKey(String key) {
                return cashRepository.findByIdempotencyKey(key);
            }
            @Override public SaveResult saveOnce(CashMovement movement, String fingerprint) {
                throw new IllegalStateException("Fallo simulado al cerrar el ingreso de caja.");
            }
            @Override public List<CashMovement> find(LocalDate from, LocalDate through, String category) {
                return cashRepository.find(from, through, category);
            }
            @Override public BigDecimal findInvoicedSales(LocalDate from, LocalDate through) {
                return cashRepository.findInvoicedSales(from, through);
            }
        };
        CustomerGeneralDebtWorkflow failing = new CustomerGeneralDebtWorkflowJdbc(customers, salesDebt, balances,
                generalLedger, failingCash, transactions);
        String key = "general-rollback-" + UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> failing.registerPayment(customerId,
                new BigDecimal("10.00"), "Pago general", "Debe revertir", key));
        assertEquals(new BigDecimal("110.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("30.00"), generalDebt.findBalance(customerId).generalDebt());
        assertEquals(0, count("SELECT COUNT(*) FROM customer_general_debt_movement WHERE idempotency_key = ?", key));
        assertEquals(0, count("SELECT COUNT(*) FROM cash_movement WHERE source_reference = ?", key));
    }

    @Test
    void saleInstallmentAndCashReceiptCommitOrRollbackTogetherAndRetryOnce() {
        String key = "sale-payment-rollback-" + UUID.randomUUID();
        CashMovementRepository failingCash = new CashMovementRepository() {
            @Override public java.util.Optional<RequestRecord> findByIdempotencyKey(String candidate) {
                return cashRepository.findByIdempotencyKey(candidate);
            }
            @Override public SaveResult saveOnce(CashMovement movement, String fingerprint) {
                throw new IllegalStateException("Fallo simulado al guardar el recibo de caja.");
            }
            @Override public List<CashMovement> find(LocalDate from, LocalDate through, String category) {
                return cashRepository.find(from, through, category);
            }
            @Override public BigDecimal findInvoicedSales(LocalDate from, LocalDate through) {
                return cashRepository.findInvoicedSales(from, through);
            }
        };
        SalesWorkflow failing = new JdbcSalesWorkflow(products, customers, sellers, salesRepository,
                salesRepository, salesDebt, balances, inventory, stockMovements, transactions, failingCash);

        assertThrows(IllegalStateException.class, () -> failing.registerPayment(saleId,
                new BigDecimal("10.00"), key));
        assertEquals(new BigDecimal("110.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("80.00"), salesDebt.findOutstandingSalesDebt(customerId));
        assertEquals(0, count("SELECT COUNT(*) FROM sale_payment WHERE sale_id = ? AND idempotency_key = ?",
                saleId, key));
        assertEquals(0, count("SELECT COUNT(*) FROM cash_movement WHERE source_type = 'SALE_INSTALLMENT' "
                + "AND source_reference = ?", saleId + ":" + key));

        Sale paid = sales.registerPayment(saleId, new BigDecimal("10.00"), key);
        Sale replay = sales.registerPayment(saleId, new BigDecimal("10.00"), key);
        assertEquals(paid.getId(), replay.getId());
        assertEquals(1, count("SELECT COUNT(*) FROM sale_payment WHERE sale_id = ? AND idempotency_key = ?",
                saleId, key));
        assertEquals(1, count("SELECT COUNT(*) FROM cash_movement WHERE source_type = 'SALE_INSTALLMENT' "
                + "AND source_reference = ?", saleId + ":" + key));
        assertEquals(new BigDecimal("100.00"), customers.findById(customerId).getBalance());
        assertEquals(new BigDecimal("70.00"), salesDebt.findOutstandingSalesDebt(customerId));
        assertEquals(new BigDecimal("30.00"), generalDebt.findBalance(customerId).generalDebt());
    }

    @Test
    void saleCreationRollbackRemovesIdentityPaymentInventoryBalanceAndInitialCash() {
        String key = "sale-create-full-rollback-" + UUID.randomUUID();
        int stockBefore = inventoryRepository.findByProductId(productId).orElseThrow().totalUnits();
        BigDecimal balanceBefore = customers.findById(customerId).getBalance();
        int exitsBefore = count("SELECT COUNT(*) FROM stock_movement WHERE product_id = ? AND movement_type = 'EXIT'",
                productId);
        AtomicReference<String> initialCashReference = new AtomicReference<>();
        CashMovementRepository observedCash = new CashMovementRepository() {
            @Override public java.util.Optional<RequestRecord> findByIdempotencyKey(String candidate) {
                return cashRepository.findByIdempotencyKey(candidate);
            }
            @Override public SaveResult saveOnce(CashMovement movement, String fingerprint) {
                initialCashReference.set(movement.sourceReference());
                return cashRepository.saveOnce(movement, fingerprint);
            }
            @Override public List<CashMovement> find(LocalDate from, LocalDate through, String category) {
                return cashRepository.find(from, through, category);
            }
            @Override public BigDecimal findInvoicedSales(LocalDate from, LocalDate through) {
                return cashRepository.findInvoicedSales(from, through);
            }
        };
        InventoryStockAdjuster failAfterStockDelta = new InventoryStockAdjuster() {
            @Override public int increaseStock(String id, int units) { return inventory.increaseStock(id, units); }
            @Override public int decreaseStock(String id, int units) {
                int remaining = inventory.decreaseStock(id, units);
                throw new IllegalStateException("Fallo simulado tras aplicar el delta de inventario " + remaining);
            }
        };
        SalesWorkflow failing = new JdbcSalesWorkflow(products, customers, sellers, salesRepository,
                salesRepository, salesDebt, balances, failAfterStockDelta, stockMovements, transactions, observedCash);
        List<StockRequest> request = List.of(new StockRequest(productId, SalePresentation.UNIT, 1));

        assertThrows(IllegalStateException.class, () -> failing.registerSale(request, sellerId, customerId,
                new BigDecimal("25.00"), key));

        assertTrue(initialCashReference.get() != null, "La falla debe ocurrir después de intentar el recibo inicial.");
        assertEquals(0, count("SELECT COUNT(*) FROM sale WHERE idempotency_key = ?", key));
        assertEquals(0, count("SELECT COUNT(*) FROM sale_payment WHERE sale_id = ?", initialCashReference.get()));
        assertEquals(0, count("SELECT COUNT(*) FROM sale_line WHERE sale_id = ?", initialCashReference.get()));
        assertEquals(0, count("SELECT COUNT(*) FROM cash_movement WHERE source_type = 'SALE_INITIAL' "
                + "AND source_reference = ?", initialCashReference.get()));
        assertEquals(stockBefore, inventoryRepository.findByProductId(productId).orElseThrow().totalUnits());
        assertEquals(exitsBefore, count("SELECT COUNT(*) FROM stock_movement "
                + "WHERE product_id = ? AND movement_type = 'EXIT'", productId));
        assertEquals(balanceBefore, customers.findById(customerId).getBalance());

        Sale retried = sales.registerSale(request, sellerId, customerId, new BigDecimal("25.00"), key);
        assertEquals(1, count("SELECT COUNT(*) FROM sale WHERE idempotency_key = ?", key));
        assertEquals(1, count("SELECT COUNT(*) FROM sale_line WHERE sale_id = ?", retried.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM sale_payment WHERE sale_id = ?", retried.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM cash_movement WHERE source_type = 'SALE_INITIAL' "
                + "AND source_reference = ?", retried.getId()));
        assertEquals(stockBefore - 1, inventoryRepository.findByProductId(productId).orElseThrow().totalUnits());
        assertEquals(exitsBefore + 1, count("SELECT COUNT(*) FROM stock_movement "
                + "WHERE product_id = ? AND movement_type = 'EXIT'", productId));
        assertEquals(balanceBefore.add(new BigDecimal("75.00")), customers.findById(customerId).getBalance());
        Sale replay = sales.registerSale(request, sellerId, customerId, new BigDecimal("25.00"), key);
        assertEquals(retried.getId(), replay.getId());
        assertEquals(1, count("SELECT COUNT(*) FROM sale WHERE idempotency_key = ?", key));
        assertEquals(1, count("SELECT COUNT(*) FROM sale_payment WHERE sale_id = ?", retried.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM cash_movement WHERE source_type = 'SALE_INITIAL' "
                + "AND source_reference = ?", retried.getId()));
    }

    private boolean attemptGeneralPayment(CountDownLatch start, String key) throws Exception {
        start.await();
        try {
            generalDebt.registerPayment(customerId, new BigDecimal("20.00"), "Pago concurrente",
                    "Pago ficticio simultáneo", key);
            return true;
        } catch (IllegalArgumentException expectedOverpayAfterLock) {
            return false;
        }
    }

    private static void awaitWriter(CountDownLatch writerCommitted) {
        try {
            if (!writerCommitted.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La escritura concurrente no confirmó a tiempo.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la lectura concurrente de caja.", interrupted);
        }
    }

    private boolean tableExists(String table) {
        return count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                table) == 1;
    }

    private int count(String sql, Object... values) {
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("No se obtuvo el conteo del fixture.");
                    return rows.getInt(1);
                }
            }
        });
    }

    private BigDecimal decimal(String sql, String value) {
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("No se encontró el importe del fixture propio.");
                    return rows.getBigDecimal(1);
                }
            }
        });
    }

    private static void delete(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.executeUpdate();
        }
    }

    private static BeerProduct fixtureProduct(String id) {
        BeerProduct product = new BeerProduct(id, "Producto ficticio de caja", 330, 24,
                ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal("100.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("1100.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("2100.00"));
        return product;
    }
}
