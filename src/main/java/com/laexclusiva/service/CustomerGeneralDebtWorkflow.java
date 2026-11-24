package com.laexclusiva.service;

import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Casos de uso para deuda general independiente de la deuda pendiente de cada venta. */
public interface CustomerGeneralDebtWorkflow {
    /**
     * Consulta el saldo agregado y su desglose; JDBC toma el bloqueo del cliente para serializar cambios.
     *
     * @param customerId identificador del cliente
     * @return saldo agregado, deuda pendiente de ventas y diferencia general
     * @throws IllegalArgumentException si el cliente no existe o el identificador es inválido
     */
    CustomerDebtBreakdown findBalance(String customerId);

    /**
     * Registra deuda que no corresponde a una venta.
     *
     * @param customerId cliente responsable
     * @param amount importe positivo
     * @param category clasificación auditable
     * @param description detalle del cargo
     * @param idempotencyKey clave estable del intento
     * @return movimiento de deuda original o recién creado
     * @throws IllegalArgumentException si el importe, cliente o clave son inválidos
     */
    GeneralDebtMovement registerDebt(String customerId, BigDecimal amount, String category,
                                     String description, String idempotencyKey);

    /**
     * Aplica un pago solo a la deuda general disponible; nunca modifica pagos de ventas.
     * En JDBC, movimiento, delta del saldo y recibo de caja comparten una transacción.
     *
     * @param customerId cliente que realiza el pago
     * @param amount importe positivo no superior a la deuda general actual
     * @param category clasificación auditable
     * @param description detalle del recaudo
     * @param idempotencyKey clave durable del intento en modo JDBC
     * @return pago original o recién creado
     * @throws IllegalArgumentException si excede el saldo general o reutiliza una clave con otra carga útil
     */
    default GeneralDebtMovement registerPayment(String customerId, BigDecimal amount, String category,
                                                String description, String idempotencyKey) {
        return registerPayment(customerId, amount, category, description, idempotencyKey, PaymentMethod.OTRO);
    }

    GeneralDebtMovement registerPayment(String customerId, BigDecimal amount, String category,
                                        String description, String idempotencyKey, PaymentMethod paymentMethod);

    /**
     * Consulta el historial por cliente, rango de fechas y categoría; los filtros pueden omitirse.
     *
     * @param customerId cliente o {@code null} para todos
     * @param from fecha inicial inclusiva o {@code null}
     * @param through fecha final inclusiva o {@code null}
     * @param category categoría exacta o {@code null}
     * @return movimientos auditables en orden cronológico
     */
    List<GeneralDebtMovement> listMovements(String customerId, LocalDate from, LocalDate through, String category);

    /** @return true cuando las claves de deuda general se guardan de forma durable */
    boolean supportsDurableIdempotency();
}
