package com.laexclusiva.repository;

import com.laexclusiva.model.GoodsReceipt;
import com.laexclusiva.model.GoodsReceiptLine;
import com.laexclusiva.model.PurchaseOrderLine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Almacén de recepciones con registro atómico de claves y acumulados por instancia. */
public final class InMemoryGoodsReceiptRepository implements GoodsReceiptRepository {
    private record ReceiptKey(long orderId, String key) {}
    private final Map<ReceiptKey, GoodsReceipt> byKey = new LinkedHashMap<>();
    private final Map<Long, GoodsReceipt> receipts = new LinkedHashMap<>();
    private final Map<Long, PurchaseOrderLine> progressByLine = new LinkedHashMap<>();
    private long nextReceiptId = 1;
    private long nextLineId = 1;

    /** {@inheritDoc} */
    @Override
    public synchronized GoodsReceipt save(GoodsReceipt receipt) {
        if (receipt == null || receipt.id() != null || receipt.lines().stream().anyMatch(line -> line.id() != null)) {
            throw new IllegalArgumentException("Se requiere una recepción nueva con detalles sin ID.");
        }
        ReceiptKey key = new ReceiptKey(receipt.purchaseOrderId(), receipt.idempotencyKey());
        if (byKey.containsKey(key)) {
            throw new IllegalStateException("La clave de idempotencia ya está registrada para el pedido.");
        }

        List<GoodsReceiptLine> details = new ArrayList<>();
        for (GoodsReceiptLine line : receipt.lines()) {
            PurchaseOrderLine before = line.orderLineBeforeReceipt();
            PurchaseOrderLine recorded = progressByLine.get(before.id());
            if (recorded == null ? before.receivedEquivalentUnits() != 0 : !recorded.equals(before)) {
                throw new java.util.ConcurrentModificationException("El saldo de la línea de recepción cambió.");
            }
            details.add(new GoodsReceiptLine(nextLineId++, nextReceiptId, before, line.receivedBaskets(),
                    line.receivedLooseUnits(), line.receivedEquivalentUnits(), line.stockMovementId()));
        }
        GoodsReceipt saved = new GoodsReceipt(nextReceiptId++, receipt.purchaseOrderId(), receipt.idempotencyKey(),
                receipt.receivedByUserId(), receipt.receivedByUsername(), receipt.notes(), receipt.receivedAt(), details);
        for (GoodsReceiptLine line : details) {
            PurchaseOrderLine before = line.orderLineBeforeReceipt();
            progressByLine.put(before.id(), new PurchaseOrderLine(before.id(), before.purchaseOrderId(),
                    before.productId(), before.orderedBaskets(), before.orderedLooseUnits(),
                    before.unitsPerBasketSnapshot(), before.orderedEquivalentUnits(),
                    before.receivedEquivalentUnits() + line.receivedEquivalentUnits()));
        }
        byKey.put(key, saved);
        receipts.put(saved.id(), saved);
        return saved;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Optional<GoodsReceipt> findById(long id) {
        return Optional.ofNullable(receipts.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<GoodsReceipt> findByPurchaseOrderId(long purchaseOrderId) {
        return receipts.values().stream().filter(receipt -> receipt.purchaseOrderId() == purchaseOrderId).toList();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Optional<GoodsReceipt> findByOrderAndIdempotencyKey(long purchaseOrderId, String idempotencyKey) {
        return Optional.ofNullable(byKey.get(new ReceiptKey(purchaseOrderId, idempotencyKey)));
    }
}
