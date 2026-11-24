package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Recomendación explicable de seguimiento; no predice incumplimientos ni califica al cliente. */
public record CollectionRecommendation(String customerId, String customerName, BigDecimal outstandingBalance,
                                       Priority priority, LocalDate commitmentDate, String debtAge,
                                       boolean recentPayment, String explanation) {
    public enum Priority { HIGH, MEDIUM, UPCOMING, GENERAL }

    /** Valida el resultado que consume la tabla de alertas. */
    public CollectionRecommendation {
        if (customerId == null || customerId.isBlank() || customerName == null || outstandingBalance == null
                || outstandingBalance.signum() <= 0 || priority == null || debtAge == null
                || explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("La recomendación debe explicar un saldo pendiente real.");
        }
    }
}
