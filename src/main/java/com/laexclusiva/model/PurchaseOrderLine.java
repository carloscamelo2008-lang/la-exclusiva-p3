package com.laexclusiva.model;

/**
 * Línea de compra con la capacidad de canasta capturada al ordenar y el acumulado recibido.
 *
 * @param id identificador positivo, o nulo antes de persistir
 * @param purchaseOrderId identificador positivo del pedido
 * @param productId identificador del producto
 * @param orderedBaskets canastas completas solicitadas
 * @param orderedLooseUnits unidades sueltas solicitadas
 * @param unitsPerBasketSnapshot capacidad de la canasta al crear el pedido
 * @param orderedEquivalentUnits total solicitado en unidades equivalentes
 * @param receivedEquivalentUnits total recibido hasta el momento en unidades equivalentes
 */
public record PurchaseOrderLine(Long id, long purchaseOrderId, String productId,
                                int orderedBaskets, int orderedLooseUnits, int unitsPerBasketSnapshot,
                                int orderedEquivalentUnits, int receivedEquivalentUnits) {
    /**
     * Construye una línea comprobando el total persistido y el límite de recepción.
     *
     * @throws IllegalArgumentException si la identidad, capacidad o cantidades son inválidas
     */
    public PurchaseOrderLine {
        if ((id != null && id <= 0) || purchaseOrderId <= 0) {
            throw new IllegalArgumentException("Los identificadores de la línea deben ser positivos.");
        }
        if (productId == null || productId.isBlank() || productId.length() > 64) {
            throw new IllegalArgumentException("El ID del producto debe tener entre 1 y 64 caracteres.");
        }
        int calculatedUnits = equivalentUnits(orderedBaskets, orderedLooseUnits, unitsPerBasketSnapshot);
        if (orderedEquivalentUnits != calculatedUnits) {
            throw new IllegalArgumentException("El total pedido no coincide con las cantidades de la línea.");
        }
        if (receivedEquivalentUnits < 0 || receivedEquivalentUnits > orderedEquivalentUnits) {
            throw new IllegalArgumentException("La cantidad recibida debe estar entre cero y el total pedido.");
        }
    }

    /** @return unidades equivalentes que aún pueden recibirse */
    public int pendingEquivalentUnits() {
        return orderedEquivalentUnits - receivedEquivalentUnits;
    }

    /**
     * Calcula el total usando la capacidad histórica, incluso si el catálogo cambió.
     *
     * @param baskets número de canastas completas
     * @param looseUnits número de unidades sueltas
     * @param unitsPerBasket capacidad registrada por canasta
     * @return total positivo en unidades equivalentes
     * @throws IllegalArgumentException si las cantidades o la capacidad son inválidas, el total
     *         es cero o supera el rango de {@code int}
     */
    public static int equivalentUnits(int baskets, int looseUnits, int unitsPerBasket) {
        if (baskets < 0 || looseUnits < 0 || unitsPerBasket <= 0) {
            throw new IllegalArgumentException("Las cantidades deben ser no negativas y la capacidad positiva.");
        }
        int total;
        try {
            total = Math.addExact(Math.multiplyExact(baskets, unitsPerBasket), looseUnits);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("El total de unidades equivalentes supera el rango permitido.", overflow);
        }
        if (total == 0) {
            throw new IllegalArgumentException("La línea debe contener al menos una unidad.");
        }
        return total;
    }
}
