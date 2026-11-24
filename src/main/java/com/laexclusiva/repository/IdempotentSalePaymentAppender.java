package com.laexclusiva.repository;

import com.laexclusiva.model.SalePayment;
import java.math.BigDecimal;

/** Extiende el puerto de abonos para distinguir una inserción nueva de un reintento idempotente. */
public interface IdempotentSalePaymentAppender extends SalePaymentAppender {

    /**
     * Registra el pago o devuelve el existente para esa clave, indicando si se insertó ahora.
     *
     * @param saleId identificador de la venta
     * @param amount importe del abono
     * @param idempotencyKey clave estable del intento
     * @return pago y marca que permite evitar volver a reducir el saldo agregado en un reintento
     * @implNote El adaptador JDBC bloquea primero el cliente asociado y después la venta dentro de
     *           la transacción compartida, manteniendo el mismo orden que las ventas a crédito.
     */
    AppendResult appendPaymentOnce(String saleId, BigDecimal amount, String idempotencyKey);

    /** Anexa un abono conservando la identidad del receptor cuando el adaptador lo admite. */
    default AppendResult appendPaymentOnce(String saleId, BigDecimal amount, String idempotencyKey, Long receiverId) {
        return appendPaymentOnce(saleId, amount, idempotencyKey);
    }

    /** {@inheritDoc} Conserva el contrato anterior devolviendo el valor del intento idempotente. */
    @Override
    default SalePayment appendPayment(String saleId, BigDecimal amount, String idempotencyKey) {
        return appendPaymentOnce(saleId, amount, idempotencyKey).payment();
    }

    /** Resultado del intento de anexar un abono identificado. */
    record AppendResult(SalePayment payment, boolean inserted) {
        public AppendResult {
            if (payment == null) {
                throw new IllegalArgumentException("El pago resultante es obligatorio.");
            }
        }
    }
}
