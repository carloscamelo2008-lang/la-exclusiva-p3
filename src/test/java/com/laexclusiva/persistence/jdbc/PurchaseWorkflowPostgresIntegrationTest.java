package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.GoodsReceipt;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.PurchaseOrderStatus;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.PurchaseOrderRepository.OrderWithLines;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.ui.IdentityServices;
import com.laexclusiva.service.SellerService;
import com.laexclusiva.service.GoodsReceiptService.IdempotencyConflictException;
import com.laexclusiva.service.GoodsReceiptService.ReceiptLineRequest;
import com.laexclusiva.service.PurchaseOrderService.OrderLineRequest;
import com.laexclusiva.service.PurchasesModuleFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración opcional de compras sobre el esquema ya instalado en la base dedicada de pruebas. */
class PurchaseWorkflowPostgresIntegrationTest {
    private final String prefix = "PUR-TST-" + UUID.randomUUID();
    private final String testPassword = "Purchase-Fixture-" + UUID.randomUUID() + "-R8!";
    private final List<String> productIds = new ArrayList<>();
    private DatabaseConfig config;
    private JdbcConnectionProvider provider;
    private JdbcTransactionManager transactions;
    private IdentityServices identity;
    private UserSession admin;
    private Long userId;
    private PurchasesModuleFactory.Services module;
    private Long supplierId;

