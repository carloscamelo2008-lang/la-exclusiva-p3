package com.laexclusiva.model;

/**
 * Presentación comercial de una unidad individual.
 * La cantidad descontada no depende de la capacidad de la canasta del producto.
 */
public class UnitPresentation implements SalePresentation {

    /** {@inheritDoc} */
    @Override
    public String code() {
        return "UNIT";
    }

    /**
     * {@inheritDoc}
     *
     * @param unitsPerBasket capacidad de la canasta; no afecta la venta por unidad
     * @return siempre una unidad
     */
    @Override
    public int unitsToDiscount(int unitsPerBasket) {
        return 1;
    }
}
