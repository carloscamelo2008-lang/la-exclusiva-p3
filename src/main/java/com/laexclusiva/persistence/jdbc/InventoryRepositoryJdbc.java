package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.InventoryItem;
import com.laexclusiva.repository.InventoryRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consulta existencias e inserta filas iniciales dentro de una transacción activa.
 * Los cambios posteriores se aplican mediante ajustes atómicos por delta para impedir
 * que un snapshot antiguo sobrescriba existencias más recientes.
 */
public final class InventoryRepositoryJdbc implements InventoryRepository {
    private static final String INVENTORY_SELECT = """
            SELECT p.id, p.name, p.volume_ml, p.units_per_basket, p.container_type,
                   p.container_group, p.image_filename, i.total_units, pp.presentation_code, pp.price
            FROM inventory_item i
            JOIN beer_product p ON p.id = i.product_id
            LEFT JOIN product_price pp ON pp.product_id = p.id
            """;

    private final JdbcConnectionProvider connectionProvider;
    private final JdbcTransactionManager transactionManager;

    public InventoryRepositoryJdbc(JdbcConnectionProvider connectionProvider,
                                   JdbcTransactionManager transactionManager) {
        if (connectionProvider == null || transactionManager == null) {
            throw new IllegalArgumentException("El proveedor JDBC y el coordinador son obligatorios.");
        }
        this.connectionProvider = connectionProvider;
        this.transactionManager = transactionManager;
    }

    @Override
    public void save(InventoryItem item) {
        if (item == null) {
            throw new IllegalArgumentException("El inventario es obligatorio.");
        }
        if (!transactionManager.isTransactionActive()) {
            throw new IllegalStateException("Guardar inventario requiere una transacción JDBC activa.");
        }
        String sql = """
                INSERT INTO inventory_item (product_id, total_units)
                VALUES (?, ?)
                ON CONFLICT (product_id) DO NOTHING
                """;
        connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, item.getProduct().getId());
                statement.setInt(2, item.totalUnits());
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Ya existe inventario para el producto "
                            + item.getProduct().getId()
                            + "; los cambios deben aplicarse con InventoryStockAdjuster.");
                }
            }
            return null;
        });
    }

    @Override
    public Optional<InventoryItem> findByProductId(String productId) {
        return findOne(productId, INVENTORY_SELECT + " WHERE i.product_id = ?");
    }

    @Override
    public Optional<InventoryItem> findByProductIdForUpdate(String productId) {
        if (!transactionManager.isTransactionActive()) {
            throw new IllegalStateException("El bloqueo de inventario requiere una transacción JDBC activa.");
        }
        return findOne(productId, INVENTORY_SELECT + " WHERE i.product_id = ? FOR UPDATE OF i");
    }

    private Optional<InventoryItem> findOne(String productId, String sql) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, productId);
                return readItems(statement).stream().findFirst();
            }
        });
    }

    @Override
    public List<InventoryItem> findAll() {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INVENTORY_SELECT + " ORDER BY i.product_id")) {
                return readItems(statement);
            }
        });
    }

    private static List<InventoryItem> readItems(PreparedStatement statement) throws SQLException {
        Map<String, InventoryItem> items = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String productId = rows.getString("id");
                InventoryItem item = items.get(productId);
                if (item == null) {
                    item = new InventoryItem(ProductRepositoryJdbc.readProduct(rows));
                    int totalUnits = rows.getInt("total_units");
                    if (totalUnits > 0) {
                        item.add(totalUnits);
                    }
                    items.put(productId, item);
                }
                ProductRepositoryJdbc.readPrice(rows, item.getProduct());
            }
        }
        return new ArrayList<>(items.values());
    }
}
