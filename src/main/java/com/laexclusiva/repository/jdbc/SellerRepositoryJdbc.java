package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.Seller;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.repository.SellerRepositoryInterface;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Adaptador JDBC del repositorio de vendedores, sin operaciones de reemplazo implícitas. */
public final class SellerRepositoryJdbc implements SellerRepositoryInterface {

    private static final String SELLER_COLUMNS = "id, name, phone, address";

    private final JdbcConnectionProvider connectionProvider;

    /**
     * Crea el adaptador con el proveedor de conexiones compartido por la unidad de trabajo.
     *
     * @param connectionProvider proveedor JDBC requerido
     * @throws IllegalArgumentException si el proveedor es nulo
     */
    public SellerRepositoryJdbc(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Inserta un vendedor conservando su ID y sin reemplazar una fila existente.
     *
     * @param seller vendedor que se insertará
     * @throws IllegalArgumentException si el vendedor es nulo, el ID excede V001 o ya existe
     * @throws JdbcPersistenceException si PostgreSQL rechaza los datos o falla la operación
     */
    @Override
    public void save(Seller seller) {
        requireSeller(seller);
        connectionProvider.withConnection(connection -> {
            String sql = "INSERT INTO seller (id, name, phone, address) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, seller.getId());
                statement.setString(2, seller.getName());
                statement.setString(3, seller.getPhone());
                statement.setString(4, seller.getAddress());
                try {
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("No se insertó exactamente un vendedor.");
                    }
                } catch (SQLException failure) {
                    if ("23505".equals(failure.getSQLState())) {
                        throw new IllegalArgumentException(
                                "Ya existe un vendedor registrado con el ID: " + seller.getId(), failure);
                    }
                    throw failure;
                }
            }
            return null;
        });
    }

    /**
     * Busca un vendedor por su ID histórico.
     *
     * @param id identificador que se consultará
     * @return vendedor encontrado o {@link Optional#empty()} si no existe o el ID es inválido
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public Optional<Seller> findById(String id) {
        if (id == null || id.isBlank() || id.length() > 32) {
            return Optional.empty();
        }
        return connectionProvider.withConnection(connection -> {
            String sql = "SELECT " + SELLER_COLUMNS + " FROM seller WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapSeller(resultSet)) : Optional.empty();
                }
            }
        });
    }

    /**
     * Devuelve snapshots de todos los vendedores ordenados por ID.
     *
     * @return lista independiente; vacía si no hay filas
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public List<Seller> findAll() {
        return connectionProvider.withConnection(connection -> {
            String sql = "SELECT " + SELLER_COLUMNS + " FROM seller ORDER BY id";
            List<Seller> sellers = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sellers.add(mapSeller(resultSet));
                }
            }
            return sellers;
        });
    }

    private static Seller mapSeller(ResultSet resultSet) throws SQLException {
        return new Seller(resultSet.getString("id"), resultSet.getString("name"),
                resultSet.getString("phone"), resultSet.getString("address"));
    }

    private static void requireSeller(Seller seller) {
        if (seller == null) {
            throw new IllegalArgumentException("El vendedor no puede ser nulo.");
        }
        if (seller.getId() == null || seller.getId().isBlank()) {
            throw new IllegalArgumentException("El ID del vendedor es obligatorio.");
        }
        if (seller.getId().length() > 32) {
            throw new IllegalArgumentException("El ID del vendedor supera el límite VARCHAR(32) de V001.");
        }
    }
}
