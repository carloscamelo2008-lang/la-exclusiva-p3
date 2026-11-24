package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pago o abono inmutable asociado a una venta. */
public final class SalePayment {

    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final Long receivedByUserId;
    private final PaymentMethod paymentMethod;

    public Long getReceivedByUserId() { return receivedByUserId; }

    public SalePayment(BigDecimal amount, LocalDateTime timestamp) {
        this(amount, timestamp, null);
    }

    public SalePayment(BigDecimal amount, LocalDateTime timestamp, Long receivedByUserId) {
        this(amount, timestamp, receivedByUserId, null);
    }

    public SalePayment(BigDecimal amount, LocalDateTime timestamp, Long receivedByUserId, PaymentMethod paymentMethod) {
        this.receivedByUserId = receivedByUserId;
        this.paymentMethod = paymentMethod;
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

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }
}
