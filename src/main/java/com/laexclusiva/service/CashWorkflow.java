package com.laexclusiva.service;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.CashSnapshot;
import com.laexclusiva.model.CashSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Casos de uso de caja, con recibos comerciales generados desde pagos y entradas manuales auditables. */
public interface CashWorkflow {
    /**
     * Registra un movimiento manual; las categorías de recibos comerciales se generan desde su flujo de origen.
     *
     * @param direction sentido del movimiento
     * @param category categoría manual compatible con el sentido
     * @param amount importe positivo en moneda local
     * @param occurredAt fecha y hora contables propuestas
     * @param description explicación auditable
     * @param idempotencyKey referencia estable que se conserva ante reintentos
     * @return movimiento registrado o recuperado por idempotencia
     * @throws IllegalArgumentException si los datos no cumplen el contrato de caja
     */
    CashMovement registerMovement(CashDirection direction, CashCategory category, BigDecimal amount,
                                  LocalDateTime occurredAt, String description, String idempotencyKey);

    /**
     * Consulta movimientos por fechas y categoría, con extremos de fecha incluidos.
     *
     * @param from fecha inicial o {@code null}
     * @param through fecha final o {@code null}
     * @param category filtro de categoría o {@code null}
     * @return movimientos visibles para caja
     */
    /** Registra el medio declarado en el formulario de caja. */
    default CashMovement registerMovement(CashDirection direction, CashCategory category, BigDecimal amount,
            LocalDateTime occurredAt, String description, String idempotencyKey,
            com.laexclusiva.model.PaymentMethod paymentMethod) {
        return registerMovement(direction, category, amount, occurredAt, description, idempotencyKey);
    }

    List<CashMovement> listMovements(LocalDate from, LocalDate through, CashCategory category);

    /**
     * Resume facturación y efectivo recibido o egresado en el mismo rango.
     *
     * @param from fecha inicial inclusiva o {@code null}
     * @param through fecha final inclusiva o {@code null}
     * @return totales separados para no confundir ventas facturadas con recaudo
     */
    CashSummary summarize(LocalDate from, LocalDate through);

    /**
     * Obtiene las filas visibles y los indicadores para una actualización de pantalla.
     *
     * <p>La implementación JDBC reemplaza este recorrido por una transacción
     * {@code REPEATABLE_READ}. El método predeterminado conserva compatibilidad con workflows
     * simulados y en memoria.</p>
     *
     * @param from fecha inicial inclusiva o {@code null}
     * @param through fecha final inclusiva o {@code null}
     * @param category filtro aplicado únicamente a la tabla o {@code null}
     * @return filas y resumen del rango solicitado
     */
    default CashSnapshot loadSnapshot(LocalDate from, LocalDate through, CashCategory category) {
        return new CashSnapshot(listMovements(from, through, category), summarize(from, through));
    }

    /** Indica si las operaciones manuales tienen claves persistidas. */
    boolean supportsDurableIdempotency();
}
