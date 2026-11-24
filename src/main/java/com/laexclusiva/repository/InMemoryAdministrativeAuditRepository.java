package com.laexclusiva.repository;

import com.laexclusiva.model.AdministrativeAuditEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Registro de auditoría no persistente para modo memoria. */
public final class InMemoryAdministrativeAuditRepository implements AdministrativeAuditRepository {
    private final List<AdministrativeAuditEvent> events = new ArrayList<>();
    private long nextId = 1;

    @Override public synchronized void append(AdministrativeAuditEvent event) {
        events.add(new AdministrativeAuditEvent(nextId++, event.actorUserId(), event.action(), event.targetType(),
                event.targetReference(), event.occurredAt(), event.result()));
    }

    @Override public synchronized List<AdministrativeAuditEvent> find(LocalDate from, LocalDate through, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("El límite debe estar entre 1 y 1000.");
        return events.stream()
                .filter(event -> from == null || !event.occurredAt().toLocalDate().isBefore(from))
                .filter(event -> through == null || !event.occurredAt().toLocalDate().isAfter(through))
                .sorted(Comparator.comparing(AdministrativeAuditEvent::occurredAt).reversed())
                .limit(limit).toList();
    }
}
