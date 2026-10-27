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

    /** Unidades que se descuentan del inventario al vender esta presentación. */
    int unitsToDiscount(int unitsPerBasket);
}
