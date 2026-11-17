package com.laexclusiva.repository;

import java.math.BigDecimal;

/** Consulta de solo lectura para conocer la deuda pendiente que sí pertenece a ventas. */
public interface CustomerDebtQuery {

    /**
     * Calcula la deuda pendiente de las ventas asociadas a un cliente, sin incluir deuda general.
     *
     * @param customerId identificador no vacío del cliente
     * @return suma de total histórico menos pagos por venta, o cero si no tiene ventas pendientes
     * @throws IllegalArgumentException si el identificador es nulo o está en blanco
     */
    BigDecimal findOutstandingSalesDebt(String customerId);
}
