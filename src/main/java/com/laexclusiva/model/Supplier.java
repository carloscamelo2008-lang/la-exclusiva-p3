package com.laexclusiva.model;

import java.time.LocalDateTime;

/**
 * Proveedor del catálogo de compras.
 *
 * @param id identificador positivo, o nulo antes de persistir
 * @param name nombre obligatorio de hasta 120 caracteres
 * @param taxId identificación tributaria opcional de hasta 40 caracteres
 * @param contactInfo información de contacto opcional de hasta 200 caracteres
 * @param active indica si el proveedor está habilitado
 * @param createdAt fecha y hora de creación
 */
public record Supplier(Long id, String name, String taxId, String contactInfo,
                       boolean active, LocalDateTime createdAt) {
    /**
     * Construye un proveedor con los límites del catálogo.
     *
     * @throws IllegalArgumentException si el identificador, los textos o la fecha son inválidos
     */
    public Supplier {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("El ID del proveedor debe ser positivo.");
        }
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("El nombre debe tener entre 1 y 120 caracteres.");
        }
        if ((taxId != null && taxId.length() > 40) || (contactInfo != null && contactInfo.length() > 200)) {
            throw new IllegalArgumentException("La identificación tributaria o el contacto exceden su longitud.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("La fecha de creación es obligatoria.");
        }
    }
}
