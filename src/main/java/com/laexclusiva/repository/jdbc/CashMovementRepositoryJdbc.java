package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcTransactionManager;
import com.laexclusiva.repository.CashMovementRepository;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Repositorio del libro de caja con unicidad por intento y por operación financiera de origen. */
public final class CashMovementRepositoryJdbc implements CashMovementRepository {
    private final JdbcConnectionProvider connections;
    private final JdbcTransactionManager transactions;

    /** Comparte el contexto de conexión para incluir movimientos derivados en la transacción comercial. */
    public CashMovementRepositoryJdbc(JdbcConnectionProvider connections, JdbcTransactionManager transactions) {
        if (connections == null || transactions == null) {
            throw new IllegalArgumentException("El proveedor y coordinador JDBC son obligatorios.");
        }
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public Optional<RequestRecord> findByIdempotencyKey(String key) {
        requireKey(key);
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(selectSql()
                    + " WHERE idempotency_key = ?")) {
                statement.setString(1, key);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    return Optional.of(new RequestRecord(map(rows), rows.getString("request_fingerprint").trim()));
                }
            }
        });
    }

    @Override
    public SaveResult saveOnce(CashMovement movement, String fingerprint) {
        validate(movement, fingerprint);
        return transactions.inTransaction(() -> connections.withConnection(connection -> {
            String sql = """
                    INSERT INTO cash_movement
                        (direction, category, amount, occurred_at, description, idempotency_key,
                         request_fingerprint, source_type, source_reference)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (idempotency_key) DO NOTHING
                    RETURNING id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, movement.direction().name());
                statement.setString(2, movement.category().name());
                statement.setBigDecimal(3, movement.amount());
                statement.setObject(4, movement.occurredAt());
                statement.setString(5, movement.description());
                statement.setString(6, movement.idempotencyKey());
                statement.setString(7, fingerprint);
                statement.setString(8, movement.sourceType());
                statement.setString(9, movement.sourceReference());
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) return new SaveResult(withId(movement, rows.getLong("id")), true);
                }
            }
            RequestRecord existing = findByIdempotencyKey(movement.idempotencyKey()).orElseThrow(() ->
                    new IllegalStateException("PostgreSQL no devolvió el movimiento para la clave utilizada."));
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("La clave de caja ya se usó con datos diferentes.");
            }
            return new SaveResult(existing.movement(), false);
        }));
    }

    @Override
    public List<CashMovement> find(LocalDate from, LocalDate through, String category) {
        validateRange(from, through);
        StringBuilder sql = new StringBuilder(selectSql()).append(" WHERE TRUE");
        List<Object> values = new ArrayList<>();
        if (from != null) { sql.append(" AND occurred_at >= ?"); values.add(from.atStartOfDay()); }
        if (through != null) { sql.append(" AND occurred_at < ?"); values.add(through.plusDays(1).atStartOfDay()); }
        if (category != null && !category.isBlank()) { sql.append(" AND category = ?"); values.add(category); }
        sql.append(" ORDER BY occurred_at ASC, id ASC");
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, values);
                try (ResultSet rows = statement.executeQuery()) {
                    List<CashMovement> result = new ArrayList<>();
                    while (rows.next()) result.add(map(rows));
                    return List.copyOf(result);
                }
            }
        });
    }

    @Override
    public BigDecimal findInvoicedSales(LocalDate from, LocalDate through) {
        validateRange(from, through);
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(line.quantity * line.unit_price), 0) AS total
                FROM sale sale JOIN sale_line line ON line.sale_id = sale.id WHERE TRUE
                """);
        List<Object> values = new ArrayList<>();
        if (from != null) { sql.append(" AND sale.created_at >= ?"); values.add(from.atStartOfDay()); }
        if (through != null) { sql.append(" AND sale.created_at < ?"); values.add(through.plusDays(1).atStartOfDay()); }
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, values);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new java.sql.SQLException("No se obtuvo el total facturado.");
                    return rows.getBigDecimal("total");
                }
            }
        });
    }

    private static String selectSql() {
        return "SELECT id, direction, category, amount, occurred_at, description, idempotency_key, "
                + "request_fingerprint, source_type, source_reference FROM cash_movement";
    }

    private static CashMovement map(ResultSet rows) throws java.sql.SQLException {
        return new CashMovement(rows.getLong("id"), CashDirection.valueOf(rows.getString("direction")),
                CashCategory.valueOf(rows.getString("category")), rows.getBigDecimal("amount"),
                rows.getObject("occurred_at", LocalDateTime.class), rows.getString("description"),
                rows.getString("idempotency_key"), rows.getString("source_type"),
                rows.getString("source_reference"));
    }

    private static CashMovement withId(CashMovement value, long id) {
        return new CashMovement(id, value.direction(), value.category(), value.amount(), value.occurredAt(),
                value.description(), value.idempotencyKey(), value.sourceType(), value.sourceReference());
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws java.sql.SQLException {
        for (int index = 0; index < values.size(); index++) statement.setObject(index + 1, values.get(index));
    }

    private static void validate(CashMovement movement, String fingerprint) {
        if (movement == null || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("El movimiento y su huella válida son obligatorios.");
        }
        requireKey(movement.idempotencyKey());
        if (movement.idempotencyKey().length() > 255 || movement.sourceReference().length() > 255) {
            throw new IllegalArgumentException("La referencia de caja supera el límite de almacenamiento.");
        }
        if (!movement.sourceType().matches("MANUAL|SALE_INITIAL|SALE_INSTALLMENT|GENERAL_DEBT_PAYMENT")) {
            throw new IllegalArgumentException("El origen del movimiento de caja no es válido.");
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("La clave de caja es obligatoria.");
    }

    private static void validateRange(LocalDate from, LocalDate through) {
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        }
    }
}
