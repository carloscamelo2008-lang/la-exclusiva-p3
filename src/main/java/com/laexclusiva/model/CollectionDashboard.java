package com.laexclusiva.model;

import java.util.List;

/** Vista agregada y segura para la sección «Alertas y recordatorios». */
public record CollectionDashboard(int overdueCommitments, int agedDebts, int upcomingCommitments,
                                  int statementsPendingReview, List<CollectionRecommendation> recommendations) {
    /** Copia la lista para que las vistas no puedan alterar el resultado del servicio. */
    public CollectionDashboard {
        if (overdueCommitments < 0 || agedDebts < 0 || upcomingCommitments < 0 || statementsPendingReview < 0
                || recommendations == null) throw new IllegalArgumentException("Los contadores son obligatorios.");
        recommendations = List.copyOf(recommendations);
    }
}
