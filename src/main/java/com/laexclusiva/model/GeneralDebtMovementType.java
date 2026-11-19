package com.laexclusiva.model;

/** Tipos de movimiento que cambian la deuda general no asignada a ventas. */
public enum GeneralDebtMovementType {
    /** Cargo que aumenta el saldo agregado del cliente sin asociarse a una venta. */
    DEBT_INCREASE,
    /** Pago aplicado únicamente a la diferencia de deuda general disponible. */
    PAYMENT
}
