package com.laexclusiva.model;

import java.time.LocalDateTime;

/**
 * Cabecera de un pedido de compra y datos de auditoría de su creación.
 *
 * @param id identificador positivo, o nulo antes de persistir
 * @param supplierId identificador positivo del proveedor
 * @param status estado del pedido
 * @param notes notas opcionales
 * @param createdByUserId identificador positivo de la cuenta persistida, si está disponible
 * @param createdByUsername nombre de usuario registrado al crear el pedido
 * @param createdAt fecha y hora de creación
 */
public record PurchaseOrder(Long id, long supplierId, PurchaseOrderStatus status, String notes,
                            Long createdByUserId, String createdByUsername, LocalDateTime createdAt) {
    /**
     * Construye la cabecera con identidad y trazabilidad válidas.
     *
     * @throws IllegalArgumentException si algún identificador o dato obligatorio es inválido
     */
    public PurchaseOrder {
        if ((id != null && id <= 0) || supplierId <= 0 || (createdByUserId != null && createdByUserId <= 0)) {
            throw new IllegalArgumentException("Los identificadores del pedido deben ser positivos.");
        }
        if (status == null || createdAt == null) {
            throw new IllegalArgumentException("El estado y la fecha de creación son obligatorios.");
        }
        if (createdByUsername == null || createdByUsername.isBlank() || createdByUsername.length() > 80) {
            throw new IllegalArgumentException("El nombre del usuario creador debe tener entre 1 y 80 caracteres.");
        }
    }
}
