package com.laexclusiva.repository;

import com.laexclusiva.model.CashMovement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia del libro de caja y del valor facturado histórico. */
public interface CashMovementRepository {
    /** Resultado de registrar o recuperar una operación de caja idempotente. */
    record SaveResult(CashMovement movement, boolean inserted) { }
    /** Movimiento existente junto a la huella de la solicitud que lo creó. */
    record RequestRecord(CashMovement movement, String fingerprint) { }

    /**
     * Busca un movimiento mediante la clave estable de la operación.
     *
     * @param key clave idempotente persistida
     * @return movimiento y huella, si la clave ya existe
     */
    Optional<RequestRecord> findByIdempotencyKey(String key);

    /**
     * Inserta el movimiento o devuelve el registro original para un reintento equivalente.
     *
     * @param movement movimiento propuesto
     * @param fingerprint huella de sus datos de negocio
     * @return registro resultante e indicador de si se insertó en esta llamada
     */
    SaveResult saveOnce(CashMovement movement, String fingerprint);

    /**
     * Consulta movimientos con límites de fecha inclusivos y categoría opcional.
     *
     * @param from fecha inicial, o {@code null} para no limitarla
     * @param through fecha final, o {@code null} para no limitarla
     * @param category nombre persistido de categoría, o {@code null} para todas
     * @return movimientos ordenados cronológicamente
     */
    List<CashMovement> find(LocalDate from, LocalDate through, String category);

    /**
     * Calcula la facturación con los precios históricos guardados en las líneas de venta.
     *
     * @param from fecha inicial inclusiva
     * @param through fecha final inclusiva
     * @return importe facturado; no representa efectivo necesariamente recibido
     */
    BigDecimal findInvoicedSales(LocalDate from, LocalDate through);
}
