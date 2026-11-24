package com.laexclusiva.repository;

import com.laexclusiva.model.AdministrativeAuditEvent;
import java.time.LocalDate;
import java.util.List;

/** Puerto de escritura y lectura del registro administrativo protegido. */
public interface AdministrativeAuditRepository {
    /** Registra solo metadatos permitidos; la llamada comparte transacción con la mutación si existe. */
    void append(AdministrativeAuditEvent event);

    /** Consulta eventos dentro del rango inclusivo, más recientes primero. */
    List<AdministrativeAuditEvent> find(LocalDate from, LocalDate through, int limit);
}
