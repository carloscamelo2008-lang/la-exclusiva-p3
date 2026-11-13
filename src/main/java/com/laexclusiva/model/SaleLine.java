package com.laexclusiva.model;

import java.math.BigDecimal;

/**
 * Representa una línea de venta y vincula un producto, una presentación, una cantidad y el precio
 * aplicado. Conserva una copia del producto y el precio configurado al construirla, de modo que
 * cambios posteriores del catálogo no alteren el subtotal histórico. Conserva y expone la
 * referencia a la presentación recibida.
 */
public final class SaleLine {

    private final BeerProduct product;
    private final SalePresentation presentation;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final int unitsPerBasketSnapshot;

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
        this.unitsPerBasketSnapshot = product.getUnitsPerBasket();
    }

    private SaleLine(BeerProduct product, SalePresentation presentation, int quantity, BigDecimal unitPrice,
                     int unitsPerBasketSnapshot) {
        this.product = product.copy();
        this.presentation = presentation;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitsPerBasketSnapshot = unitsPerBasketSnapshot;
    }

    /**
     * Reconstruye una línea persistida con el precio y la capacidad capturados al vender.
     * No consulta los precios ni la capacidad actuales del producto para reemplazar esos valores.
     *
     * @param product producto asociado a la línea
     * @param presentation código de presentación recuperado de la base de datos
     * @param quantity cantidad vendida
     * @param unitPrice precio histórico por presentación
     * @param unitsPerBasketSnapshot capacidad de canasta aplicada al registrar la venta
     * @return línea reconstruida con sus valores históricos
     * @throws IllegalArgumentException si falta un dato, la cantidad/capacidad no es válida o el
     *                                  precio no es positivo
     */
    public static SaleLine reconstitute(BeerProduct product, SalePresentation presentation, int quantity,
                                        BigDecimal unitPrice, int unitsPerBasketSnapshot) {
        if (product == null || presentation == null || unitPrice == null) {
            throw new IllegalArgumentException("El producto, la presentación y el precio histórico son obligatorios.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad de la línea debe ser mayor que cero.");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio aplicado debe ser mayor que cero.");
        }
        if (unitsPerBasketSnapshot <= 0 || unitsPerBasketSnapshot % 2 != 0) {
            throw new IllegalArgumentException("La capacidad histórica de canasta debe ser positiva y par.");
        }
        return new SaleLine(product, presentation, quantity, unitPrice, unitsPerBasketSnapshot);
    }

    /**
     * Copia los datos de la línea y conserva el precio histórico aplicado.
     *
     * @return nueva línea con los mismos datos y el precio histórico aplicado
     */
    public SaleLine copy() {
        return new SaleLine(product, presentation, quantity, unitPrice, unitsPerBasketSnapshot);
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
     * Devuelve la capacidad de canasta capturada para esta venta.
     *
     * @return unidades por canasta según el catálogo al momento de vender
     */
    public int getUnitsPerBasketSnapshot() {
        return unitsPerBasketSnapshot;
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
