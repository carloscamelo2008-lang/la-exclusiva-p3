package com.laexclusiva.model;

/**
 * Presentación comercial de media canasta. Descuenta la mitad de las unidades definidas para
 * el producto (19, 15 u 8 en las capacidades actuales de 38, 30 y 16 unidades).
 * {@link BeerProduct} exige una capacidad positiva y par, por lo que la división es exacta.
 */
public class HalfBasketPresentation implements SalePresentation {

    /**
     * {@inheritDoc}
     *
     * @return código estable {@code HALF_BASKET}
     */
    @Override
    public String code() {
        return "HALF_BASKET";
    }

    /**
     * Calcula la mitad de la capacidad de la canasta del producto.
     *
     * @param unitsPerBasket capacidad positiva y par, validada al crear el producto
     * @return unidades equivalentes a media canasta
     */
    @Override
    public int unitsToDiscount(int unitsPerBasket) {
        return unitsPerBasket / 2;
    }
}
