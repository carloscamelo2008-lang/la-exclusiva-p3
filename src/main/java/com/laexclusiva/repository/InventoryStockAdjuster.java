package com.laexclusiva.repository;

/** Ajusta existencias por delta, sin escribir un snapshot completo del inventario. */
public interface InventoryStockAdjuster {

    /**
     * Aumenta atómicamente las existencias.
     *
     * @param productId identificador del producto
     * @param units unidades positivas que se reciben
     * @return total resultante
     */
    int increaseStock(String productId, int units);

    /**
     * Descuenta atómicamente las existencias si hay unidades suficientes.
     *
     * @param productId identificador del producto
     * @param units unidades positivas que se venden
     * @return total resultante
     * @throws com.laexclusiva.model.InsufficientStockException si no hay unidades suficientes
     */
    int decreaseStock(String productId, int units);
}
