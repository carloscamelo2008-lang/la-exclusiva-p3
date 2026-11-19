package com.laexclusiva.model;

import java.math.BigDecimal;

/** Resumen que separa facturación de cobros reales registrados en caja. */
public record CashSummary(BigDecimal invoicedSales, BigDecimal saleReceipts,
                          BigDecimal generalDebtReceipts, BigDecimal otherIncome,
                          BigDecimal expenses) {
    /** Verifica que el resumen no tenga componentes nulos. */
    public CashSummary {
        if (invoicedSales == null || saleReceipts == null || generalDebtReceipts == null
                || otherIncome == null || expenses == null) {
            throw new IllegalArgumentException("Los importes del resumen de caja son obligatorios.");
        }
    }

    /** Efectivo ingresado; excluye el valor vendido que aún no se ha cobrado. */
    public BigDecimal cashIn() { return saleReceipts.add(generalDebtReceipts).add(otherIncome); }
    /** Efectivo neto después de egresos. */
    public BigDecimal netCash() { return cashIn().subtract(expenses); }
}
