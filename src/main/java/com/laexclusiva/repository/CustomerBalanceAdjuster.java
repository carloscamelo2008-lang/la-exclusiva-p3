package com.laexclusiva.repository;

import java.math.BigDecimal;

/** Puerto para ajustar el saldo agregado de un cliente sin reemplazar snapshots financieros. */
public interface CustomerBalanceAdjuster {

    /**
     * Incrementa atómicamente el saldo agregado del cliente.
     *
     * @param customerId identificador del cliente
     * @param amount incremento positivo con máximo dos decimales
     * @return saldo resultante
     * @throws IllegalArgumentException si el identificador o importe son inválidos o no existe el cliente
     */
    BigDecimal increaseBalance(String customerId, BigDecimal amount);

    /**
     * Aplica atómicamente un abono y evita que el saldo resulte negativo.
     *
     * @param customerId identificador del cliente
     * @param amount abono positivo con máximo dos decimales
     * @return saldo resultante
     * @throws IllegalArgumentException si el identificador o importe son inválidos, no existe el
     *                                  cliente o el abono supera el saldo disponible
     */
    BigDecimal decreaseBalance(String customerId, BigDecimal amount);
}
