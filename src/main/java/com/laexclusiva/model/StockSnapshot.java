package com.laexclusiva.model;

/**
 * Foto de solo lectura de las existencias de un producto.
 * Se entrega a quien consulta (menú, ventas) para que no pueda modificar el inventario real.
 */
public record StockSnapshot(String productId, int fullBaskets, int looseUnits, int totalUnits) {
}
