package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.Customer;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

/** Adaptador JDBC del repositorio de clientes que conserva sus identificadores históricos. */
public final class CustomerRepositoryJdbc implements CustomerRepositoryInterface {

    private static final String CUSTOMER_COLUMNS = "id, name, phone, address, balance";

    private final JdbcConnectionProvider connectionProvider;

    /**
     * Crea el adaptador con el proveedor de conexiones compartido por la unidad de trabajo.
     *
     * @param connectionProvider proveedor JDBC requerido
     * @throws IllegalArgumentException si el proveedor es nulo
     */
    public CustomerRepositoryJdbc(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Inserta un cliente sin reemplazar una fila existente.
     *
     * @param customer cliente que se insertará
     * @throws IllegalArgumentException si el cliente es nulo, su ID excede V001 o ya existe
     * @throws JdbcPersistenceException si PostgreSQL rechaza los datos o falla la operación
     */
    @Override
    public void save(Customer customer) {
        requireCustomer(customer);
        BigDecimal balance = requireBalance(customer.getBalance());
        connectionProvider.withConnection(connection -> {
            String sql = "INSERT INTO customer (id, name, phone, address, balance) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, customer.getId());
                statement.setString(2, customer.getName());
                statement.setString(3, customer.getPhone());
                statement.setString(4, customer.getAddress());
                statement.setBigDecimal(5, balance);
                try {
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("No se insertó exactamente un cliente.");
                    }
                } catch (SQLException failure) {
                    if ("23505".equals(failure.getSQLState())) {
                        throw new IllegalArgumentException(
                                "Ya existe un cliente con el ID: " + customer.getId(), failure);
                    }
                    throw failure;
                }
            }
            return null;
        });
    }

    /**
     * Busca un cliente por su ID histórico.
     *
     * @param id identificador no vacío
     * @return snapshot del cliente o {@code null} si no existe
     * @throws IllegalArgumentException si el ID es nulo o está en blanco
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public Customer findById(String id) {
        requireId(id);
        return connectionProvider.withConnection(connection -> {
            String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customer WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? mapCustomer(resultSet) : null;
                }
            }
        });
    }

    /**
     * Devuelve snapshots de todos los clientes ordenados por ID.
     *
     * @return lista independiente; vacía si no hay filas
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public List<Customer> findAll() {
        return connectionProvider.withConnection(connection -> {
            String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customer ORDER BY id";
            List<Customer> customers = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    customers.add(mapCustomer(resultSet));
                }
            }
            return customers;
        });
    }

    /**
     * Comprueba la existencia de un cliente sin cargar su saldo ni sus datos personales.
     *
     * @param id identificador que se consultará
     * @return {@code true} si existe; {@code false} para IDs nulos o en blanco
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public boolean existsById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM customer WHERE id = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    /**
     * Actualiza solo los datos personales si el saldo persistido sigue coincidiendo con el snapshot.
     * El saldo se usa como condición de concurrencia y nunca aparece en la cláusula {@code SET}.
     *
     * @param customer snapshot cuyos datos personales se actualizarán
     * @throws IllegalArgumentException si el cliente es nulo o ya no existe
     * @throws ConcurrentModificationException si el saldo cambió desde la lectura del snapshot
     * @throws JdbcPersistenceException si falla la actualización
     */
    @Override
    public void update(Customer customer) {
        requireCustomer(customer);
        BigDecimal balance = requireBalance(customer.getBalance());
        connectionProvider.withConnection(connection -> {
            String sql = "UPDATE customer SET name = ?, phone = ?, address = ? WHERE id = ? AND balance = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, customer.getName());
                statement.setString(2, customer.getPhone());
                statement.setString(3, customer.getAddress());
                statement.setString(4, customer.getId());
                statement.setBigDecimal(5, balance);
                int updated = statement.executeUpdate();
                if (updated == 0) {
                    if (!customerExists(connection, customer.getId())) {
                        throw new IllegalArgumentException(
                                "No se puede actualizar: el cliente con ID " + customer.getId() + " no existe.");
                    }
                    throw new ConcurrentModificationException(
                            "No se puede actualizar el cliente con ID " + customer.getId()
                                    + ": el saldo cambió desde que se obtuvo el snapshot. Vuelva a cargarlo.");
                }
                if (updated != 1) {
                    throw new JdbcPersistenceException("La actualización afectó más de un cliente.");
                }
            }
            return null;
        });
    }

    private static boolean customerExists(java.sql.Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM customer WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static Customer mapCustomer(ResultSet resultSet) throws SQLException {
        return new Customer(resultSet.getString("id"), resultSet.getString("name"),
                resultSet.getString("phone"), resultSet.getString("address"),
                resultSet.getBigDecimal("balance"));
    }

    private static void requireCustomer(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        requireId(customer.getId());
        if (customer.getId().length() > 64) {
            throw new IllegalArgumentException("El ID del cliente supera el límite VARCHAR(64) de V001.");
        }
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El ID del cliente es obligatorio.");
        }
    }

    private static BigDecimal requireBalance(BigDecimal balance) {
        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo del cliente no puede ser nulo ni negativo.");
        }
        try {
            BigDecimal normalized = balance.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw new IllegalArgumentException("El saldo del cliente supera NUMERIC(19,2).");
            }
            return normalized;
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("El saldo del cliente no admite más de dos decimales.", failure);
        }
    }
}
