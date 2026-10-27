package com.laexclusiva.model;

/**
 * Una línea de venta tal como la necesita inventario: qué producto, en qué presentación
 * y cuántas veces. Ejemplo: 2 medias canastas de Poker 750 -> (POKER_750, HALF_BASKET, 2).
 * Es un "record": un contenedor de datos inmutable, ideal para pasar información entre módulos.
 */
public record StockRequest(String productId, SalePresentation presentation, int quantity) {

    public StockRequest {
        // Validaciones en el constructor compacto: un pedido inválido no puede ni existir.
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("El id del producto es obligatorio.");
        }
        if (presentation == null) {
            throw new IllegalArgumentException("La presentación es obligatoria.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }
    }
}
