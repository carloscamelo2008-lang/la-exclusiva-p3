package com.laexclusiva.repository;

import com.laexclusiva.model.GeneralDebtMovement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Puerto JDBC para el libro histórico de operaciones de deuda general. */
public interface CustomerGeneralDebtMovementRepository {
    /** Operación previamente registrada y su huella para validar reintentos. */
    record RequestRecord(GeneralDebtMovement movement, String fingerprint) { }
    /** Resultado de una inserción idempotente en el libro histórico. */
    record SaveResult(GeneralDebtMovement movement, boolean inserted) { }

    /**
     * Busca la operación que reservó la clave idempotente.
     *
     * @param key clave estable del intento
     * @return movimiento y huella cuando ya se registró
     */
    Optional<RequestRecord> findByIdempotencyKey(String key);

    /**
     * Inserta un movimiento histórico una sola vez.
     *
     * @param movement aumento o pago de deuda general
     * @param fingerprint huella de los datos de negocio
     * @return movimiento guardado e indicador de inserción nueva
     */
    SaveResult saveOnce(GeneralDebtMovement movement, String fingerprint);

    /**
     * Consulta movimientos por cliente, rango inclusivo de fechas y categoría opcional.
     *
     * @param customerId cliente, o {@code null} para todos
     * @param from fecha inicial inclusiva
     * @param through fecha final inclusiva
     * @param category categoría, o {@code null} para todas
     * @return movimientos históricos en orden cronológico
     */
    List<GeneralDebtMovement> find(String customerId, LocalDate from, LocalDate through, String category);
}
