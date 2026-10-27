package com.laexclusiva.model;

/** Venta de media canasta: la mitad de las unidades de una canasta (19, 15 u 8). */
public class HalfBasketPresentation implements SalePresentation {

    @Override
    public String code() {
        return "HALF_BASKET";
    }

    @Override
    public int unitsToDiscount(int unitsPerBasket) {
        return unitsPerBasket / 2;
    }
}
