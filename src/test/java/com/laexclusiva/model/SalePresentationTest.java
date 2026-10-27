package com.laexclusiva.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Contrato de las presentaciones con las capacidades admitidas (positivas y pares). */
class SalePresentationTest {

    @Test
    void unitIsAlwaysOne() {
        assertEquals(1, SalePresentation.UNIT.unitsToDiscount(38));
        assertEquals(1, SalePresentation.UNIT.unitsToDiscount(30));
        assertEquals(1, SalePresentation.UNIT.unitsToDiscount(16));
    }

    @Test
    void halfBasketIsHalfTheCapacity() {
        assertEquals(19, SalePresentation.HALF_BASKET.unitsToDiscount(38));
        assertEquals(15, SalePresentation.HALF_BASKET.unitsToDiscount(30));
        assertEquals(8, SalePresentation.HALF_BASKET.unitsToDiscount(16));
    }

    @Test
    void fullBasketIsTheWholeCapacity() {
        assertEquals(38, SalePresentation.FULL_BASKET.unitsToDiscount(38));
        assertEquals(30, SalePresentation.FULL_BASKET.unitsToDiscount(30));
        assertEquals(16, SalePresentation.FULL_BASKET.unitsToDiscount(16));
    }
}
