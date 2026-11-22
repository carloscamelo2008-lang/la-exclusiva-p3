package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.GoodsReceipt;
import com.laexclusiva.model.GoodsReceiptLine;
import com.laexclusiva.model.PurchaseOrderLine;
import com.laexclusiva.model.PurchaseOrderStatus;
import com.laexclusiva.repository.GoodsReceiptRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.laexclusiva.persistence.jdbc.PurchaseOrderRepositoryJdbc.lockOrder;
import static com.laexclusiva.persistence.jdbc.PurchaseOrderRepositoryJdbc.requireProvider;
import static com.laexclusiva.persistence.jdbc.PurchaseOrderRepositoryJdbc.safe;

/** Persiste recepciones y sus detalles en PostgreSQL usando el contexto JDBC compartido. */
public final class GoodsReceiptRepositoryJdbc implements GoodsReceiptRepository {
    private static final String SELECT_RECEIPTS = """
            SELECT r.*, d.id AS detail_id, d.purchase_order_line_id, d.product_id AS received_product_id,
                   d.received_baskets, d.received_loose_units, d.received_equivalent_units, d.stock_movement_id,
                   l.purchase_order_id AS line_order_id, l.product_id, l.ordered_baskets, l.ordered_loose_units,
                   l.units_per_basket_snapshot, l.ordered_equivalent_units,
                   (SELECT COALESCE(SUM(previous.received_equivalent_units), 0)
                    FROM goods_receipt_line previous
                    JOIN goods_receipt prior_receipt ON prior_receipt.id = previous.goods_receipt_id
                    WHERE previous.purchase_order_line_id = d.purchase_order_line_id
                      AND prior_receipt.purchase_order_id = r.purchase_order_id
                      AND prior_receipt.id < r.id) AS previously_received
            FROM goods_receipt r
            LEFT JOIN goods_receipt_line d ON d.goods_receipt_id = r.id
            LEFT JOIN purchase_order_line l ON l.id = d.purchase_order_line_id
            """;

    private final JdbcConnectionProvider connections;
    private final JdbcTransactionManager transactions;

    /** @param connections proveedor JDBC compartido con pedidos e inventario */
    public GoodsReceiptRepositoryJdbc(JdbcConnectionProvider connections) {
        this.connections = requireProvider(connections);
        this.transactions = new JdbcTransactionManager(connections);
    }

