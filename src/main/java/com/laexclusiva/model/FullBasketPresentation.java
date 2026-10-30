package com.laexclusiva.model;

/**
 * Presentación comercial de una canasta completa. La cantidad de unidades depende de la
 * capacidad configurada para cada producto.
 */
public class FullBasketPresentation implements SalePresentation {

    /**
     * {@inheritDoc}
     *
     * @return código estable {@code FULL_BASKET}
     */
    @Override
    public String code() {
        return "FULL_BASKET";
    }

    /**
     * {@inheritDoc}
     *
     * @param unitsPerBasket capacidad de la canasta del producto
     * @return la capacidad completa expresada en unidades
     */
    @Override
    public int unitsToDiscount(int unitsPerBasket) {
        return unitsPerBasket;
    }
}
