package com.laexclusiva.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseOrderModelTest {
    @ParameterizedTest
    @ValueSource(ints = {38, 30, 16})
    void usesHistoricalBasketCapacity(int capacity) {
        int total = 2 * capacity + 3;
        var line = new PurchaseOrderLine(1L, 2L, "P", 2, 3, capacity, total, capacity);
        var receipt = new GoodsReceiptLine(null, null, line, 1, 3, capacity + 3, null);
        assertEquals(capacity + 3, line.pendingEquivalentUnits());
        assertEquals(capacity + 3, receipt.receivedEquivalentUnits());
    }

    @Test
    void rejectsNegativeQuantitiesInOrdersAndReceipts() {
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrderLine(1L, 2L, "P", -1, 50, 38, 12, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrderLine(1L, 2L, "P", 1, 0, 38, 38, -1));
        var line = new PurchaseOrderLine(1L, 2L, "P", 1, 0, 38, 38, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new GoodsReceiptLine(null, null, line, 1, -1, 37, null));
    }

    @Test
    void rejectsOverReceiptAndOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrderLine(1L, 2L, "P", 1, 0, 38, 38, 39));
        var line = new PurchaseOrderLine(1L, 2L, "P", 1, 0, 38, 38, 30);
        assertThrows(IllegalArgumentException.class,
                () -> new GoodsReceiptLine(null, null, line, 0, 9, 9, null));
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseOrderLine.equivalentUnits(Integer.MAX_VALUE, 0, 38));
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseOrderLine.equivalentUnits(1, Integer.MAX_VALUE, 38));
    }
}
