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
import org.postgresql.util.PSQLException;
import com.laexclusiva.model.CustomerDeletionBlockedException;

/** Adaptador JDBC del repositorio de clientes que conserva sus identificadores históricos. */
public final class CustomerRepositoryJdbc implements CustomerRepositoryInterface {

    private static final String CUSTOMER_COLUMNS = "id, name, phone, address, balance, credit_limit";

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
            String sql = "INSERT INTO customer (id, name, phone, address, balance, credit_limit) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, customer.getId());
                statement.setString(2, customer.getName());
                statement.setString(3, customer.getPhone());
                statement.setString(4, customer.getAddress());
                statement.setBigDecimal(5, balance);
                statement.setBigDecimal(6, customer.getCreditLimit());
                try {
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("No se insertó exactamente un cliente.");
                    }
                } catch (SQLException failure) {
                    rejectDuplicatePhone(failure);
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
     * Lee y bloquea la fila del cliente para serializar una venta a crédito con sus abonos.
     * El bloqueo usa la misma conexión transaccional que las consultas de cartera y los ajustes
     * de saldo, por lo que se conserva hasta el commit o rollback del flujo.
     *
     * @param id identificador del cliente
     * @return snapshot bloqueado o {@code null} si no existe
     * @throws IllegalArgumentException si el ID es nulo o está en blanco
     * @throws IllegalStateException si no hay una transacción JDBC compartida activa
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public Customer findByIdForUpdate(String id) {
        requireId(id);
        return connectionProvider.withConnection(connection -> {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException(
                        "El bloqueo financiero del cliente requiere una transacción JDBC compartida.");
            }
            String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customer WHERE id = ? FOR UPDATE";
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
        return readCustomers(null);
    }

    private List<Customer> readCustomers(String nameKey) {
        return connectionProvider.withConnection(connection -> {
            String sql = "SELECT " + CUSTOMER_COLUMNS + " FROM customer ORDER BY id";
            List<Customer> customers = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (nameKey == null || nameKey.equals(Customer.comparableName(resultSet.getString("name")))) {
                        customers.add(mapCustomer(resultSet));
                    }
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
                    "SELECT 1 FROM customer WHERE btrim(regexp_replace(id, '[[:space:]]+', ' ', 'g')) = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    /**
     * Actualiza el contacto y el cupo si el saldo persistido sigue coincidiendo con el snapshot.
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
            String sql = "UPDATE customer SET name = ?, phone = ?, address = ?, credit_limit = ? WHERE id = ? AND balance = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, customer.getName());
                statement.setString(2, customer.getPhone());
                statement.setString(3, customer.getAddress());
                statement.setBigDecimal(4, customer.getCreditLimit());
                statement.setString(5, customer.getId());
                statement.setBigDecimal(6, balance);
                int updated;
                try {
                    updated = statement.executeUpdate();
                } catch (SQLException failure) {
                    rejectDuplicatePhone(failure);
                    throw failure;
                }
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

    /** Consulta nombres usando la misma comparación Unicode que el modo memoria. */
    @Override
    public List<Customer> findSimilarNames(String name) {
        String key = Customer.comparableName(name);
        return key.isEmpty() ? List.of() : readCustomers(key);
    }

    /** Busca el teléfono canónico con ayuda del índice único. */
    @Override
    public boolean existsByPhone(String phone, String excludedId) {
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM customer WHERE phone = ? AND id IS DISTINCT FROM ?")) {
                statement.setString(1, phone);
                statement.setString(2, excludedId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }

    /** Deja que PostgreSQL compruebe las referencias incluso ante inserciones concurrentes. */
    @Override
    public void deleteById(String id) {
        requireId(id);
        connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM customer WHERE id = ? AND balance = 0")) {
                statement.setString(1, id);
                try {
                    if (statement.executeUpdate() == 1) return null;
                } catch (SQLException failure) {
                    // PostgreSQL 18 puede distinguir RESTRICT de la infracción general de FK.
                    if ("23503".equals(failure.getSQLState()) || "23001".equals(failure.getSQLState())) {
                        throw new CustomerDeletionBlockedException();
                    }
                    throw failure;
                }
            }
            if (customerExists(connection, id)) throw new CustomerDeletionBlockedException();
            throw new IllegalArgumentException("El cliente no existe.");
        });
    }

    private static void rejectDuplicatePhone(SQLException failure) {
        if ("23505".equals(failure.getSQLState()) && failure instanceof PSQLException postgres
                && postgres.getServerErrorMessage() != null
                && "uq_customer_phone".equals(postgres.getServerErrorMessage().getConstraint())) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con ese teléfono.");
        }
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
        return Customer.reconstitute(resultSet.getString("id"), resultSet.getString("name"),
                resultSet.getString("phone"), resultSet.getString("address"),
                resultSet.getBigDecimal("balance"), resultSet.getBigDecimal("credit_limit"));
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
