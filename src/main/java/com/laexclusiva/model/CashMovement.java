package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Movimiento persistible de caja con referencia idempotente a su operación de origen. */
public record CashMovement(long id, CashDirection direction, CashCategory category, BigDecimal amount,
                           LocalDateTime occurredAt, String description, String idempotencyKey,
                           String sourceType, String sourceReference, Long userId, PaymentMethod paymentMethod) {
    public CashMovement(long id, CashDirection direction, CashCategory category, BigDecimal amount,
                        LocalDateTime occurredAt, String description, String idempotencyKey,
                        String sourceType, String sourceReference) {
        this(id, direction, category, amount, occurredAt, description, idempotencyKey, sourceType,
                sourceReference, null, null);
    }
    /** Valida importes, clasificación y vínculo de origen sin imponer una identidad generada en memoria. */
    public CashMovement {
        if (id < 0 || direction == null || category == null || category.direction() != direction
                || amount == null || amount.signum() <= 0 || occurredAt == null
                || description == null || description.isBlank() || idempotencyKey == null
                || idempotencyKey.isBlank() || sourceType == null || sourceType.isBlank()
                || sourceReference == null || sourceReference.isBlank()) {
            throw new IllegalArgumentException("El movimiento de caja requiere datos válidos y completos.");
        }
    }
}
