package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;

/** Recordatorio idempotente de una semana; no contiene el destinatario ni el contenido del mensaje. */
public record CustomerCollectionReminder(long id, String customerId, LocalDate weekStart,
                                         CollectionChannel channel, Status status,
                                         BigDecimal balanceSnapshot, LocalDateTime generatedAt,
                                         LocalDateTime lastAttemptAt, int attemptCount, ResultCode resultCode) {
    /** SENDING queda bloqueado tras un cierre abrupto para impedir un reenvío ambiguo. */
    public enum Status { PENDING_REVIEW, SENDING, SENT, FAILED }
    public enum ResultCode {
        PREPARED, SIMULATED, SENT, WINDOW_BLOCKED, CONSENT_MISSING,
        BALANCE_CHANGED, PROVIDER_FAILED, PAYMENT_INSTRUCTIONS_UNVERIFIED, MANUAL_CONTACT_ONLY
    }

    /** Valida la semana ISO y el estado del aviso para evitar duplicados o saldos vacíos. */
    public CustomerCollectionReminder {
        if (id < 0 || customerId == null || customerId.isBlank() || weekStart == null
                || weekStart.getDayOfWeek() != WeekFields.ISO.getFirstDayOfWeek()
                || (channel != CollectionChannel.EMAIL && channel != CollectionChannel.PHONE)
                || status == null || balanceSnapshot == null || balanceSnapshot.signum() <= 0
                || generatedAt == null || attemptCount < 0 || resultCode == null) {
            throw new IllegalArgumentException("El recordatorio no cumple el contrato de idempotencia semanal.");
        }
    }
}
