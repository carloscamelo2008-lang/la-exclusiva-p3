package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Entrada de historial de seguimiento atribuida a un actor administrativo validado. */
public record CollectionFollowUp(long id, String customerId, long actorUserId, EventType eventType,
                                 LocalDate commitmentDate, BigDecimal balanceSnapshot, String note,
                                 LocalDateTime occurredAt) {
    /** Tipos cerrados de eventos; el historial nunca conserva contraseñas ni cuerpos de correo. */
    public enum EventType {
        COMMITMENT_SET, CONSENT_CHANGED, BALANCE_CHANGED, NOTE_ADDED,
        REMINDER_PREPARED, REMINDER_SENT, REMINDER_FAILED, REMINDER_UNCERTAIN
    }

    /** Comprueba campos auditables sin incluir datos sensibles adicionales. */
    public CollectionFollowUp {
        if (id < 0 || customerId == null || customerId.isBlank() || actorUserId <= 0
                || eventType == null || occurredAt == null || (note != null && note.length() > 500)
                || (balanceSnapshot != null && balanceSnapshot.signum() < 0)) {
            throw new IllegalArgumentException("El seguimiento requiere una identidad y un evento válidos.");
        }
    }
}
