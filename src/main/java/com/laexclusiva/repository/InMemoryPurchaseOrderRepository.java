package com.laexclusiva.repository;

import com.laexclusiva.model.PurchaseOrder;
import com.laexclusiva.model.PurchaseOrderLine;
import com.laexclusiva.model.PurchaseOrderStatus;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Almacén de pedidos con actualizaciones atómicas e instantáneas inmutables por instancia. */
public final class InMemoryPurchaseOrderRepository implements PurchaseOrderRepository {
    private final Map<Long, OrderWithLines> orders = new LinkedHashMap<>();
    private long nextOrderId = 1;
    private long nextLineId = 1;

    /** {@inheritDoc} */
    @Override
    public synchronized OrderWithLines save(PurchaseOrder order, List<NewLine> lines) {
        if (order == null || order.id() != null || order.status() != PurchaseOrderStatus.PENDING
                || lines == null || lines.isEmpty() || lines.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Se requiere un pedido pendiente nuevo con al menos una línea válida.");
        }
        PurchaseOrder saved = new PurchaseOrder(nextOrderId++, order.supplierId(), order.status(), order.notes(),
                order.createdByUserId(), order.createdByUsername(), order.createdAt());
        List<PurchaseOrderLine> savedLines = new ArrayList<>();
        for (NewLine line : lines) {
            savedLines.add(new PurchaseOrderLine(nextLineId++, saved.id(), line.productId(), line.orderedBaskets(),
                    line.orderedLooseUnits(), line.unitsPerBasketSnapshot(), line.orderedEquivalentUnits(), 0));
        }
        OrderWithLines result = new OrderWithLines(saved, savedLines);
        orders.put(saved.id(), result);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Optional<OrderWithLines> findById(long id) {
        return Optional.ofNullable(orders.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OrderWithLines> findByIdForUpdate(long id) {
        return findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<PurchaseOrder> findAll() {
        return orders.values().stream().map(OrderWithLines::order).toList();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<PurchaseOrder> findByStatus(PurchaseOrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("El estado del pedido es obligatorio.");
        }
        return findAll().stream().filter(order -> order.status() == status).toList();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized OrderWithLines updateStatusAndReceivedUnits(OrderWithLines expected,
            PurchaseOrderStatus status, Map<Long, Integer> receivedDeltas) {
        if (expected == null || expected.order().id() == null) {
            throw new IllegalArgumentException("Se requiere una instantánea de un pedido persistido.");
        }
        OrderWithLines current = orders.get(expected.order().id());
        if (!expected.equals(current)) {
            throw new ConcurrentModificationException("El pedido cambió; vuelva a consultarlo.");
        }
        OrderWithLines result = current.applyReceipt(status, receivedDeltas);
        orders.put(result.order().id(), result);
        return result;
    }
}
