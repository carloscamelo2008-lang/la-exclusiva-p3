package com.laexclusiva.model;

/**
 * Existencias de UN producto.
 *
 * Decisión de diseño: internamente se guarda un único total de unidades. Las canastas completas
 * y las unidades sueltas se DERIVAN de ese total usando la capacidad del producto:
 *     canastas completas = total / capacidad      unidades sueltas = total % capacidad
 * Así se cumple la equivalencia del plan (total = canastas × capacidad + sueltas) y, al vender
 * unidades sueltas cuando ya no quedan, "se abre" una canasta de forma automática y consistente.
 *
 * GRASP: Information Expert (esta clase conoce sus existencias y protege su invariante).
 * Invariante: el total NUNCA es negativo; ninguna operación puede violarlo.
 */
public class InventoryItem {

    private final BeerProduct product;
    private int totalUnits;

    public InventoryItem(BeerProduct product) {
        // Bloque 1: validación del producto asociado.
        if (product == null) {
            throw new IllegalArgumentException("El producto del inventario es obligatorio.");
        }
        this.product = product;
        this.totalUnits = 0; // un ítem nuevo comienza sin existencias
    }

    /** Aumenta las existencias (compra, recepción de pedido, ajuste). */
    public void add(int units) {
        // Bloque 2: solo se aceptan cantidades positivas.
        if (units <= 0) {
            throw new IllegalArgumentException("La cantidad a ingresar debe ser mayor que cero.");
        }
        try {
            totalUnits = Math.addExact(totalUnits, units); // no modifica nada si desborda
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "La cantidad es demasiado grande: el inventario superaría el máximo permitido.");
        }
    }

    /** Indica si se pueden retirar estas unidades sin dejar el inventario en negativo. */
    public boolean canRemove(int units) {
        return units > 0 && units <= totalUnits;
    }

    /** Disminuye las existencias (venta). Rechaza la operación si no hay suficiente stock. */
    public void remove(int units) {
        // Bloque 3: validar la cantidad solicitada.
        if (units <= 0) {
            throw new IllegalArgumentException("La cantidad a descontar debe ser mayor que cero.");
        }
        // Bloque 4: proteger el invariante; si no alcanza, no se modifica nada.
        if (units > totalUnits) {
            throw new InsufficientStockException(product.getId(), units, totalUnits);
        }
        totalUnits -= units;
    }

    /** Canastas completas disponibles. */
    public int fullBaskets() {
        return totalUnits / product.getUnitsPerBasket();
    }

    /** Unidades sueltas (las que no completan una canasta). */
    public int looseUnits() {
        return totalUnits % product.getUnitsPerBasket();
    }

    public int totalUnits() { return totalUnits; }

    public BeerProduct getProduct() { return product; }
}
