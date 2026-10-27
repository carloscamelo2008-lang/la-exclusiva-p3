package com.laexclusiva.model;

/**
 * Se lanza cuando se intenta descontar más unidades de las que hay en inventario.
 * Es una excepción propia (en lugar de una genérica) para que quien la capture, por ejemplo
 * el menú de consola, pueda mostrar un mensaje claro y saber qué producto falló.
 */
public class InsufficientStockException extends RuntimeException {

    private final String productId;
    private final int requestedUnits;
    private final int availableUnits;

    public InsufficientStockException(String productId, int requestedUnits, int availableUnits) {
        super("Stock insuficiente de " + productId + ": se requieren " + requestedUnits
                + " unidades y solo hay " + availableUnits + ".");
        this.productId = productId;
        this.requestedUnits = requestedUnits;
        this.availableUnits = availableUnits;
    }

    public String getProductId() { return productId; }
    public int getRequestedUnits() { return requestedUnits; }
    public int getAvailableUnits() { return availableUnits; }
}
