package com.laexclusiva.model;

import java.math.BigDecimal;
import java.util.List;

/** Datos de catálogo ficticios compartidos exclusivamente por las pruebas automatizadas. */
public final class InitialCatalog {

    private InitialCatalog() { }

    public static List<BeerProduct> create() {
        return List.of(
                small("COSTENITA_175", "Costeñita", 175, 38, ContainerType.RETURNABLE_EXCLUSIVE, null),
                small("POKER_250", "Poker", 250, 38, ContainerType.RETURNABLE_SHARED, "ENVASE_250"),
                small("AGUILITA_250", "Aguilita", 250, 38, ContainerType.RETURNABLE_SHARED, "ENVASE_250"),
                small("COSTENA_BACANA_330", "Costeña Bacana", 330, 30, ContainerType.RETURNABLE_EXCLUSIVE, null),
                small("COLA_Y_POLA_LATA_330", "Cola y Pola", 330, 30, ContainerType.NON_RETURNABLE_CAN, null),
                poker750());
    }

    private static BeerProduct small(String id, String name, int ml, int perBasket,
                                     ContainerType type, String group) {
        BeerProduct product = new BeerProduct(id, name, ml, perBasket, type, group);
        product.definePrice(SalePresentation.UNIT, BigDecimal.valueOf(2_500L));
        product.definePrice(SalePresentation.HALF_BASKET, BigDecimal.valueOf(45_000L));
        product.definePrice(SalePresentation.FULL_BASKET, BigDecimal.valueOf(85_000L));
        return product;
    }

    private static BeerProduct poker750() {
        BeerProduct product = new BeerProduct("POKER_750", "Poker", 750, 16,
                ContainerType.RETURNABLE_EXCLUSIVE, null);
        product.definePrice(SalePresentation.UNIT, BigDecimal.valueOf(6_000L));
        product.definePrice(SalePresentation.HALF_BASKET, BigDecimal.valueOf(45_000L));
        product.definePrice(SalePresentation.FULL_BASKET, BigDecimal.valueOf(90_000L));
        return product;
    }
}
