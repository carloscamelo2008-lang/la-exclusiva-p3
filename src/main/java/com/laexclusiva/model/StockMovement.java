package com.laexclusiva.model;

import java.time.LocalDateTime;

/** Registro inmutable de una entrada o salida de inventario (trazabilidad). */
public record StockMovement(String productId, MovementType type, int units,
                            String reason, LocalDateTime timestamp) {
}
