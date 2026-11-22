package com.laexclusiva.repository;

import com.laexclusiva.model.GoodsReceipt;
import com.laexclusiva.model.GoodsReceiptLine;
import com.laexclusiva.model.PurchaseOrder;
import com.laexclusiva.model.PurchaseOrderLine;
import com.laexclusiva.model.PurchaseOrderStatus;
import com.laexclusiva.model.Supplier;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static com.laexclusiva.model.PurchaseOrderStatus.CANCELLED;
import static com.laexclusiva.model.PurchaseOrderStatus.COMPLETED;
import static com.laexclusiva.model.PurchaseOrderStatus.PARTIAL;
import static com.laexclusiva.model.PurchaseOrderStatus.PENDING;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryPurchaseRepositoriesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 12, 0);

    @Test
    void filtersAndUpdatesSuppliersWithoutSharingInstances() {
        var suppliers = new InMemorySupplierRepository();
        var saved = suppliers.save(new Supplier(null, "Proveedor", null, null, true, NOW));
        assertEquals(List.of(saved), suppliers.findActive());
        var inactive = suppliers.update(new Supplier(saved.id(), saved.name(), null, null, false, NOW.plusDays(1)));
        assertEquals(NOW, inactive.createdAt());
        assertTrue(suppliers.findActive().isEmpty());
        assertEquals(List.of(inactive), suppliers.findAll());
        assertTrue(new InMemorySupplierRepository().findById(saved.id()).isEmpty());
    }

    @Test
    void updatesPendingPartialCompletedAndCancelledOrders() {
        var orders = new InMemoryPurchaseOrderRepository();
        var pending = newOrder(orders);
        long lineId = pending.lines().get(0).id();
        assertEquals(PENDING, pending.order().status());
        assertEquals(List.of(pending.order()), orders.findByStatus(PENDING));
        assertTrue(new InMemoryPurchaseOrderRepository().findById(pending.order().id()).isEmpty());

        var partial = orders.updateStatusAndReceivedUnits(pending, PARTIAL, Map.of(lineId, 20));
        assertEquals(PARTIAL, partial.order().status());
        assertThrows(ConcurrentModificationException.class,
                () -> orders.updateStatusAndReceivedUnits(pending, PARTIAL, Map.of(lineId, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> orders.updateStatusAndReceivedUnits(partial, COMPLETED, Map.of(lineId, 19)));
        assertEquals(partial, orders.findByIdForUpdate(pending.order().id()).orElseThrow());
        var completed = orders.updateStatusAndReceivedUnits(partial, COMPLETED, Map.of(lineId, 18));
        assertEquals(COMPLETED, completed.order().status());
        assertEquals(38, completed.lines().get(0).receivedEquivalentUnits());

        var cancelled = orders.updateStatusAndReceivedUnits(newOrder(orders), CANCELLED, Map.of());
        assertEquals(CANCELLED, cancelled.order().status());
        assertThrows(IllegalArgumentException.class, () -> orders.updateStatusAndReceivedUnits(
                cancelled, PARTIAL, Map.of(cancelled.lines().get(0).id(), 1)));
        assertEquals(List.of(cancelled.order()), orders.findByStatus(CANCELLED));
    }

    @Test
    void scopesIdempotencyToTheOrderAndRejectsStaleReceiptBalances() {
        var orders = new InMemoryPurchaseOrderRepository();
        var receipts = new InMemoryGoodsReceiptRepository();
        var first = newOrder(orders).lines().get(0);
        var second = newOrder(orders).lines().get(0);
        var requested = receipt(first, "retry", 20);
        var saved = receipts.save(requested);
        assertThrows(IllegalStateException.class, () -> receipts.save(requested));
        assertThrows(ConcurrentModificationException.class, () -> receipts.save(receipt(first, "different", 20)));
        assertEquals(saved, receipts.findByOrderAndIdempotencyKey(first.purchaseOrderId(), "retry").orElseThrow());
        assertEquals(saved, receipts.findById(saved.id()).orElseThrow());
        assertEquals(List.of(saved), receipts.findByPurchaseOrderId(first.purchaseOrderId()));
        assertEquals(second.purchaseOrderId(), receipts.save(receipt(second, "retry", 20)).purchaseOrderId());
        assertTrue(new InMemoryGoodsReceiptRepository().findById(saved.id()).isEmpty());
    }

    @Test
    void concurrentInsertsWithTheSameKeyStoreOneReceipt() throws Exception {
        var receipts = new InMemoryGoodsReceiptRepository();
        var request = receipt(newOrder(new InMemoryPurchaseOrderRepository()).lines().get(0), "retry", 20);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> insert = () -> {
            start.await();
            try {
                receipts.save(request);
                return true;
            } catch (IllegalStateException duplicate) {
                return false;
            }
        };
        try {
            var first = pool.submit(insert);
            var second = pool.submit(insert);
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, receipts.findByPurchaseOrderId(request.purchaseOrderId()).size());
        } finally {
            pool.shutdownNow();
        }
    }

    private static PurchaseOrderRepository.OrderWithLines newOrder(PurchaseOrderRepository orders) {
        return orders.save(new PurchaseOrder(null, 1, PurchaseOrderStatus.PENDING, null, null, "jesus", NOW),
                List.of(new PurchaseOrderRepository.NewLine("P", 1, 0, 38)));
    }

    private static GoodsReceipt receipt(PurchaseOrderLine line, String key, int units) {
        var detail = new GoodsReceiptLine(null, null, line, 0, units, units, null);
        return new GoodsReceipt(null, line.purchaseOrderId(), key, null, "jesus", null, NOW, List.of(detail));
    }
}
