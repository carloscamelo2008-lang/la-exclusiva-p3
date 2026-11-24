package com.laexclusiva.model;

import java.time.LocalDateTime;

/** Evento de auditoría sin credenciales, hashes, tokens, SQL ni contenido del mensaje. */
public record AdministrativeAuditEvent(long id, long actorUserId, Action action, TargetType targetType,
                                       String targetReference, LocalDateTime occurredAt, Result result) {
    public enum Action {
        USER_CREATED, ROLE_CHANGED, ACCOUNT_ACTIVATED, ACCOUNT_DEACTIVATED,
        PASSWORD_RESET, SELLER_ASSIGNMENT_CHANGED, STATEMENT_SEND_ATTEMPT
    }
    public enum TargetType { USER, CUSTOMER }
    public enum Result { SUCCESS, FAILED, UNCERTAIN }

    /** Rechaza referencias vacías o campos de auditoría incompletos. */
    public AdministrativeAuditEvent {
        if (id < 0 || actorUserId <= 0 || action == null || targetType == null
                || targetReference == null || targetReference.isBlank() || targetReference.length() > 128
                || occurredAt == null || result == null) {
            throw new IllegalArgumentException("El evento de auditoría requiere actor y objetivo válidos.");
        }
    }
}
