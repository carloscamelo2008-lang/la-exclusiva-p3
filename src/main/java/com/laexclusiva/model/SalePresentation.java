package com.laexclusiva.model;

/**
 * Contrato de una presentación comercial (unidad, media canasta, canasta completa).
 *
 * GRASP: Polymorphism. SOLID: OCP (agregar una presentación nueva = una clase nueva,
 * sin modificar BeerProduct) y LSP (todas respetan el mismo contrato).
 */
public interface SalePresentation {

    SalePresentation UNIT = new UnitPresentation();
    SalePresentation HALF_BASKET = new HalfBasketPresentation();
    SalePresentation FULL_BASKET = new FullBasketPresentation();

    /** Identificador estable de la presentación, usado como llave de precios. */
    String code();

    /**
     * Unidades que se descuentan del inventario al vender esta presentación.
     *
     * @param unitsPerBasket capacidad de la canasta del producto. Debe ser positiva y par
     *                       (como las capacidades admitidas por el catálogo actual: 38, 30 y 16),
     *                       para que la media canasta sea un número entero de unidades.
     *                       BeerProduct garantiza esta condición al construirse.
     * @return unidades a descontar: unidad = 1; media canasta = capacidad / 2 (19, 15 u 8);
     *         canasta completa = capacidad (38, 30 o 16)
     */
    int unitsToDiscount(int unitsPerBasket);
}
