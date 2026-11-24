package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.AdministrativeAuditEvent;
import com.laexclusiva.repository.AdministrativeAuditRepository;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Adaptador JDBC del registro mínimo de seguridad; nunca persiste excepciones ni datos de credenciales. */
public final class AdministrativeAuditRepositoryJdbc implements AdministrativeAuditRepository {
    private final JdbcConnectionProvider connections;

    /** @param connections proveedor JDBC compartido con la transacción administrativa */
    public AdministrativeAuditRepositoryJdbc(JdbcConnectionProvider connections) {
        if (connections == null) throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        this.connections = connections;
    }

    @Override public void append(AdministrativeAuditEvent event) {
        if (event == null) throw new IllegalArgumentException("El evento es obligatorio.");
        connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO administrative_audit_event
                      (actor_user_id,action,target_type,target_reference,occurred_at,result_code)
                    VALUES (?,?,?,?,?,?)
                    """)) {
                statement.setLong(1, event.actorUserId());
                statement.setString(2, event.action().name());
                statement.setString(3, event.targetType().name());
                statement.setString(4, event.targetReference());
                statement.setObject(5, event.occurredAt());
                statement.setString(6, event.result().name());
                if (statement.executeUpdate() != 1) throw new java.sql.SQLException("No se registró el evento administrativo.");
            }
            return null;
        });
    }

    @Override public List<AdministrativeAuditEvent> find(LocalDate from, LocalDate through, int limit) {
        if (limit < 1 || limit > 1000 || (from != null && through != null && from.isAfter(through))) {
            throw new IllegalArgumentException("El rango o límite de auditoría no es válido.");
        }
        return connections.withConnection(connection -> {
            StringBuilder sql = new StringBuilder("""
                    SELECT id,actor_user_id,action,target_type,target_reference,occurred_at,result_code
                    FROM administrative_audit_event WHERE 1=1
                    """);
            if (from != null) sql.append(" AND occurred_at >= ?");
            if (through != null) sql.append(" AND occurred_at < ?");
            sql.append(" ORDER BY occurred_at DESC,id DESC LIMIT ?");
            List<AdministrativeAuditEvent> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int index = 1;
                if (from != null) statement.setObject(index++, from.atStartOfDay());
                if (through != null) statement.setObject(index++, through.plusDays(1).atStartOfDay());
                statement.setInt(index, limit);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new AdministrativeAuditEvent(rows.getLong("id"),
                            rows.getLong("actor_user_id"), AdministrativeAuditEvent.Action.valueOf(rows.getString("action")),
                            AdministrativeAuditEvent.TargetType.valueOf(rows.getString("target_type")),
                            rows.getString("target_reference"), rows.getObject("occurred_at", LocalDateTime.class),
                            AdministrativeAuditEvent.Result.valueOf(rows.getString("result_code"))));
                }
            }
            return result;
        });
    }
}
