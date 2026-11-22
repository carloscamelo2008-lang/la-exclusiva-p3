package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.PurchaseOrder;
import com.laexclusiva.model.PurchaseOrderLine;
import com.laexclusiva.model.PurchaseOrderStatus;
import com.laexclusiva.repository.PurchaseOrderRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Persiste pedidos y líneas en PostgreSQL usando el contexto JDBC compartido. */
public final class PurchaseOrderRepositoryJdbc implements PurchaseOrderRepository {
    private final JdbcConnectionProvider connections;
    private final JdbcTransactionManager transactions;

    /** @param connections proveedor JDBC compartido con el caso de uso */
    public PurchaseOrderRepositoryJdbc(JdbcConnectionProvider connections) {
        this.connections = requireProvider(connections);
        this.transactions = new JdbcTransactionManager(connections);
    }

    /** {@inheritDoc} */
    @Override
    public OrderWithLines save(PurchaseOrder order, List<NewLine> lines) {
        if (order == null || order.id() != null || order.status() != PurchaseOrderStatus.PENDING
                || lines == null || lines.isEmpty() || lines.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Se requiere un pedido pendiente nuevo con al menos una línea válida.");
        }
        List<NewLine> requested = List.copyOf(lines);
        return safe(() -> transactions.inTransaction(() -> connections.withConnection(connection -> {
            PurchaseOrder saved;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_order (supplier_id, status, notes, created_by_user_id,
                                                created_by_username, created_at)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING *
                    """)) {
                statement.setLong(1, order.supplierId());
                statement.setString(2, order.status().name());
                statement.setString(3, order.notes());
                statement.setObject(4, order.createdByUserId(), java.sql.Types.BIGINT);
                statement.setString(5, order.createdByUsername());
                statement.setObject(6, order.createdAt());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new JdbcPersistenceException("No se pudo guardar el pedido.");
                    saved = readOrder(rows);
                }
            }
            List<PurchaseOrderLine> savedLines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_order_line (purchase_order_id, product_id, ordered_baskets,
                        ordered_loose_units, units_per_basket_snapshot, ordered_equivalent_units)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING *
                    """)) {
                for (NewLine line : requested) {
                    statement.setLong(1, saved.id());
                    statement.setString(2, line.productId());
                    statement.setInt(3, line.orderedBaskets());
                    statement.setInt(4, line.orderedLooseUnits());
                    statement.setInt(5, line.unitsPerBasketSnapshot());
                    statement.setInt(6, line.orderedEquivalentUnits());
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw new JdbcPersistenceException("No se pudo guardar una línea del pedido.");
                        savedLines.add(readLine(rows, "id"));
                    }
                }
            }
            return new OrderWithLines(saved, savedLines);
        })));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OrderWithLines> findById(long id) {
        return safe(() -> connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT o.*, l.id AS line_id, l.purchase_order_id, l.product_id, l.ordered_baskets,
                           l.ordered_loose_units, l.units_per_basket_snapshot, l.ordered_equivalent_units,
                           l.received_equivalent_units
                    FROM purchase_order o LEFT JOIN purchase_order_line l ON l.purchase_order_id = o.id
                    WHERE o.id = ? ORDER BY l.id
                    """)) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    PurchaseOrder order = readOrder(rows);
                    List<PurchaseOrderLine> lines = new ArrayList<>();
                    do {
                        if (rows.getObject("line_id") != null) lines.add(readLine(rows, "line_id"));
                    } while (rows.next());
                    return Optional.of(new OrderWithLines(order, lines));
                }
            }
        }));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OrderWithLines> findByIdForUpdate(long id) {
        requireTransaction();
        return safe(() -> connections.withConnection(connection -> lockOrder(connection, id)));
    }

    /** {@inheritDoc} */
    @Override
    public List<PurchaseOrder> findAll() {
        return list("SELECT * FROM purchase_order ORDER BY id", null);
    }

    /** {@inheritDoc} */
    @Override
    public List<PurchaseOrder> findByStatus(PurchaseOrderStatus status) {
        if (status == null) throw new IllegalArgumentException("El estado del pedido es obligatorio.");
        return list("SELECT * FROM purchase_order WHERE status = ? ORDER BY id", status);
    }

    private List<PurchaseOrder> list(String sql, PurchaseOrderStatus status) {
        return safe(() -> connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (status != null) statement.setString(1, status.name());
                try (ResultSet rows = statement.executeQuery()) {
                    List<PurchaseOrder> orders = new ArrayList<>();
                    while (rows.next()) orders.add(readOrder(rows));
                    return List.copyOf(orders);
                }
            }
        }));
    }

    /** {@inheritDoc} */
    @Override
    public OrderWithLines updateStatusAndReceivedUnits(OrderWithLines expected,
            PurchaseOrderStatus status, Map<Long, Integer> receivedDeltas) {
        if (expected == null || expected.order().id() == null) {
            throw new IllegalArgumentException("Se requiere una instantánea de un pedido persistido.");
        }
        return safe(() -> transactions.inTransaction(() -> connections.withConnection(connection -> {
            OrderWithLines current = lockOrder(connection, expected.order().id()).orElse(null);
            if (!expected.equals(current)) {
                throw new ConcurrentModificationException("El pedido cambió; vuelva a consultarlo.");
            }
            OrderWithLines result = current.applyReceipt(status, receivedDeltas);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE purchase_order_line SET received_equivalent_units = ?
                    WHERE id = ? AND purchase_order_id = ?
                    """)) {
                for (PurchaseOrderLine line : result.lines()) {
                    if (!receivedDeltas.containsKey(line.id())) continue;
                    statement.setInt(1, line.receivedEquivalentUnits());
                    statement.setLong(2, line.id());
                    statement.setLong(3, line.purchaseOrderId());
                    requireOne(statement);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE purchase_order SET status = ? WHERE id = ?")) {
                statement.setString(1, status.name());
                statement.setLong(2, expected.order().id());
                requireOne(statement);
            }
            return result;
        })));
    }

    private void requireTransaction() {
        if (!connections.connectionContext().hasActiveTransaction()) {
            throw new IllegalStateException("La consulta con bloqueo requiere una transacción JDBC activa.");
        }
    }

    /** Bloquea primero la cabecera y luego sus líneas en orden de ID. */
    static Optional<OrderWithLines> lockOrder(Connection connection, long id) throws SQLException {
        PurchaseOrder order;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM purchase_order WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                order = readOrder(rows);
            }
        }
        List<PurchaseOrderLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM purchase_order_line WHERE purchase_order_id = ? ORDER BY id FOR UPDATE")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) lines.add(readLine(rows, "id"));
            }
        }
        return Optional.of(new OrderWithLines(order, lines));
    }

    private static PurchaseOrder readOrder(ResultSet rows) throws SQLException {
        return new PurchaseOrder(rows.getLong("id"), rows.getLong("supplier_id"),
                PurchaseOrderStatus.valueOf(rows.getString("status")), rows.getString("notes"),
                rows.getObject("created_by_user_id", Long.class), rows.getString("created_by_username"),
                rows.getObject("created_at", LocalDateTime.class));
    }

    private static PurchaseOrderLine readLine(ResultSet rows, String idColumn) throws SQLException {
        return new PurchaseOrderLine(rows.getLong(idColumn), rows.getLong("purchase_order_id"),
                rows.getString("product_id"), rows.getInt("ordered_baskets"), rows.getInt("ordered_loose_units"),
                rows.getInt("units_per_basket_snapshot"), rows.getInt("ordered_equivalent_units"),
                rows.getInt("received_equivalent_units"));
    }

    private static void requireOne(PreparedStatement statement) throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw new JdbcPersistenceException("No se pudo actualizar el pedido completo.");
        }
    }

    /** Valida el proveedor compartido de los repositorios de compras. */
    static JdbcConnectionProvider requireProvider(JdbcConnectionProvider connections) {
        if (connections == null) throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        return connections;
    }

    /** Evita propagar detalles del controlador JDBC, incluso si se adjuntan al cerrar recursos. */
    static <T> T safe(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (JdbcPersistenceException failure) {
            throw new JdbcPersistenceException("No se pudo completar la operación JDBC de compras.");
        } catch (RuntimeException failure) {
            if (failure.getSuppressed().length != 0) {
                throw new JdbcPersistenceException("No se pudo completar la operación JDBC de compras.");
            }
            throw failure;
        }
    }
}
