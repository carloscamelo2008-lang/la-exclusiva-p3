package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.repository.InventoryStockAdjuster;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Ajusta existencias con operaciones SQL por delta y participa en la transacción exterior. */
public final class InventoryStockAdjusterJdbc implements InventoryStockAdjuster {
    private final JdbcConnectionProvider connectionProvider;

    /** Crea el adaptador con el proveedor compartido de inventario, ventas y movimientos. */
    public InventoryStockAdjusterJdbc(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    /** {@inheritDoc} */
    @Override
    public int increaseStock(String productId, int units) {
        requireAdjustment(productId, units);
        return connectionProvider.withConnection(connection -> {
            String sql = """
                    INSERT INTO inventory_item (product_id, total_units)
                    VALUES (?, ?)
                    ON CONFLICT (product_id) DO UPDATE
                    SET total_units = inventory_item.total_units + EXCLUDED.total_units
                    RETURNING total_units
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, productId);
                statement.setInt(2, units);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return rows.getInt("total_units");
                    }
                }
            }
            throw new JdbcPersistenceException("PostgreSQL no devolvió las existencias incrementadas.");
        });
    }

    /** {@inheritDoc} */
    @Override
    public int decreaseStock(String productId, int units) {
        requireAdjustment(productId, units);
        return connectionProvider.withConnection(connection -> {
            String sql = """
                    UPDATE inventory_item
                    SET total_units = total_units - ?
                    WHERE product_id = ? AND total_units >= ?
                    RETURNING total_units
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, units);
                statement.setString(2, productId);
                statement.setInt(3, units);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return rows.getInt("total_units");
                    }
                }
            }
            int available = findAvailableUnits(connection, productId);
            throw new InsufficientStockException(productId, units, available);
        });
    }

    private static int findAvailableUnits(java.sql.Connection connection, String productId) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT total_units FROM inventory_item WHERE product_id = ?")) {
            statement.setString(1, productId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("total_units") : 0;
            }
        }
    }

    private static void requireAdjustment(String productId, int units) {
        if (productId == null || productId.isBlank() || productId.length() > 64) {
            throw new IllegalArgumentException("El ID de producto debe caber en VARCHAR(64) y no estar vacío.");
        }
        if (units <= 0) {
            throw new IllegalArgumentException("El ajuste de existencias debe ser mayor que cero.");
        }
    }
}
