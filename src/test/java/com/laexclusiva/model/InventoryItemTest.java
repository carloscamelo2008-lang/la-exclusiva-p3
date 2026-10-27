package com.laexclusiva.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryItemTest {

    private InventoryItem item;

    @BeforeEach
    void setUp() {
        // Poker 250 ml: capacidad 38 unidades por canasta.
        BeerProduct poker = new BeerProduct("POKER_250", "Poker", 250, 38,
                ContainerType.RETURNABLE_SHARED, "ENVASE_250");
        item = new InventoryItem(poker);
    }

    @Test
    void newItemStartsEmpty() {
        assertEquals(0, item.totalUnits());
        assertEquals(0, item.fullBaskets());
        assertEquals(0, item.looseUnits());
    }

    @Test
    void combinedRepresentationIsDerivedFromTotal() {
        item.add(2 * 38 + 5); // 2 canastas y 5 sueltas
        assertEquals(2, item.fullBaskets());
        assertEquals(5, item.looseUnits());
        assertEquals(81, item.totalUnits());
    }

    @Test
    void sellingLooseUnitsOpensABasket() {
        item.add(38);            // 1 canasta completa, 0 sueltas
        item.remove(1);          // se vende 1 unidad
        assertEquals(0, item.fullBaskets());
        assertEquals(37, item.looseUnits());
    }

    @Test
    void removeBeyondStockIsRejectedAndKeepsStock() {
        item.add(10);
        InsufficientStockException ex =
                assertThrows(InsufficientStockException.class, () -> item.remove(11));
        assertEquals(11, ex.getRequestedUnits());
        assertEquals(10, ex.getAvailableUnits());
        assertEquals(10, item.totalUnits());
    }

    @Test
    void canRemoveReflectsAvailability() {
        item.add(5);
        assertTrue(item.canRemove(5));
        assertFalse(item.canRemove(6));
        assertFalse(item.canRemove(0));
    }

    @Test
    void nonPositiveQuantitiesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> item.add(0));
        assertThrows(IllegalArgumentException.class, () -> item.remove(-1));
    }
}
