package com.laexclusiva.model;

/**
 * Detalle de mercancía recibida contra una línea del pedido.
 *
 * @param id identificador positivo, o nulo antes de persistir
 * @param goodsReceiptId identificador positivo de la recepción, o nulo antes de persistir
 * @param orderLineBeforeReceipt estado de la línea del pedido inmediatamente anterior a esta recepción
 * @param receivedBaskets canastas completas recibidas en esta operación
 * @param receivedLooseUnits unidades sueltas recibidas en esta operación
 * @param receivedEquivalentUnits unidades equivalentes recibidas en esta operación
 * @param stockMovementId identificador positivo del movimiento de inventario, si está disponible
 */
public record GoodsReceiptLine(Long id, Long goodsReceiptId, PurchaseOrderLine orderLineBeforeReceipt,
                               int receivedBaskets, int receivedLooseUnits, int receivedEquivalentUnits,
                               Long stockMovementId) {
    /**
     * Construye un detalle utilizando la capacidad y el saldo previo de la línea del pedido.
     *
     * @throws IllegalArgumentException si los identificadores o las cantidades son inválidos,
     *         el total no coincide con las cantidades recibidas o supera el saldo pendiente
     */
    public GoodsReceiptLine {
        if ((id != null && id <= 0) || (goodsReceiptId != null && goodsReceiptId <= 0)
                || (stockMovementId != null && stockMovementId <= 0)) {
            throw new IllegalArgumentException("Los identificadores del detalle de recepción deben ser positivos.");
        }
        if (orderLineBeforeReceipt == null || orderLineBeforeReceipt.id() == null) {
            throw new IllegalArgumentException("El detalle requiere una línea de pedido persistida.");
        }
        int calculatedUnits = PurchaseOrderLine.equivalentUnits(
                receivedBaskets, receivedLooseUnits, orderLineBeforeReceipt.unitsPerBasketSnapshot());
        if (receivedEquivalentUnits != calculatedUnits) {
            throw new IllegalArgumentException("El total recibido no coincide con las cantidades del detalle.");
        }
        if (receivedEquivalentUnits > orderLineBeforeReceipt.pendingEquivalentUnits()) {
            throw new IllegalArgumentException("La cantidad recibida supera el saldo pendiente.");
        }
    }

    /** @return identificador de la línea de pedido asociada al detalle */
    public long purchaseOrderLineId() {
        return orderLineBeforeReceipt.id();
    }

    /** @return identificador del producto recibido */
    public String productId() {
        return orderLineBeforeReceipt.productId();
    }
}
