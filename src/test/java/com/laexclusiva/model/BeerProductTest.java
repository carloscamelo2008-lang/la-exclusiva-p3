package com.laexclusiva.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BeerProductTest {

    private BeerProduct find(String id) {
        return InitialCatalog.create().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void p1_unitOfSmallReference() {
        BeerProduct p = find("COSTENITA_175");
        assertEquals(2_500, p.priceFor(SalePresentation.UNIT));
        assertEquals(1, p.unitsFor(SalePresentation.UNIT));
    }

    @Test
    void p2_unitOfPoker750() {
        BeerProduct p = find("POKER_750");
        assertEquals(6_000, p.priceFor(SalePresentation.UNIT));
        assertEquals(1, p.unitsFor(SalePresentation.UNIT));
    }

    @Test
    void p3_halfBasketDiscountsHalfTheCapacity() {
        assertEquals(19, find("POKER_250").unitsFor(SalePresentation.HALF_BASKET));
        assertEquals(15, find("COSTENA_BACANA_330").unitsFor(SalePresentation.HALF_BASKET));
        assertEquals(8, find("POKER_750").unitsFor(SalePresentation.HALF_BASKET));
        assertEquals(45_000, find("POKER_750").priceFor(SalePresentation.HALF_BASKET));
    }

    @Test
    void p4_fullBasketDiscountsFullCapacity() {
        assertEquals(38, find("POKER_250").unitsFor(SalePresentation.FULL_BASKET));
        assertEquals(30, find("COLA_Y_POLA_LATA_330").unitsFor(SalePresentation.FULL_BASKET));
        assertEquals(16, find("POKER_750").unitsFor(SalePresentation.FULL_BASKET));
        assertEquals(85_000, find("COSTENITA_175").priceFor(SalePresentation.FULL_BASKET));
        assertEquals(90_000, find("POKER_750").priceFor(SalePresentation.FULL_BASKET));
    }

    @Test
    void equivalentUnitsCombinesBasketsAndLooseUnits() {
        assertEquals(2 * 38 + 5, find("POKER_250").equivalentUnits(2, 5));
        assertThrows(IllegalArgumentException.class, () -> find("POKER_250").equivalentUnits(-1, 0));
    }

    @Test
    void poker250AndAguilita250ShareContainer() {
        assertTrue(find("POKER_250").sharesContainerWith(find("AGUILITA_250")));
    }

    @Test
    void invalidProductIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeerProduct("X", "X", 330, 0, ContainerType.NON_RETURNABLE_CAN, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BeerProduct("X", " ", 330, 30, ContainerType.NON_RETURNABLE_CAN, null));
    }

    @Test
    void initialCatalogHasSixReferences() {
        assertEquals(6, InitialCatalog.create().size());
    }
}
