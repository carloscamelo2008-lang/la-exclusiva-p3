package com.laexclusiva.model;

/** Estados persistidos del ciclo de vida de un pedido de compra. */
public enum PurchaseOrderStatus {
    /** Pedido pendiente de su primera recepción. */
    PENDING,
    /** Pedido con mercancía recibida y unidades pendientes. */
    PARTIAL,
    /** Pedido con todas las unidades solicitadas recibidas. */
    COMPLETED,
    /** Pedido cancelado y cerrado a nuevas recepciones. */
    CANCELLED
}
