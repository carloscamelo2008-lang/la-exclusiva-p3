package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.repository.StockMovementRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persiste el historial de movimientos de inventario en PostgreSQL. */
public final class StockMovementRepositoryJdbc implements StockMovementRepository {
    private static final String MOVEMENT_SELECT = """
            SELECT m.id AS movement_id, m.movement_type, m.units, m.reason, m.occurred_at, m.actor_user_id,
                   p.id, p.name, p.volume_ml, p.units_per_basket, p.container_type,
                   p.container_group, p.image_filename, pp.presentation_code, pp.price
            FROM stock_movement m
            JOIN beer_product p ON p.id = m.product_id
            LEFT JOIN product_price pp ON pp.product_id = p.id
            """;
    private static final String MOVEMENT_ORDER = " ORDER BY m.occurred_at, m.id";

    private final JdbcConnectionProvider connectionProvider;

    public StockMovementRepositoryJdbc(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    @Override
    public void save(StockMovement movement) {
        saveAndReturnId(movement);
    }

    /** Inserta un movimiento y devuelve la clave técnica asignada por PostgreSQL. */
    public long saveAndReturnId(StockMovement movement) {
        if (movement == null) {
            throw new IllegalArgumentException("El movimiento es obligatorio.");
        }
        String sql = """
                INSERT INTO stock_movement (product_id, movement_type, units, reason, occurred_at, actor_user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, movement.productId());
                statement.setString(2, movement.type().name());
                statement.setInt(3, movement.units());
                statement.setString(4, movement.reason());
                statement.setObject(6, movement.actorUserId(), java.sql.Types.BIGINT);
                statement.setObject(5, movement.timestamp().truncatedTo(ChronoUnit.MICROS));
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new JdbcPersistenceException("PostgreSQL no devolvió el ID del movimiento.");
                    }
                    return rows.getLong("id");
                }
            }
        });
    }

    @Override
    public List<StockMovement> findAll() {
        return findMovements(MOVEMENT_SELECT + MOVEMENT_ORDER, null);
    }

    @Override
    public List<StockMovement> findByProductId(String productId) {
        if (productId == null) {
            return List.of();
        }
        return findMovements(MOVEMENT_SELECT + " WHERE m.product_id = ?" + MOVEMENT_ORDER, productId);
    }

    private List<StockMovement> findMovements(String sql, String productId) {
        return connectionProvider.withConnection(connection -> {
            Map<String, BeerProduct> products = new HashMap<>();
            Map<Long, StockMovement> movements = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (productId != null) {
                    statement.setString(1, productId);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String id = rows.getString("id");
                        BeerProduct product = products.get(id);
                        if (product == null) {
                            product = ProductRepositoryJdbc.readProduct(rows);
                            products.put(id, product);
                        }
                        ProductRepositoryJdbc.readPrice(rows, product);
                        long movementId = rows.getLong("movement_id");
                        if (!movements.containsKey(movementId)) {
                            movements.put(movementId, new StockMovement(id,
                                    MovementType.valueOf(rows.getString("movement_type")),
                                    rows.getInt("units"), rows.getString("reason"),
                                    rows.getObject("occurred_at", LocalDateTime.class), movementId, product, rows.getObject("actor_user_id", Long.class)));
                        }
                    }
                }
            }
            return new ArrayList<>(movements.values());
        });
    }
}
