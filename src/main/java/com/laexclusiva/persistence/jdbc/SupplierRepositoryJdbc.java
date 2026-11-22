package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.Supplier;
import com.laexclusiva.repository.SupplierRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.laexclusiva.persistence.jdbc.PurchaseOrderRepositoryJdbc.requireProvider;
import static com.laexclusiva.persistence.jdbc.PurchaseOrderRepositoryJdbc.safe;

/** Persiste proveedores en PostgreSQL con la conexión compartida del caso de uso. */
public final class SupplierRepositoryJdbc implements SupplierRepository {
    private final JdbcConnectionProvider connections;

    /** @param connections proveedor JDBC compartido */
    public SupplierRepositoryJdbc(JdbcConnectionProvider connections) {
        this.connections = requireProvider(connections);
    }

    /** {@inheritDoc} */
    @Override
    public Supplier save(Supplier supplier) {
        if (supplier == null || supplier.id() != null) {
            throw new IllegalArgumentException("Se requiere un proveedor nuevo sin ID.");
        }
        return write("""
                INSERT INTO supplier (name, tax_id, contact_info, active, created_at)
                VALUES (?, ?, ?, ?, ?) RETURNING *
                """, supplier);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Supplier> findById(long id) {
        return query("SELECT * FROM supplier WHERE id = ?", id).stream().findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public List<Supplier> findAll() {
        return query("SELECT * FROM supplier ORDER BY id", null);
    }

    /** {@inheritDoc} */
    @Override
    public List<Supplier> findActive() {
        return query("SELECT * FROM supplier WHERE active = TRUE ORDER BY id", null);
    }

    /** {@inheritDoc} */
    @Override
    public Supplier update(Supplier supplier) {
        if (supplier == null || supplier.id() == null) {
            throw new IllegalArgumentException("El proveedor indicado no existe.");
        }
        return write("""
                UPDATE supplier SET name = ?, tax_id = ?, contact_info = ?, active = ?
                WHERE id = ? RETURNING *
                """, supplier);
    }

    private Supplier write(String sql, Supplier supplier) {
        return safe(() -> connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, supplier.name());
                statement.setString(2, supplier.taxId());
                statement.setString(3, supplier.contactInfo());
                statement.setBoolean(4, supplier.active());
                if (supplier.id() == null) statement.setObject(5, supplier.createdAt());
                else statement.setLong(5, supplier.id());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("El proveedor indicado no existe.");
                    return map(rows);
                }
            }
        }));
    }

    private List<Supplier> query(String sql, Long id) {
        return safe(() -> connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (id != null) statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    List<Supplier> result = new ArrayList<>();
                    while (rows.next()) result.add(map(rows));
                    return List.copyOf(result);
                }
            }
        }));
    }

    private static Supplier map(ResultSet rows) throws SQLException {
        return new Supplier(rows.getLong("id"), rows.getString("name"), rows.getString("tax_id"),
                rows.getString("contact_info"), rows.getBoolean("active"),
                rows.getObject("created_at", LocalDateTime.class));
    }
}
