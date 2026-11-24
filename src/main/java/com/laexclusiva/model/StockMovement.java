package com.laexclusiva.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro inmutable de una entrada o salida de inventario (trazabilidad).
 * El constructor compacto impide crear movimientos con datos inválidos.
 * El id técnico y el producto asociado están disponibles al reconstituir desde JDBC.
 */
public record StockMovement(String productId, MovementType type, int units,
                            String reason, LocalDateTime timestamp, Long id, BeerProduct product, Long actorUserId) {
    public StockMovement(String productId, MovementType type, int units, String reason,
                         LocalDateTime timestamp, Long id, BeerProduct product) {
        this(productId, type, units, reason, timestamp, id, product, null);
    }

    public StockMovement(String productId, MovementType type, int units, String reason, LocalDateTime timestamp) {
        this(productId, type, units, reason, timestamp, null, null);
    }

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
        if (product != null && !productId.equals(product.getId())) {
            throw new IllegalArgumentException("El producto asociado debe coincidir con el id del movimiento.");
        }
    }

    // El catálogo asociado es mutable y se puede cargar de nuevo sin cambiar el movimiento.
    @Override
    public boolean equals(Object other) {
        return other instanceof StockMovement movement
                && units == movement.units
                && type == movement.type
                && productId.equals(movement.productId)
                && reason.equals(movement.reason)
                && timestamp.equals(movement.timestamp)
                && Objects.equals(id, movement.id)
                && Objects.equals(actorUserId, movement.actorUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, type, units, reason, timestamp, id, actorUserId);
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
