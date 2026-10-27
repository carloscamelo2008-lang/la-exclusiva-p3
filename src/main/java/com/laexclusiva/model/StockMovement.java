package com.laexclusiva.model;

import java.time.LocalDateTime;

/**
 * Registro inmutable de una entrada o salida de inventario (trazabilidad).
 * El constructor compacto impide crear movimientos con datos inválidos.
 */
public record StockMovement(String productId, MovementType type, int units,
                            String reason, LocalDateTime timestamp) {

    public StockMovement {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("El id del producto del movimiento es obligatorio.");
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio.");
        }
        if (units <= 0) {
            throw new IllegalArgumentException("Las unidades del movimiento deben ser mayores que cero.");
        }
        requireReason(reason);
        if (timestamp == null) {
            throw new IllegalArgumentException("La fecha y hora del movimiento son obligatorias.");
        }
    }

    /**
     * Valida la razón de un movimiento. Es público para que los servicios puedan comprobarla
     * ANTES de modificar existencias (así un movimiento inválido no deja el stock cambiado).
     */
    public static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("La razón del movimiento es obligatoria.");
        }
    }
}
