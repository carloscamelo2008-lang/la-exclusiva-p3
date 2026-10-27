package com.laexclusiva.model;

import java.math.BigDecimal;

/**
 * Línea inmutable de una venta. Conserva el precio de la presentación configurado
 * en el producto al construir la línea, de modo que cambios futuros del catálogo
 * no alteren el subtotal histórico.
 */
public final class SaleLine {

    private final BeerProduct product;
    private final SalePresentation presentation;
    private final int quantity;
    private final BigDecimal unitPrice;

    public SaleLine(BeerProduct product, SalePresentation presentation, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("El producto de la línea es obligatorio.");
        }
        if (presentation == null) {
            throw new IllegalArgumentException("La presentación de la línea es obligatoria.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad de la línea debe ser mayor que cero.");
        }

        BigDecimal configuredPrice = product.priceFor(presentation);
        if (configuredPrice == null || configuredPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio aplicado debe ser mayor que cero.");
        }

        this.product = product.copy();
        this.presentation = presentation;
        this.quantity = quantity;
        this.unitPrice = configuredPrice;
    }

    private SaleLine(BeerProduct product, SalePresentation presentation, int quantity, BigDecimal unitPrice) {
        this.product = product.copy();
        this.presentation = presentation;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** Copia los datos de la línea y conserva el precio histórico aplicado. */
    public SaleLine copy() {
        return new SaleLine(product, presentation, quantity, unitPrice);
    }

    public BeerProduct getProduct() {
        return product.copy();
    }

    public SalePresentation getPresentation() {
        return presentation;
    }

    public int getQuantity() {
        return quantity;
    }

    /** Precio por unidad de presentación aplicado al registrar la línea. */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** Precio aplicado multiplicado por la cantidad de presentaciones vendidas. */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
