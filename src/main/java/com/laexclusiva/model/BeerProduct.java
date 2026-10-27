package com.laexclusiva.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Referencia del catálogo (por ejemplo, Poker 750 ml).
 *
 * GRASP: Information Expert. Conoce su capacidad por canasta, por lo tanto es quien
 * calcula unidades equivalentes y unidades a descontar. Así la regla vive en un solo
 * lugar y la venta y el inventario no la duplican.
 *
 * Los precios de media canasta y canasta completa se guardan por presentación
 * (no se calculan multiplicando el precio unitario).
 */
public class BeerProduct {

    private final String id;
    private final String name;
    private final int volumeMl;
    private final int unitsPerBasket;
    private final ContainerType containerType;
    private final String containerGroup; // null si el envase no se comparte
    private final Map<String, Long> prices = new LinkedHashMap<>();

    public BeerProduct(String id, String name, int volumeMl, int unitsPerBasket,
                       ContainerType containerType, String containerGroup) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El identificador del producto es obligatorio.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
        }
        if (volumeMl <= 0) {
            throw new IllegalArgumentException("El volumen debe ser mayor que cero.");
        }
        if (unitsPerBasket <= 0 || unitsPerBasket % 2 != 0) {
            throw new IllegalArgumentException(
                    "La capacidad de la canasta debe ser un número par mayor que cero.");
        }
        if (containerType == null) {
            throw new IllegalArgumentException("El tipo de envase es obligatorio.");
        }
        if (containerType == ContainerType.RETURNABLE_SHARED
                && (containerGroup == null || containerGroup.isBlank())) {
            throw new IllegalArgumentException("Un envase compartido requiere un grupo de envase.");
        }
        this.id = id;
        this.name = name;
        this.volumeMl = volumeMl;
        this.unitsPerBasket = unitsPerBasket;
        this.containerType = containerType;
        this.containerGroup = containerType == ContainerType.RETURNABLE_SHARED ? containerGroup : null;
    }

    public void definePrice(SalePresentation presentation, long pricePesos) {
        if (presentation == null) {
            throw new IllegalArgumentException("La presentación es obligatoria.");
        }
        if (pricePesos <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero.");
        }
        prices.put(presentation.code(), pricePesos);
    }

    public long priceFor(SalePresentation presentation) {
        Long price = prices.get(presentation.code());
        if (price == null) {
            throw new IllegalStateException("El producto " + name + " no tiene precio definido para "
                    + presentation.code() + ".");
        }
        return price;
    }

    public boolean hasAllPrices() {
        return prices.containsKey(SalePresentation.UNIT.code())
                && prices.containsKey(SalePresentation.HALF_BASKET.code())
                && prices.containsKey(SalePresentation.FULL_BASKET.code());
    }

    /** Unidades que descuenta del inventario vender una presentación de este producto. */
    public int unitsFor(SalePresentation presentation) {
        return presentation.unitsToDiscount(unitsPerBasket);
    }

    /** unidades totales = canastas completas × capacidad + unidades sueltas. */
    public int equivalentUnits(int fullBaskets, int looseUnits) {
        if (fullBaskets < 0 || looseUnits < 0) {
            throw new IllegalArgumentException("Las cantidades no pueden ser negativas.");
        }
        return fullBaskets * unitsPerBasket + looseUnits;
    }

    public boolean sharesContainerWith(BeerProduct other) {
        return other != null && containerGroup != null && containerGroup.equals(other.containerGroup);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getVolumeMl() { return volumeMl; }
    public int getUnitsPerBasket() { return unitsPerBasket; }
    public ContainerType getContainerType() { return containerType; }
    public String getContainerGroup() { return containerGroup; }

    @Override
    public String toString() {
        return name + " " + volumeMl + " ml (" + unitsPerBasket + " u/canasta)";
    }
}
