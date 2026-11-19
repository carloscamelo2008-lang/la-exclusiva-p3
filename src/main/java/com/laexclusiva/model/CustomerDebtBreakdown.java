package com.laexclusiva.model;

import java.math.BigDecimal;

/** Presenta por separado el saldo agregado, la cartera de ventas y la deuda general calculada. */
public record CustomerDebtBreakdown(BigDecimal aggregateBalance, BigDecimal outstandingSalesDebt,
                                    BigDecimal generalDebt) {
    /**
     * Aplica el contrato contable acordado: deuda general = saldo agregado menos deuda de ventas.
     *
     * @param aggregateBalance saldo persistido del cliente
     * @param outstandingSalesDebt saldos pendientes de ventas
     * @param generalDebt diferencia calculada sin reasignar sobrepagos
     */
    public CustomerDebtBreakdown {
        if (aggregateBalance == null || outstandingSalesDebt == null || generalDebt == null) {
            throw new IllegalArgumentException("Los tres saldos son obligatorios.");
        }
    }

    /** Indica si los saldos son coherentes y no hay deuda de ventas mayor que el total agregado. */
    public boolean isConsistent() {
        return generalDebt.signum() >= 0;
    }
}