    /**
     * {@inheritDoc}
     * Para coordinar la recepción completa, invocar antes de actualizar los acumulados del
     * pedido y dentro de la transacción exterior que también actualiza pedidos e inventario.
     * Una clave repetida produce un error de dominio y revierte la unidad de trabajo.
     */
    @Override
    public GoodsReceipt save(GoodsReceipt receipt) {
        if (receipt == null || receipt.id() != null || receipt.lines().stream().anyMatch(line -> line.id() != null)) {
            throw new IllegalArgumentException("Se requiere una recepción nueva con detalles sin ID.");
        }
        return safe(() -> transactions.inTransaction(() -> connections.withConnection(connection -> {
            var order = lockOrder(connection, receipt.purchaseOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("El pedido indicado no existe."));
            long id = insertHeader(connection, receipt);
            if (order.order().status() == PurchaseOrderStatus.CANCELLED
                    || order.order().status() == PurchaseOrderStatus.COMPLETED) {
                throw new IllegalArgumentException("El pedido está cerrado a nuevas recepciones.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO goods_receipt_line (goods_receipt_id, purchase_order_line_id, product_id,
                        received_baskets, received_loose_units, received_equivalent_units, stock_movement_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (GoodsReceiptLine detail : receipt.lines()) {
                    if (!order.lines().contains(detail.orderLineBeforeReceipt())) {
                        throw new ConcurrentModificationException("El saldo de la línea de recepción cambió.");
                    }
                    statement.setLong(1, id);
                    statement.setLong(2, detail.purchaseOrderLineId());
                    statement.setString(3, detail.productId());
                    statement.setInt(4, detail.receivedBaskets());
                    statement.setInt(5, detail.receivedLooseUnits());
                    statement.setInt(6, detail.receivedEquivalentUnits());
                    statement.setObject(7, detail.stockMovementId(), Types.BIGINT);
                    if (statement.executeUpdate() != 1) {
                        throw new JdbcPersistenceException("No se pudo guardar el detalle de recepción.");
                    }
                }
            }
            GoodsReceipt saved = findById(id)
                    .orElseThrow(() -> new JdbcPersistenceException("No se pudo recuperar la recepción guardada."));
            Map<Long, PurchaseOrderLine> expected = new LinkedHashMap<>();
            for (GoodsReceiptLine line : receipt.lines()) {
                expected.put(line.purchaseOrderLineId(), line.orderLineBeforeReceipt());
            }
            for (GoodsReceiptLine line : saved.lines()) {
                if (!line.orderLineBeforeReceipt().equals(expected.get(line.purchaseOrderLineId()))) {
                    throw new ConcurrentModificationException("El historial de recepción no coincide con el saldo del pedido.");
                }
            }
            return saved;
        })));
    }

    private static long insertHeader(Connection connection, GoodsReceipt receipt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO goods_receipt (purchase_order_id, idempotency_key, received_by_user_id,
                                           received_by_username, notes, received_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (purchase_order_id, idempotency_key) DO NOTHING RETURNING id
                """)) {
            statement.setLong(1, receipt.purchaseOrderId());
            statement.setString(2, receipt.idempotencyKey());
            statement.setObject(3, receipt.receivedByUserId(), Types.BIGINT);
            statement.setString(4, receipt.receivedByUsername());
            statement.setString(5, receipt.notes());
            statement.setObject(6, receipt.receivedAt());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("La clave de idempotencia ya está registrada para el pedido.");
                }
                return rows.getLong("id");
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<GoodsReceipt> findById(long id) {
        return query("WHERE r.id = ?", id, null).stream().findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public List<GoodsReceipt> findByPurchaseOrderId(long purchaseOrderId) {
        return query("WHERE r.purchase_order_id = ?", purchaseOrderId, null);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<GoodsReceipt> findByOrderAndIdempotencyKey(long purchaseOrderId, String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        return query("WHERE r.purchase_order_id = ? AND r.idempotency_key = ?", purchaseOrderId, idempotencyKey)
                .stream().findFirst();
    }

    private List<GoodsReceipt> query(String filter, long id, String key) {
        return safe(() -> connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT_RECEIPTS + filter + " ORDER BY r.id, d.id")) {
                statement.setLong(1, id);
                if (key != null) statement.setString(2, key);
                try (ResultSet rows = statement.executeQuery()) {
                    Map<Long, ReceiptRow> receipts = new LinkedHashMap<>();
                    while (rows.next()) {
                        long receiptId = rows.getLong("id");
                        ReceiptRow receipt = receipts.get(receiptId);
                        if (receipt == null) {
                            receipt = new ReceiptRow(receiptId, rows.getLong("purchase_order_id"),
                                    rows.getString("idempotency_key"), rows.getObject("received_by_user_id", Long.class),
                                    rows.getString("received_by_username"), rows.getString("notes"),
                                    rows.getObject("received_at", LocalDateTime.class), new ArrayList<>());
                            receipts.put(receiptId, receipt);
                        }
                        Long detailId = rows.getObject("detail_id", Long.class);
                        if (detailId != null) receipt.details().add(readDetail(rows, receiptId, detailId));
                    }
                    return receipts.values().stream().map(ReceiptRow::toReceipt).toList();
                }
            }
        }));
    }

    private static GoodsReceiptLine readDetail(ResultSet rows, long receiptId, long detailId) throws SQLException {
        String productId = rows.getString("product_id");
        if (productId == null || !productId.equals(rows.getString("received_product_id"))) {
            throw new JdbcPersistenceException("El producto del detalle difiere del producto pedido.");
        }
        long previouslyReceived = rows.getLong("previously_received");
        if (previouslyReceived < 0 || previouslyReceived > rows.getInt("ordered_equivalent_units")) {
            throw new JdbcPersistenceException("El historial de recepción excede las unidades pedidas.");
        }
        PurchaseOrderLine before = new PurchaseOrderLine(rows.getLong("purchase_order_line_id"),
                rows.getLong("line_order_id"), productId, rows.getInt("ordered_baskets"),
                rows.getInt("ordered_loose_units"), rows.getInt("units_per_basket_snapshot"),
                rows.getInt("ordered_equivalent_units"), (int) previouslyReceived);
        return new GoodsReceiptLine(detailId, receiptId, before, rows.getInt("received_baskets"),
                rows.getInt("received_loose_units"), rows.getInt("received_equivalent_units"),
                rows.getObject("stock_movement_id", Long.class));
    }

    private record ReceiptRow(long id, long orderId, String key, Long userId, String username, String notes,
                              LocalDateTime timestamp, List<GoodsReceiptLine> details) {
        GoodsReceipt toReceipt() {
            return new GoodsReceipt(id, orderId, key, userId, username, notes, timestamp, details);
        }
    }
}
