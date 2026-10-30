package com.laexclusiva.model;

/**
 * Contrato para representar una presentación comercial de un producto: unidad, media canasta
 * o canasta completa. {@link BeerProduct} usa el código como llave del precio configurado y
 * delega aquí el cálculo de unidades que debe descontar el inventario.
 *
 * GRASP: Polimorfismo. SOLID: OCP (agregar una presentación nueva = una clase nueva,
 * sin modificar BeerProduct) y LSP (todas respetan el mismo contrato).
 */
public interface SalePresentation {

    /** Presentación de una unidad individual. */
    SalePresentation UNIT = new UnitPresentation();
    /** Presentación de media canasta. */
    SalePresentation HALF_BASKET = new HalfBasketPresentation();
    /** Presentación de una canasta completa. */
    SalePresentation FULL_BASKET = new FullBasketPresentation();

    /**
     * Devuelve el identificador estable de esta presentación, usado como llave de precios.
     *
     * @return código de la presentación, por ejemplo {@code UNIT} o {@code HALF_BASKET}
     */
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
