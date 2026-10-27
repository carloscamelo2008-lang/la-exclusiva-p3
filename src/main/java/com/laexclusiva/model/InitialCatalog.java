package com.laexclusiva.model;

import java.util.List;

/**
 * Las seis referencias iniciales con capacidades y tarifas acordadas en el plan de trabajo.
 * Se separa de BeerProduct (SRP): el modelo define reglas; este catálogo solo carga datos.
 */
public final class InitialCatalog {

    private InitialCatalog() {
    }

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
        BeerProduct p = new BeerProduct(id, name, ml, perBasket, type, group);
        p.definePrice(SalePresentation.UNIT, 2_500);
        p.definePrice(SalePresentation.HALF_BASKET, 45_000);
        p.definePrice(SalePresentation.FULL_BASKET, 85_000);
        return p;
    }

    private static BeerProduct poker750() {
        BeerProduct p = new BeerProduct("POKER_750", "Poker", 750, 16,
                ContainerType.RETURNABLE_EXCLUSIVE, null);
        p.definePrice(SalePresentation.UNIT, 6_000);
        p.definePrice(SalePresentation.HALF_BASKET, 45_000);
        p.definePrice(SalePresentation.FULL_BASKET, 90_000);
        return p;
    }
}
