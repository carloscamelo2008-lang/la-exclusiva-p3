package com.laexclusiva.model;

/**
 * Venta de media canasta: la mitad de las unidades de una canasta (19, 15 u 8).
 * Supone una capacidad positiva y par (ver {@link SalePresentation#unitsToDiscount(int)});
 * BeerProduct rechaza capacidades impares al construirse, por eso aquí la división es exacta.
 */
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
