package com.laexclusiva.repository;

import com.laexclusiva.model.SalePayment;
import java.math.BigDecimal;

/** Puerto para registrar abonos con una identidad estable frente a reintentos. */
public interface SalePaymentAppender {

    /**
     * Anexa un abono o devuelve el ya persistido para la misma clave.
     *
     * @param saleId identificador de la venta
     * @param amount importe positivo, con un máximo de dos decimales
     * @param idempotencyKey clave estable del intento, de 1 a 64 caracteres no blancos
     * @return pago persistido; en una repetición conserva su fecha original
     * @throws IllegalArgumentException si la venta no existe, la clave ya se usó con otro importe,
     *                                  o el importe no cabe en el saldo pendiente
     */
    SalePayment appendPayment(String saleId, BigDecimal amount, String idempotencyKey);
}
