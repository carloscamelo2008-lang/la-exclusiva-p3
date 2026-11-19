package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Registro histórico auditable de una creación o pago de deuda general. */
public record GeneralDebtMovement(long id, String customerId, GeneralDebtMovementType type,
                                  BigDecimal amount, LocalDateTime occurredAt, String category,
                                  String description, String idempotencyKey) {
    /** Valida los campos de dominio conservando la identidad generada por PostgreSQL. */
    public GeneralDebtMovement {
        if (id < 0 || customerId == null || customerId.isBlank() || type == null
                || amount == null || amount.signum() <= 0 || occurredAt == null
                || category == null || category.isBlank() || description == null || description.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("El movimiento de deuda general requiere todos sus datos.");
        }
    }
}
