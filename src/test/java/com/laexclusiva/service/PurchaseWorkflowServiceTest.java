package com.laexclusiva.service;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.PurchaseOrderStatus;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.PurchaseOrderRepository.OrderWithLines;
import com.laexclusiva.service.GoodsReceiptService.IdempotencyConflictException;
import com.laexclusiva.service.GoodsReceiptService.ReceiptLineRequest;
import com.laexclusiva.service.PurchaseOrderService.OrderLineRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PurchaseWorkflowServiceTest {
    private final InMemoryProductRepository catalog = new InMemoryProductRepository();
    private final InMemoryInventoryRepository inventory = new InMemoryInventoryRepository();
    private final InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
    private BeerProduct replacement;
    private final ProductService products = new ProductService(catalog) {
        @Override
        public BeerProduct findById(String id) {
            return replacement != null && replacement.getId().equals(id)
                    ? replacement : super.findById(id);
        }
    };
    private final PurchasesModuleFactory.Services module =
            PurchasesModuleFactory.inMemory(products, inventory, movements);
    private final UserSession admin = session(UserRole.ADMINISTRATOR);
    private final UserSession worker = session(UserRole.WORKER);
    private long supplierId;

    @BeforeEach
    void setUp() {
        for (int capacity : new int[]{38, 30, 16}) {
            catalog.save(product(capacity, capacity));
        }
        supplierId = module.suppliers().create(admin, "Proveedor", null, null).id();
    }

    @Test
    void createsMultireferenceOrderWithoutChangingStockAndReceivesEachCapacity() {
        var order = module.orders().create(admin, supplierId, null, List.of(
                new OrderLineRequest("P38", 1, 2), new OrderLineRequest("P30", 1, 2),
                new OrderLineRequest("P16", 1, 2)));
        assertEquals(List.of(38, 30, 16), order.lines().stream().map(line -> line.unitsPerBasketSnapshot()).toList());
        assertEquals(List.of(40, 32, 18), order.lines().stream().map(line -> line.orderedEquivalentUnits()).toList());
        assertTrue(inventory.findAll().isEmpty());
        assertTrue(movements.findAll().isEmpty());
        var forward = order.lines().stream().map(line -> new ReceiptLineRequest(line.id(), 1, 2)).toList();
        var saved = module.receipts().receive(admin, order.order().id(), "multilinea", null, forward);
        var reversed = List.of(forward.get(2), forward.get(1), forward.get(0));
        assertEquals(saved, module.receipts().receive(admin, order.order().id(), "multilinea", "otra nota", reversed));
        assertEquals(40, stock("P38"));
        assertEquals(32, stock("P30"));
        assertEquals(18, stock("P16"));
        assertEquals(3, movements.findAll().size());
    }

    @Test
    void receivesTwoOfFourBasketsAndThenCompletes() {
        var order = newOrder();
        var first = receive(order, "primera", 2);
        assertEquals(PurchaseOrderStatus.PARTIAL, reload(order).order().status());
        assertEquals(76, reload(order).lines().get(0).receivedEquivalentUnits());
        assertEquals(76, stock("P38"));
        assertEquals(MovementType.ENTRY, movements.findAll().get(0).type());
        assertNull(first.lines().get(0).stockMovementId());

        var second = receive(order, "segunda", 2);
        assertNotEquals(first.id(), second.id());
        assertEquals(PurchaseOrderStatus.COMPLETED, reload(order).order().status());
        assertEquals(152, stock("P38"));
        assertEquals(first, receive(order, "primera", 2));
        assertEquals(2, movements.findAll().size());
        assertThrows(IllegalArgumentException.class, () -> module.orders().cancel(admin, order.order().id()));
    }

    @Test
    void rejectsOverReceiptAndCancellationClosesOnlyThePendingBalance() {
        var order = newOrder();
        receive(order, "primera", 2);
        assertThrows(IllegalArgumentException.class, () -> receive(order, "exceso", 3));
        assertEquals(PurchaseOrderStatus.PARTIAL, reload(order).order().status());
        var cancelled = module.orders().cancel(admin, order.order().id());
        assertEquals(PurchaseOrderStatus.CANCELLED, cancelled.order().status());
        assertEquals(76, cancelled.lines().get(0).receivedEquivalentUnits());
        assertThrows(IllegalArgumentException.class, () -> receive(order, "nueva", 1));
        assertEquals(76, stock("P38"));
        assertEquals(1, movements.findAll().size());
    }

    @Test
    void concurrentDoubleClickReturnsSameReceiptAndRejectsChangedPayload() throws Exception {
        var order = newOrder();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> action = () -> {
                start.await();
                return receive(order, "doble", 2).id();
            };
            var first = pool.submit(action);
            var second = pool.submit(action);
            start.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThrows(IdempotencyConflictException.class, () -> receive(order, "doble", 1));
        assertEquals(76, stock("P38"));
        assertEquals(1, movements.findAll().size());
    }

    @Test
    void workerCanReadButOnlyAnActiveAdministratorCanWrite() {
        var order = newOrder();
        assertEquals(List.of(order.order()), module.orders().findAll(worker));
        assertEquals(1, module.suppliers().findActive(worker).size());
        assertThrows(SecurityException.class, () -> module.suppliers().create(worker, "Otro", null, null));
        assertThrows(SecurityException.class, () -> module.suppliers().setActive(worker, supplierId, false));
        assertThrows(SecurityException.class, () -> module.orders().create(worker, supplierId, null,
                List.of(new OrderLineRequest("P38", 1, 0))));
        assertThrows(SecurityException.class, () -> module.orders().cancel(worker, order.order().id()));
        assertThrows(SecurityException.class, () -> module.receipts().receive(worker, order.order().id(), "intento",
                null, List.of(new ReceiptLineRequest(order.lines().get(0).id(), 1, 0))));
        module.suppliers().update(admin, supplierId, "Actualizado", null, null);
        module.suppliers().setActive(admin, supplierId, false);
        assertTrue(module.suppliers().findActive(worker).isEmpty());
        assertThrows(IllegalArgumentException.class, this::newOrder);
        admin.invalidate();
        assertThrows(SecurityException.class, () -> module.suppliers().findAll(admin));
    }

    @Test
    void usesOrderCapacityWhenTheCatalogChanges() {
        var order = newOrder();
        replacement = product(38, 30);
        receive(order, "historia", 1);
        assertEquals(38, stock("P38"));
        assertEquals(38, reload(order).lines().get(0).receivedEquivalentUnits());
    }

    private OrderWithLines newOrder() {
        return module.orders().create(admin, supplierId, null, List.of(new OrderLineRequest("P38", 4, 0)));
    }

    private OrderWithLines reload(OrderWithLines order) {
        return module.orders().findById(admin, order.order().id()).orElseThrow();
    }

    private com.laexclusiva.model.GoodsReceipt receive(OrderWithLines order, String key, int baskets) {
        return module.receipts().receive(admin, order.order().id(), key, null,
                List.of(new ReceiptLineRequest(order.lines().get(0).id(), baskets, 0)));
    }

    private int stock(String productId) {
        return inventory.findByProductId(productId).orElseThrow().totalUnits();
    }

    private static BeerProduct product(int reference, int capacity) {
        var product = new BeerProduct("P" + reference, "Producto", 330, capacity, ContainerType.NON_RETURNABLE_CAN, null);
        for (SalePresentation presentation : List.of(
                SalePresentation.UNIT, SalePresentation.HALF_BASKET, SalePresentation.FULL_BASKET)) {
            product.definePrice(presentation, BigDecimal.ONE);
        }
        return product;
    }

    private static UserSession session(UserRole role) {
        return new UserSession(new UserAccount(1L, role.name(), "fixture", role, null));
    }
}
