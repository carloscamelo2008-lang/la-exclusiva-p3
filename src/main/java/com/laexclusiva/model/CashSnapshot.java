package com.laexclusiva.model;

import java.util.List;

/** Resultado de una actualización de Caja que agrupa filas visibles y sus indicadores. */
public record CashSnapshot(List<CashMovement> movements, CashSummary summary) {
    /**
     * Valida el resultado y copia las filas para que la vista no observe mutaciones posteriores.
     *
     * @param movements movimientos que cumplen los filtros visibles
     * @param summary totales del rango de fechas, sin aplicar el filtro de categoría
     * @throws IllegalArgumentException si faltan los movimientos o el resumen
     */
    public CashSnapshot {
        if (movements == null || summary == null) {
            throw new IllegalArgumentException("Los movimientos y el resumen de caja son obligatorios.");
        }
        movements = List.copyOf(movements);
    }
}