    @BeforeEach
    void prepareFixture() {
        String url = System.getenv(DatabaseConfig.TEST_URL_ENV);
        assumeTrue(url != null && !url.isBlank(), "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        config = DatabaseConfig.fromTestEnvironment();
        provider = new JdbcConnectionProvider(config);
        transactions = new JdbcTransactionManager(provider);
        int schemaReady = number(provider, """
                SELECT CASE WHEN current_database() = 'la_exclusiva_test'
                     AND EXISTS (SELECT FROM information_schema.columns WHERE table_schema='public'
                         AND table_name='user_account' AND column_name='security_version')
                     AND EXISTS (SELECT FROM information_schema.columns WHERE table_schema='public'
                         AND table_name='user_account' AND column_name='account_instance_id')
                     AND to_regclass('supplier') IS NOT NULL
                     AND to_regclass('purchase_order') IS NOT NULL
                     AND to_regclass('purchase_order_line') IS NOT NULL
                     AND to_regclass('goods_receipt') IS NOT NULL
                     AND to_regclass('goods_receipt_line') IS NOT NULL
                     THEN 1 ELSE 0 END
                """);
        assumeTrue(schemaReady == 1,
                "Omitida: V004, V104 y V106 deben estar instaladas en la_exclusiva_test.");
        identity = IdentityServices.postgres(provider, new SellerService(new SellerRepositoryJdbc(provider)));
        var account = new com.laexclusiva.model.UserAccount(null, prefix, PasswordHasher.hash(testPassword),
                UserRole.ADMINISTRATOR, null);
        identity.users().save(account);
        userId = account.getId();
        admin = identity.authentication().createSession(prefix, testPassword).orElseThrow();
        module = PurchasesModuleFactory.postgres(provider, identity.authorization(), identity.users());
        supplierId = module.suppliers().create(admin, prefix, null, null).id();
    }

    @AfterEach
    void deleteOnlyFixtureRows() {
        if (supplierId == null && productIds.isEmpty() && userId == null) return;
        transactions.inTransaction(() -> provider.withConnection(connection -> {
            if (supplierId != null) {
                delete(connection, """
                        DELETE FROM goods_receipt_line WHERE goods_receipt_id IN (
                            SELECT r.id FROM goods_receipt r
                            JOIN purchase_order o ON o.id = r.purchase_order_id WHERE o.supplier_id = ?)
                        """, supplierId);
                delete(connection, """
                        DELETE FROM goods_receipt WHERE purchase_order_id IN (
                            SELECT id FROM purchase_order WHERE supplier_id = ?)
                        """, supplierId);
                delete(connection, """
                        DELETE FROM purchase_order_line WHERE purchase_order_id IN (
                            SELECT id FROM purchase_order WHERE supplier_id = ?)
                        """, supplierId);
                delete(connection, "DELETE FROM purchase_order WHERE supplier_id = ?", supplierId);
                delete(connection, "DELETE FROM supplier WHERE id = ?", supplierId);
            }
            for (String id : productIds) {
                delete(connection, "DELETE FROM stock_movement WHERE product_id = ?", id);
                delete(connection, "DELETE FROM inventory_item WHERE product_id = ?", id);
                delete(connection, "DELETE FROM product_price WHERE product_id = ?", id);
                delete(connection, "DELETE FROM beer_product WHERE id = ?", id);
            }
            if (userId != null) delete(connection, "DELETE FROM user_account WHERE id = ?", userId);
            return null;
        }));
    }

    @Test
    void multireferenceOrderDoesNotTouchStockAndSurvivesRecomposition() {
        String p38 = product(38);
        String p30 = product(30);
        String p16 = product(16);
        var order = create(new OrderLineRequest(p38, 1, 2), new OrderLineRequest(p30, 1, 2),
                new OrderLineRequest(p16, 1, 2));
        assertEquals(PurchaseOrderStatus.PENDING, order.order().status());
        for (String id : productIds) assertNoStock(id);

        var restarted = PurchasesModuleFactory.postgres(new JdbcConnectionProvider(config),
                identity.authorization(), identity.users());
        assertEquals(supplierId, restarted.suppliers().findById(admin, supplierId).orElseThrow().id());
        assertEquals(order, restarted.orders().findById(admin, order.order().id()).orElseThrow());
        assertEquals(List.of(38, 30, 16), order.lines().stream().map(line -> line.unitsPerBasketSnapshot()).toList());
        assertEquals(List.of(40, 32, 18), order.lines().stream().map(line -> line.orderedEquivalentUnits()).toList());
        var requests = order.lines().stream().map(line -> new ReceiptLineRequest(line.id(), 1, 2)).toList();
        var receipt = restarted.receipts().receive(admin, order.order().id(), "capacidades", null, requests);
        var reread = PurchasesModuleFactory.postgres(new JdbcConnectionProvider(config),
                identity.authorization(), identity.users());
        assertEquals(List.of(receipt), reread.receipts().findByPurchaseOrderId(admin, order.order().id()));
        assertEquals(PurchaseOrderStatus.COMPLETED,
                reread.orders().findById(admin, order.order().id()).orElseThrow().order().status());
        assertEquals(40, stock(p38));
        assertEquals(32, stock(p30));
        assertEquals(18, stock(p16));
    }

    @Test
    void receivesTwoOfFourBasketsThenCompletesAndLinksEachMovement() {
        String id = product(38);
        var order = create(new OrderLineRequest(id, 4, 0));
        assertEquals(userId, order.order().createdByUserId());
        GoodsReceipt first = receive(order, "primera", 2);
        assertEquals(userId, first.receivedByUserId());
        assertEquals(PurchaseOrderStatus.PARTIAL, reload(order).order().status());
        assertEquals(76, reload(order).lines().get(0).receivedEquivalentUnits());
        assertEquals(76, stock(id));
        assertMovement(first, id, 76);

        GoodsReceipt second = receive(order, "segunda", 2);
        assertEquals(PurchaseOrderStatus.COMPLETED, reload(order).order().status());
        assertEquals(152, reload(order).lines().get(0).receivedEquivalentUnits());
        assertEquals(152, stock(id));
        assertMovement(second, id, 76);
        assertNotEquals(first.lines().get(0).stockMovementId(), second.lines().get(0).stockMovementId());
        assertEquals(2, movementCount(id));
        assertEquals(List.of(first, second), module.receipts().findByPurchaseOrderId(admin, order.order().id()));
    }

    @Test
    void rejectsOverReceiptAndNewReceiptAfterCancellation() {
        String id = product(38);
        var order = create(new OrderLineRequest(id, 4, 0));
        GoodsReceipt first = receive(order, "parcial", 2);
        assertThrows(IllegalArgumentException.class, () -> receive(order, "exceso", 3));
        assertEquals(PurchaseOrderStatus.PARTIAL, reload(order).order().status());

        module.orders().cancel(admin, order.order().id());
        assertThrows(IllegalArgumentException.class, () -> receive(order, "cancelado", 1));
        assertEquals(PurchaseOrderStatus.CANCELLED, reload(order).order().status());
        assertEquals(76, reload(order).lines().get(0).receivedEquivalentUnits());
        assertEquals(76, stock(id));
        assertEquals(1, movementCount(id));
        assertEquals(List.of(first), module.receipts().findByPurchaseOrderId(admin, order.order().id()));
    }

    @Test
    void outerFailureRollsBackBothProductsMovementsReceiptAndOrderProgress() {
        String existing = product(38);
        String fresh = product(30);
        new InventoryStockAdjusterJdbc(provider).increaseStock(existing, 7);
        var order = create(new OrderLineRequest(existing, 1, 0), new OrderLineRequest(fresh, 1, 0));
        var lines = order.lines().stream().map(line -> new ReceiptLineRequest(line.id(), 1, 0)).toList();
        var abort = new IllegalStateException("Fallo simulado antes de confirmar la transacción exterior.");
        assertSame(abort, assertThrows(IllegalStateException.class, () -> transactions.inTransaction(() -> {
            GoodsReceipt staged = module.receipts().receive(admin, order.order().id(), "rollback", null, lines);
            assertEquals(45, stock(existing));
            assertEquals(30, stock(fresh));
            assertEquals(1, movementCount(existing));
            assertEquals(1, movementCount(fresh));
            assertEquals(PurchaseOrderStatus.COMPLETED, reload(order).order().status());
            assertEquals(List.of(staged), module.receipts().findByPurchaseOrderId(admin, order.order().id()));
            throw abort;
        })));

        assertEquals(7, stock(existing));
        assertEquals(0, movementCount(existing));
        assertNoStock(fresh);
        assertEquals(order, reload(order));
        assertTrue(module.receipts().findByPurchaseOrderId(admin, order.order().id()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentReceiptsWaitForOrderLockAndRecheckAfterCommit(boolean sameKey) throws Exception {
        String id = product(38);
        String extra = product(16);
        // La segunda referencia deja el pedido parcial para comprobar el rechazo por saldo de la primera.
        var order = create(new OrderLineRequest(id, 4, 0), new OrderLineRequest(extra, 1, 0));
        receive(order, "parcial", 2);
        var secondProvider = new JdbcConnectionProvider(config);
        var secondTransactions = new JdbcTransactionManager(secondProvider);
        var secondModule = PurchasesModuleFactory.postgres(secondProvider,
                identity.authorization(), identity.users());
        CountDownLatch staged = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var firstPid = new CompletableFuture<Integer>();
        var secondPid = new CompletableFuture<Integer>();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> transactions.inTransaction(() -> {
                firstPid.complete(backendWithTimeout(provider));
                GoodsReceipt receipt = receive(order, "restante", 2);
                staged.countDown();
                await(release);
                return receipt;
            }));
            var second = pool.submit(() -> {
                await(staged);
                return secondTransactions.inTransaction(() -> {
                    secondPid.complete(backendWithTimeout(secondProvider));
                    return secondModule.receipts().receive(admin, order.order().id(),
                            sameKey ? "restante" : "otra-clave", null, List.of(request(order, 2)));
                });
            });
            awaitBlockedBy(secondPid.get(12, TimeUnit.SECONDS), firstPid.get(2, TimeUnit.SECONDS));
            assertFalse(second.isDone());
            release.countDown();
            GoodsReceipt committed = first.get(10, TimeUnit.SECONDS);

            if (sameKey) {
                assertEquals(committed, second.get(10, TimeUnit.SECONDS));
                assertEquals(committed, receive(order, "restante", 2));
                assertThrows(IdempotencyConflictException.class, () -> receive(order, "restante", 1));
            } else {
                ExecutionException rejected = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
                assertInstanceOf(IllegalArgumentException.class, rejected.getCause());
                assertTrue(rejected.getCause().getMessage().contains("saldo pendiente"));
            }
            assertEquals(152, stock(id));
            assertEquals(2, movementCount(id));
            assertNoStock(extra);
            assertEquals(2, module.receipts().findByPurchaseOrderId(admin, order.order().id()).size());
            assertEquals(152, reload(order).lines().get(0).receivedEquivalentUnits());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(25, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "Los hilos JDBC deben finalizar antes de limpiar.");
            }
        }
    }

    private String product(int capacity) {
        String id = prefix + "-" + capacity;
        BeerProduct product = new BeerProduct(id, "Producto ficticio", 330, capacity,
                ContainerType.NON_RETURNABLE_CAN, null);
        for (SalePresentation presentation : List.of(
                SalePresentation.UNIT, SalePresentation.HALF_BASKET, SalePresentation.FULL_BASKET)) {
            product.definePrice(presentation, BigDecimal.ONE);
        }
        new ProductRepositoryJdbc(provider, transactions).save(product);
        productIds.add(id);
        return id;
    }

    private OrderWithLines create(OrderLineRequest... lines) {
        return module.orders().create(admin, supplierId, null, List.of(lines));
    }

    private OrderWithLines reload(OrderWithLines order) {
        return module.orders().findById(admin, order.order().id()).orElseThrow();
    }

    private GoodsReceipt receive(OrderWithLines order, String key, int baskets) {
        return module.receipts().receive(admin, order.order().id(), key, null, List.of(request(order, baskets)));
    }

    private static ReceiptLineRequest request(OrderWithLines order, int baskets) {
        return new ReceiptLineRequest(order.lines().get(0).id(), baskets, 0);
    }

    private int stock(String productId) {
        return number(provider, "SELECT total_units FROM inventory_item WHERE product_id = ?", productId);
    }

    private int movementCount(String productId) {
        return number(provider, "SELECT count(*) FROM stock_movement WHERE product_id = ?", productId);
    }

    private void assertNoStock(String productId) {
        assertEquals(0, number(provider, "SELECT count(*) FROM inventory_item WHERE product_id = ?", productId));
        assertEquals(0, movementCount(productId));
    }

    private void assertMovement(GoodsReceipt receipt, String productId, int units) {
        Long movementId = receipt.lines().get(0).stockMovementId();
        assertNotNull(movementId);
        assertEquals(1, number(provider, """
                SELECT count(*) FROM goods_receipt_line d JOIN stock_movement m ON m.id = d.stock_movement_id
                WHERE d.id = ? AND d.goods_receipt_id = ? AND m.id = ?
                  AND m.product_id = ? AND m.units = ? AND m.movement_type = ?
                """, receipt.lines().get(0).id(), receipt.id(), movementId, productId, units, MovementType.ENTRY.name()));
    }

    private static int backendWithTimeout(JdbcConnectionProvider provider) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SET LOCAL statement_timeout = '15s'")) {
                statement.execute();
                return null;
            }
        });
        return number(provider, "SELECT pg_backend_pid()");
    }

    private void awaitBlockedBy(int waitingPid, int blockingPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (number(provider, "SELECT CASE WHEN ? = ANY(pg_blocking_pids(?)) THEN 1 ELSE 0 END",
                    blockingPid, waitingPid) == 1) return;
            TimeUnit.MILLISECONDS.sleep(25);
        }
        fail("PostgreSQL no observó a la segunda recepción esperando el bloqueo del pedido.");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(15, TimeUnit.SECONDS), "Se agotó la espera de coordinación JDBC.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Se interrumpió la coordinación JDBC.", interrupted);
        }
    }

    private static int number(JdbcConnectionProvider provider, String sql, Object... params) {
        return provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "La consulta de prueba debe devolver un valor.");
                    return rows.getInt(1);
                }
            }
        });
    }

    private static void delete(Connection connection, String sql, Object id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }
}
