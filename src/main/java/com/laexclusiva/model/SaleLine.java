package com.laexclusiva.model;

import java.math.BigDecimal;

/**
 * Línea inmutable de una venta que vincula producto, presentación, cantidad y precio aplicado.
 * Captura el precio configurado al construirla, de modo que cambios posteriores del catálogo
 * no alteren el subtotal histórico. {@link Sale} conserva estas líneas como parte de la venta.
 */
public final class SaleLine {

    private final BeerProduct product;
    private final SalePresentation presentation;
    private final int quantity;
    private final BigDecimal unitPrice;

    /**
     * Crea una línea y captura el precio vigente para el producto y la presentación indicados.
     *
     * @param product producto del catálogo con precio configurado
     * @param presentation presentación comercial vendida
     * @param quantity cantidad de presentaciones, mayor que cero
     * @throws IllegalArgumentException si el producto o la presentación son nulos, la cantidad
     *                                  no es positiva o el precio no es positivo
     * @throws IllegalStateException si el producto no tiene precio configurado para la presentación
     */
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

    /**
     * Copia los datos de la línea y conserva el precio histórico aplicado.
     *
     * @return copia independiente de la línea
     */
    public SaleLine copy() {
        return new SaleLine(product, presentation, quantity, unitPrice);
    }

    /**
     * Devuelve una copia del producto guardado en la línea.
     *
     * @return copia del producto con el precio vigente al crear esta línea
     */
    public BeerProduct getProduct() {
        return product.copy();
    }

    public SalePresentation getPresentation() {
        return presentation;
    }

    public int getQuantity() {
        return quantity;
    }

    /**
     * Devuelve el precio de una presentación aplicado al registrar la línea.
     *
     * @return precio histórico unitario de la presentación
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * Calcula el subtotal multiplicando el precio aplicado por la cantidad vendida.
     *
     * @return subtotal de esta línea
     */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
