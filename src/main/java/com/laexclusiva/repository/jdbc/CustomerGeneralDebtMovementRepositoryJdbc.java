package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcTransactionManager;
import com.laexclusiva.repository.CustomerGeneralDebtMovementRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Adaptador JDBC del libro histórico de operaciones que afectan exclusivamente la deuda general. */
public final class CustomerGeneralDebtMovementRepositoryJdbc
        implements CustomerGeneralDebtMovementRepository {
    private final JdbcConnectionProvider connections;
    private final JdbcTransactionManager transactions;

    /** Construye el repositorio con la conexión y transacción compartidas por las operaciones financieras. */
    public CustomerGeneralDebtMovementRepositoryJdbc(JdbcConnectionProvider connections,
                                                      JdbcTransactionManager transactions) {
        if (connections == null || transactions == null) {
            throw new IllegalArgumentException("El proveedor y la transacción JDBC son obligatorios.");
        }
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public Optional<RequestRecord> findByIdempotencyKey(String key) {
        requireKey(key);
        return connections.withConnection(connection -> {
            String sql = """
                    SELECT id, customer_id, movement_type, amount, occurred_at, category, description,
                           idempotency_key, request_fingerprint
                    FROM customer_general_debt_movement WHERE idempotency_key = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, key);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    return Optional.of(new RequestRecord(map(rows), rows.getString("request_fingerprint").trim()));
                }
            }
        });
    }

    @Override
    public SaveResult saveOnce(GeneralDebtMovement movement, String fingerprint) {
        validate(movement, fingerprint);
        return transactions.inTransaction(() -> connections.withConnection(connection -> {
            String sql = """
                    INSERT INTO customer_general_debt_movement
                        (customer_id, movement_type, amount, occurred_at, category, description,
                         idempotency_key, request_fingerprint)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (idempotency_key) DO NOTHING
                    RETURNING id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, movement.customerId());
                statement.setString(2, movement.type().name());
                statement.setBigDecimal(3, movement.amount());
                statement.setObject(4, movement.occurredAt());
                statement.setString(5, movement.category());
                statement.setString(6, movement.description());
                statement.setString(7, movement.idempotencyKey());
                statement.setString(8, fingerprint);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return new SaveResult(new GeneralDebtMovement(rows.getLong("id"), movement.customerId(),
                                movement.type(), movement.amount(), movement.occurredAt(), movement.category(),
                                movement.description(), movement.idempotencyKey()), true);
                    }
                }
            }
            RequestRecord existing = findByIdempotencyKey(movement.idempotencyKey()).orElseThrow(() ->
                    new IllegalStateException("PostgreSQL no devolvió el movimiento asociado a la clave."));
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("La clave financiera ya se usó con datos diferentes.");
            }
            return new SaveResult(existing.movement(), false);
        }));
    }

    @Override
    public List<GeneralDebtMovement> find(String customerId, LocalDate from, LocalDate through, String category) {
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT id, customer_id, movement_type, amount, occurred_at, category, description,
                       idempotency_key, request_fingerprint
                FROM customer_general_debt_movement WHERE TRUE
                """);
        List<Object> parameters = new ArrayList<>();
        if (customerId != null) { sql.append(" AND customer_id = ?"); parameters.add(customerId); }
        if (from != null) { sql.append(" AND occurred_at >= ?"); parameters.add(from.atStartOfDay()); }
        if (through != null) { sql.append(" AND occurred_at < ?"); parameters.add(through.plusDays(1).atStartOfDay()); }
        if (category != null && !category.isBlank()) { sql.append(" AND category = ?"); parameters.add(category.trim()); }
        sql.append(" ORDER BY occurred_at ASC, id ASC");
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                try (ResultSet rows = statement.executeQuery()) {
                    List<GeneralDebtMovement> result = new ArrayList<>();
                    while (rows.next()) result.add(map(rows));
                    return List.copyOf(result);
                }
            }
        });
    }

    private static GeneralDebtMovement map(ResultSet rows) throws java.sql.SQLException {
        return new GeneralDebtMovement(rows.getLong("id"), rows.getString("customer_id"),
                GeneralDebtMovementType.valueOf(rows.getString("movement_type")),
                rows.getBigDecimal("amount"), rows.getObject("occurred_at", LocalDateTime.class),
                rows.getString("category"), rows.getString("description"), rows.getString("idempotency_key"));
    }

    private static void validate(GeneralDebtMovement movement, String fingerprint) {
        if (movement == null) throw new IllegalArgumentException("El movimiento de deuda general es obligatorio.");
        requireKey(movement.idempotencyKey());
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("La huella del movimiento no es válida.");
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 64) {
            throw new IllegalArgumentException("La clave financiera debe tener entre 1 y 64 caracteres.");
        }
    }
}
