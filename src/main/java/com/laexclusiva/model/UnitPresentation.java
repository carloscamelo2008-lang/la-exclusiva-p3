package com.laexclusiva.model;

/** Venta de una sola unidad. */
public class UnitPresentation implements SalePresentation {

    @Override
    public String code() {
        return "UNIT";
    }

    @Override
    public int unitsToDiscount(int unitsPerBasket) {
        return 1;
    }
}
