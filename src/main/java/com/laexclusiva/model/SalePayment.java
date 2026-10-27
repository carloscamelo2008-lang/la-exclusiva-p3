package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pago o abono inmutable asociado a una venta. */
public final class SalePayment {

    private final BigDecimal amount;
    private final LocalDateTime timestamp;

    public SalePayment(BigDecimal amount, LocalDateTime timestamp) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El importe del pago debe ser mayor que cero.");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("La fecha y hora del pago son obligatorias.");
        }
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
