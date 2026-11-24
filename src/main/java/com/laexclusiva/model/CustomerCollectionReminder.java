package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.UUID;

/** Recordatorio idempotente de una semana; no contiene el destinatario ni el contenido del mensaje. */
public record CustomerCollectionReminder(long id, String customerId, LocalDate weekStart,
                                         CollectionChannel channel, Status status,
                                         BigDecimal balanceSnapshot, LocalDateTime generatedAt,
                                         LocalDateTime lastAttemptAt, int attemptCount, ResultCode resultCode,
                                         UUID attemptId, UUID attemptOwnerId, ProviderEvidence providerEvidence) {
    /**
     * SENDING es una reserva de un intento único; al cerrar abruptamente otra instancia la cambia
     * a RECONCILIATION_REQUIRED, que nunca vuelve a entrar automáticamente al flujo de envío.
     */
    public enum Status { PENDING_REVIEW, SENDING, SENT, FAILED, RECONCILIATION_REQUIRED }
    public enum ResultCode {
        PREPARED, SIMULATED, SENT, WINDOW_BLOCKED, CONSENT_MISSING,
        BALANCE_CHANGED, PROVIDER_FAILED, PROVIDER_REJECTED, PROVIDER_OUTCOME_UNCERTAIN,
        PAYMENT_INSTRUCTIONS_UNVERIFIED, MANUAL_CONTACT_ONLY
    }
    /** Evidencia cerrada y no sensible del transporte; nunca contiene mensaje ni dirección. */
    public enum ProviderEvidence { SMTP_ACCEPTED, SMTP_REJECTED, SMTP_OUTCOME_UNCERTAIN, SIMULATED }

    /** Constructor compatible con registros previos a V109 en estados no ambiguos. */
    public CustomerCollectionReminder(long id, String customerId, LocalDate weekStart,
            CollectionChannel channel, Status status, BigDecimal balanceSnapshot, LocalDateTime generatedAt,
            LocalDateTime lastAttemptAt, int attemptCount, ResultCode resultCode) {
        this(id, customerId, weekStart, channel, status, balanceSnapshot, generatedAt, lastAttemptAt,
                attemptCount, resultCode, null, null, legacyEvidence(status, resultCode));
    }

    /** Constructor compatible con el contrato anterior a la persistencia de evidencia del proveedor. */
    public CustomerCollectionReminder(long id, String customerId, LocalDate weekStart,
            CollectionChannel channel, Status status, BigDecimal balanceSnapshot, LocalDateTime generatedAt,
            LocalDateTime lastAttemptAt, int attemptCount, ResultCode resultCode, UUID attemptId, UUID attemptOwnerId) {
        this(id, customerId, weekStart, channel, status, balanceSnapshot, generatedAt, lastAttemptAt,
                attemptCount, resultCode, attemptId, attemptOwnerId, legacyEvidence(status, resultCode));
    }

    private static ProviderEvidence legacyEvidence(Status status, ResultCode resultCode) {
        if (status == Status.SENT && resultCode == ResultCode.SENT) return ProviderEvidence.SMTP_ACCEPTED;
        if (status == Status.FAILED && resultCode == ResultCode.PROVIDER_REJECTED) return ProviderEvidence.SMTP_REJECTED;
        if (status == Status.PENDING_REVIEW && resultCode == ResultCode.SIMULATED) return ProviderEvidence.SIMULATED;
        return null;
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
        if (status == Status.SENDING && (attemptCount == 0 || attemptId == null || attemptOwnerId == null)) {
            throw new IllegalArgumentException("Un intento en curso requiere identidad y propietario persistidos.");
        }
        if (status == Status.RECONCILIATION_REQUIRED
                && (attemptCount == 0 || attemptId == null || resultCode != ResultCode.PROVIDER_OUTCOME_UNCERTAIN
                || (providerEvidence != ProviderEvidence.SMTP_ACCEPTED
                && providerEvidence != ProviderEvidence.SMTP_REJECTED
                && providerEvidence != ProviderEvidence.SMTP_OUTCOME_UNCERTAIN))) {
            throw new IllegalArgumentException("La conciliación requiere un intento incierto identificado.");
        }
        if (status == Status.SENDING && providerEvidence != null) {
            throw new IllegalArgumentException("Un envío en curso todavía no tiene evidencia del proveedor.");
        }
        if (providerEvidence == ProviderEvidence.SMTP_ACCEPTED
                && resultCode != ResultCode.SENT && resultCode != ResultCode.PROVIDER_OUTCOME_UNCERTAIN) {
            throw new IllegalArgumentException("La aceptación SMTP debe conservar un resultado compatible.");
        }
        if (providerEvidence == ProviderEvidence.SMTP_ACCEPTED
                && ((resultCode == ResultCode.SENT && status != Status.SENT)
                || (resultCode == ResultCode.PROVIDER_OUTCOME_UNCERTAIN && status != Status.RECONCILIATION_REQUIRED))) {
            throw new IllegalArgumentException("La aceptación SMTP requiere un estado persistido coherente.");
        }
        if (providerEvidence == ProviderEvidence.SMTP_REJECTED
                && resultCode != ResultCode.PROVIDER_REJECTED
                && resultCode != ResultCode.PROVIDER_OUTCOME_UNCERTAIN) {
            throw new IllegalArgumentException("El rechazo SMTP debe conservar un resultado compatible.");
        }
        if (providerEvidence == ProviderEvidence.SMTP_REJECTED
                && ((resultCode == ResultCode.PROVIDER_REJECTED && status != Status.FAILED)
                || (resultCode == ResultCode.PROVIDER_OUTCOME_UNCERTAIN && status != Status.RECONCILIATION_REQUIRED))) {
            throw new IllegalArgumentException("El rechazo SMTP requiere un estado persistido coherente.");
        }
        if (providerEvidence == ProviderEvidence.SMTP_OUTCOME_UNCERTAIN
                && resultCode != ResultCode.PROVIDER_OUTCOME_UNCERTAIN) {
            throw new IllegalArgumentException("La incertidumbre SMTP debe conservar un resultado compatible.");
        }
        if (providerEvidence == ProviderEvidence.SMTP_OUTCOME_UNCERTAIN
                && status != Status.RECONCILIATION_REQUIRED) {
            throw new IllegalArgumentException("La incertidumbre SMTP requiere conciliación.");
        }
        if (providerEvidence == ProviderEvidence.SIMULATED && resultCode != ResultCode.SIMULATED) {
            throw new IllegalArgumentException("La simulación debe conservar un resultado compatible.");
        }
        if (providerEvidence == ProviderEvidence.SIMULATED && status != Status.PENDING_REVIEW) {
            throw new IllegalArgumentException("Una simulación no puede marcarse como un envío real.");
        }
        if (status == Status.SENT && (resultCode != ResultCode.SENT
                || providerEvidence != ProviderEvidence.SMTP_ACCEPTED)) {
            throw new IllegalArgumentException("Un aviso aceptado requiere evidencia explícita del proveedor.");
        }
        if (status == Status.FAILED && resultCode == ResultCode.PROVIDER_REJECTED
                && providerEvidence != ProviderEvidence.SMTP_REJECTED) {
            throw new IllegalArgumentException("Un rechazo definitivo requiere evidencia explícita del proveedor.");
        }
    }
}
