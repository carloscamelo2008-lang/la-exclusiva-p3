package com.laexclusiva.service;

import com.laexclusiva.model.Sale;
import com.laexclusiva.model.StockRequest;
import java.math.BigDecimal;
import java.util.List;

/** Casos de uso de venta compartidos por la interfaz JavaFX en memoria y con PostgreSQL. */
public interface SalesWorkflow {

    /**
     * Registra una venta con sus presentaciones y pago inicial.
     *
     * @param lines líneas válidas de producto y presentación
     * @param sellerId identificador de un vendedor registrado
     * @param customerId cliente, requerido cuando queda saldo pendiente
     * @param initialPayment pago recibido al registrar la venta
     * @return venta persistida o almacenada en memoria
     * @throws IllegalArgumentException si la entrada incumple las reglas del dominio
     */
    Sale registerSale(List<StockRequest> lines, String sellerId, String customerId, BigDecimal initialPayment);

    /**
     * Registra un abono identificado para que los reintentos no lo apliquen dos veces.
     * En el adaptador JDBC, el pago y el ajuste del saldo del cliente son una sola transacción.
     *
     * @param saleId identificador de la venta pendiente
     * @param amount importe positivo del abono
     * @param idempotencyKey clave estable del intento
     * @return estado actualizado de la venta
     * @throws IllegalArgumentException si la venta no existe, la clave se reutiliza con otro importe
     *                                  o el abono supera el saldo
     */
    Sale registerPayment(String saleId, BigDecimal amount, String idempotencyKey);

    /**
     * Busca una venta por su identificador.
     *
     * @param saleId identificador de la venta
     * @return venta encontrada
     * @throws IllegalArgumentException si no existe una venta con ese ID
     */
    Sale findById(String saleId);

    /** @return lista de ventas visible al adaptador configurado */
    List<Sale> listAll();

    /**
     * Calcula la deuda pendiente de ventas sin incluir deuda general del cliente.
     *
     * @param customerId identificador del cliente
     * @return suma no negativa de saldos pendientes de sus ventas
     */
    BigDecimal outstandingSalesDebt(String customerId);

    /** @return true cuando la clave de idempotencia se almacena de forma durable */
    boolean supportsDurableIdempotency();
}
