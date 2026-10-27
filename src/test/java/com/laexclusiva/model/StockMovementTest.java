package com.laexclusiva.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StockMovementTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);

    @Test
    void validEntryAndExitMovementsAreAccepted() {
        StockMovement entry = new StockMovement("POKER_750", MovementType.ENTRY, 16, "Stock inicial", NOW);
        StockMovement exit = new StockMovement("POKER_750", MovementType.EXIT, 8, "Venta", NOW);
        assertEquals(16, entry.units());
        assertEquals(MovementType.EXIT, exit.type());
    }

    @Test
    void nullOrBlankProductIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement(null, MovementType.ENTRY, 1, "x", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("", MovementType.ENTRY, 1, "x", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("  ", MovementType.ENTRY, 1, "x", NOW));
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("POKER_750", null, 1, "x", NOW));
    }

    @Test
    void zeroOrNegativeUnitsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("POKER_750", MovementType.ENTRY, 0, "x", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("POKER_750", MovementType.EXIT, -5, "x", NOW));
    }

    @Test
    void nullOrBlankReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("POKER_750", MovementType.ENTRY, 1, null, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("POKER_750", MovementType.ENTRY, 1, "   ", NOW));
    }

    @Test
    void nullTimestampIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StockMovement("POKER_750", MovementType.ENTRY, 1, "x", null));
    }
}
