package com.laexclusiva.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BeerProductTest {

    private BeerProduct find(String id) {
        return InitialCatalog.create().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void p1_unitOfSmallReference() {
        BeerProduct p = find("COSTENITA_175");
        assertEquals(BigDecimal.valueOf(2_500L), p.priceFor(SalePresentation.UNIT));
        assertEquals(1, p.unitsFor(SalePresentation.UNIT));
    }

    @Test
    void p2_unitOfPoker750() {
        BeerProduct p = find("POKER_750");
        assertEquals(BigDecimal.valueOf(6_000L), p.priceFor(SalePresentation.UNIT));
        assertEquals(1, p.unitsFor(SalePresentation.UNIT));
    }

    @Test
    void p3_halfBasketDiscountsHalfTheCapacity() {
        assertEquals(19, find("POKER_250").unitsFor(SalePresentation.HALF_BASKET));
        assertEquals(15, find("COSTENA_BACANA_330").unitsFor(SalePresentation.HALF_BASKET));
        assertEquals(8, find("POKER_750").unitsFor(SalePresentation.HALF_BASKET));
        assertEquals(BigDecimal.valueOf(45_000L), find("POKER_750").priceFor(SalePresentation.HALF_BASKET));
    }

    @Test
    void p4_fullBasketDiscountsFullCapacity() {
        assertEquals(38, find("POKER_250").unitsFor(SalePresentation.FULL_BASKET));
        assertEquals(30, find("COLA_Y_POLA_LATA_330").unitsFor(SalePresentation.FULL_BASKET));
        assertEquals(16, find("POKER_750").unitsFor(SalePresentation.FULL_BASKET));
        assertEquals(BigDecimal.valueOf(85_000L), find("COSTENITA_175").priceFor(SalePresentation.FULL_BASKET));
        assertEquals(BigDecimal.valueOf(90_000L), find("POKER_750").priceFor(SalePresentation.FULL_BASKET));
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

    // ---- Dinero con BigDecimal

    @Test
    void pricesAreBigDecimalAndIndependentPerPresentation() {
        BeerProduct p = find("COSTENITA_175");
        assertEquals(BigDecimal.valueOf(2_500L), p.priceFor(SalePresentation.UNIT));
        assertEquals(BigDecimal.valueOf(45_000L), p.priceFor(SalePresentation.HALF_BASKET));
        assertEquals(BigDecimal.valueOf(85_000L), p.priceFor(SalePresentation.FULL_BASKET));
        // La canasta NO es unidad x capacidad: 38 x 2.500 = 95.000, pero el precio acordado es 85.000.
        assertNotEquals(p.priceFor(SalePresentation.UNIT).multiply(BigDecimal.valueOf(38)),
                p.priceFor(SalePresentation.FULL_BASKET));
    }

    @Test
    void subtotalIsComputedWithBigDecimalOperations() {
        BigDecimal subtotal = find("POKER_750").priceFor(SalePresentation.UNIT).multiply(BigDecimal.valueOf(3));
        assertEquals(0, BigDecimal.valueOf(18_000L).compareTo(subtotal));
    }

    @Test
    void definePriceRejectsNullZeroAndNegativeAmounts() {
        BeerProduct p = new BeerProduct("X", "X", 330, 30, ContainerType.NON_RETURNABLE_CAN, null);
        assertThrows(IllegalArgumentException.class, () -> p.definePrice(null, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> p.definePrice(SalePresentation.UNIT, null));
        assertThrows(IllegalArgumentException.class, () -> p.definePrice(SalePresentation.UNIT, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> p.definePrice(SalePresentation.UNIT, BigDecimal.valueOf(-1L)));
    }

    @Test
    void priceForWithoutDefinedPriceIsRejected() {
        BeerProduct p = new BeerProduct("X", "X", 330, 30, ContainerType.NON_RETURNABLE_CAN, null);
        assertThrows(IllegalStateException.class, () -> p.priceFor(SalePresentation.UNIT));
    }

    // ---- Presentación nula y capacidades inválidas

    @Test
    void nullPresentationIsRejectedExplicitlyNotWithNullPointer() {
        BeerProduct p = find("POKER_750");
        assertThrows(IllegalArgumentException.class, () -> p.priceFor(null));
        assertThrows(IllegalArgumentException.class, () -> p.unitsFor(null));
    }

    @Test
    void oddZeroAndNegativeCapacitiesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeerProduct("X", "X", 330, 31, ContainerType.NON_RETURNABLE_CAN, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BeerProduct("X", "X", 330, 0, ContainerType.NON_RETURNABLE_CAN, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BeerProduct("X", "X", 330, -30, ContainerType.NON_RETURNABLE_CAN, null));
    }

    // ---- Desbordamiento

    @Test
    void equivalentUnitsRejectsMultiplicationOverflow() {
        // 38 x Integer.MAX_VALUE no cabe en un int.
        assertThrows(IllegalArgumentException.class,
                () -> find("POKER_250").equivalentUnits(Integer.MAX_VALUE, 0));
    }

    @Test
    void equivalentUnitsRejectsAdditionOverflow() {
        BeerProduct small = new BeerProduct("X", "X", 330, 2, ContainerType.NON_RETURNABLE_CAN, null);
        // 1.073.741.823 x 2 = 2.147.483.646 (cabe); sumar 2 sueltas desborda.
        assertEquals(Integer.MAX_VALUE - 1, small.equivalentUnits(1_073_741_823, 0));
        assertThrows(IllegalArgumentException.class, () -> small.equivalentUnits(1_073_741_823, 2));
    }

    @Test
    void equivalentUnitsAtTheLimitStillWorks() {
        BeerProduct small = new BeerProduct("X", "X", 330, 2, ContainerType.NON_RETURNABLE_CAN, null);
        assertEquals(Integer.MAX_VALUE, small.equivalentUnits(1_073_741_823, 1));
    }
}
