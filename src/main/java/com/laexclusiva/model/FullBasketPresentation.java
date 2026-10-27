package com.laexclusiva.model;

/** Venta de una canasta completa (38, 30 o 16 unidades según la referencia). */
public class FullBasketPresentation implements SalePresentation {

    @Override
    public String code() {
        return "FULL_BASKET";
    }

    @Override
    public int unitsToDiscount(int unitsPerBasket) {
        return unitsPerBasket;
    }
}
