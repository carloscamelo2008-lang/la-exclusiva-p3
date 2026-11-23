package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos efímeros usados para generar un estado de cuenta comercial; no se persiste el PDF. */
public record AccountStatementData(String businessName, Customer customer, LocalDate generatedDate,
                                   List<Sale> pendingSales, BigDecimal generalDebt,
                                   BigDecimal totalBalance, LocalDate commitmentDate,
                                   String paymentKey) {
    /** Conserva snapshots defensivos de cliente y ventas. */
    public AccountStatementData {
        if (businessName == null || businessName.isBlank() || customer == null || generatedDate == null
                || pendingSales == null || generalDebt == null || generalDebt.signum() < 0
                || totalBalance == null || totalBalance.signum() < 0) {
            throw new IllegalArgumentException("El estado de cuenta requiere datos financieros válidos.");
        }
        customer = customer.copy();
        pendingSales = pendingSales.stream().map(Sale::copy).toList();
        paymentKey = paymentKey == null || paymentKey.isBlank() ? null : paymentKey.trim();
    }
}
