package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.persistence.jdbc.SqlFunction;
import com.laexclusiva.repository.UserRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/**
 * Persiste cuentas sobre V001 y V104 sin autenticar ni administrar permisos.
 * Reutiliza la conexión transaccional del proveedor; no confirma ni cierra conexiones exteriores.
 * Devuelve cuentas independientes y nunca incluye hashes ni detalles del servidor en sus errores.
 */
public final class UserRepositoryJdbc implements UserRepository {
    private static final String INSERT_COLUMNS = "id, username, password_hash, role, active, seller_id, created_at";
    private static final String COLUMNS = INSERT_COLUMNS + ", security_version";
    // Límite defensivo: no repara secuencias atrasadas. Su comportamiento real sigue pendiente de validar.
    private static final int GENERATED_ID_ATTEMPTS = 64;
    private final JdbcConnectionProvider connections;

    /**
     * Crea el adaptador sobre la infraestructura JDBC existente.
     * @param connections proveedor compartido por la unidad de trabajo
     * @throws IllegalArgumentException si falta el proveedor
     */
    public UserRepositoryJdbc(JdbcConnectionProvider connections) {
        if (connections == null) throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        this.connections = connections;
    }

    /**
     * Inserta una cuenta sin reemplazar otra; conserva un ID explícito o recupera el generado por SQL.
     * ID y versión se asignan después de cerrar los recursos propios; no demuestran el commit exterior.
     * Un fallo de comunicación tras escribir puede dejar un resultado incierto; no reintentar automáticamente.
     * Si la secuencia encuentra 64 IDs históricos ocupados, falla sin reemplazarlos ni ajustar la secuencia.
     * @param user cuenta con hash PBKDF2, rol y fecha de creación
     * @throws IllegalArgumentException por datos inválidos, duplicados o vendedor inexistente
     * @throws JdbcPersistenceException si falla la persistencia
     */
    @Override
    public void save(UserAccount user) {
        validate(user);
        SavedAccount saved = execute("INSERT_USER", connection -> {
            if (user.getId() != null) {
                String sql = "INSERT INTO user_account (" + INSERT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "RETURNING id, security_version";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, user.getId());
                    bindAccount(statement, user, 2);
                    statement.setObject(7, normalize(user.getCreatedAt()));
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw new JdbcPersistenceException("La inserción no devolvió una cuenta.");
                        SavedAccount result = readInserted(rows);
                        if (result.id() != user.getId()) {
                            throw new JdbcPersistenceException("La inserción devolvió un ID inesperado.");
                        }
                        return result;
                    }
                }
            }
            return insertGenerated(connection, user);
        });
        user.setId(saved.id());
        user.setSecurityVersion(saved.version());
    }

    /**
     * Busca una cuenta por su identificador numérico sin cambiar la firma compartida.
     * @param id ID textual, con espacios exteriores opcionales
     * @return cuenta independiente o vacío si el ID es inválido o no existe
     * @throws JdbcPersistenceException si falla SQL o la cuenta persistida es inválida
     */
    @Override
    public Optional<UserAccount> findById(String id) {
        Long parsed = parseId(id);
        if (parsed == null) return Optional.empty();
        return execute("FIND_USER_BY_ID", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM user_account WHERE id = ?")) {
                statement.setLong(1, parsed);
                return readOne(statement);
            }
        });
    }

    /**
     * Busca usando la misma normalización SQL que el índice único de V001.
     * @param username nombre de usuario; SQL ignora mayúsculas y espacios U+0020 exteriores
     * @return cuenta independiente o vacío para nombres inválidos o ausentes
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null || sqlTrim(username).isEmpty() || sqlTrim(username).length() > 100) return Optional.empty();
        return execute("FIND_USER_BY_USERNAME", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                    + " FROM user_account WHERE lower(btrim(username)) = lower(btrim(?))")) {
                statement.setString(1, sqlTrim(username));
                return readOne(statement);
            }
        });
    }

    /**
     * Lista cuentas independientes en orden estable de ID, incluidos los usuarios inactivos.
     * @return lista vacía cuando no hay cuentas
     * @throws JdbcPersistenceException si falla la consulta
     */
    @Override
    public List<UserAccount> findAll() {
        return execute("LIST_USERS", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM user_account ORDER BY id");
                 ResultSet rows = statement.executeQuery()) {
                List<UserAccount> users = new ArrayList<>();
                while (rows.next()) users.add(map(rows));
                return users;
            }
        });
    }

    /**
     * Actualiza credenciales, rol, estado y vendedor sin reemplazar el ID ni la fecha histórica.
     * La autorización y las reglas del último administrador corresponden al futuro servicio de gestión.
     * La versión recibida es la esperada; PostgreSQL calcula la resultante mediante V104.
     * Dentro de una transacción exterior, la versión asignada es provisional hasta el commit.
     * Tras rollback se debe descartar o recargar la cuenta; no se restaura automáticamente el objeto.
     * @param user cuenta existente que se actualizará
     * @throws IllegalArgumentException por datos inválidos, duplicados o FK inválida
     * @throws ConcurrentModificationException si la cuenta no existe o su versión no coincide
     * @throws JdbcPersistenceException si falla SQL
     */
    @Override
    public void update(UserAccount user) {
        validate(user);
        if (user.getId() == null) throw new IllegalArgumentException("El ID del usuario es obligatorio.");
        long version = execute("UPDATE_USER", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE user_account SET username = ?, "
                    + "password_hash = ?, role = ?, active = ?, seller_id = ? "
                    + "WHERE id = ? AND security_version = ? RETURNING security_version")) {
                bindAccount(statement, user, 1);
                statement.setLong(6, user.getId());
                statement.setLong(7, user.getSecurityVersion());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new ConcurrentModificationException("La cuenta no existe o fue modificada; vuelva a cargarla.");
                    }
                    long result = readSecurityVersion(rows);
                    if (rows.next()) throw new JdbcPersistenceException("La actualización devolvió varias cuentas.");
                    return result;
                }
            }
        });
        user.setSecurityVersion(version);
    }

    /**
     * Conserva el borrado físico del contrato; no ofrece una acción de interfaz ni aplica permisos.
     * @param id ID textual; los IDs inválidos o inexistentes no producen cambios
     * @throws JdbcPersistenceException si falla SQL
     * @throws IllegalArgumentException si una referencia impide completar la operación
     */
    @Override
    public void delete(String id) {
        Long parsed = parseId(id);
        if (parsed == null) return;
        execute("DELETE_USER", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM user_account WHERE id = ?")) {
                statement.setLong(1, parsed);
                int changed = statement.executeUpdate();
                if (changed < 0 || changed > 1) throw new JdbcPersistenceException("Resultado de borrado inesperado.");
            }
            return null;
        });
    }

    private static SavedAccount insertGenerated(Connection connection, UserAccount user) throws SQLException {
        String sql = "INSERT INTO user_account (username, password_hash, role, active, seller_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING RETURNING id, security_version";
        // V001 permite IDs históricos explícitos que no adelantan la secuencia. Saltarlos no reemplaza cuentas.
        for (int attempt = 0; attempt < GENERATED_ID_ATTEMPTS; attempt++) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindAccount(statement, user, 1);
                statement.setObject(6, normalize(user.getCreatedAt()));
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return readInserted(rows);
                    }
                }
            }
        }
        throw new JdbcPersistenceException("No se pudo asignar un ID libre; revise la secuencia de usuarios.");
    }

    private record SavedAccount(long id, long version) { }

    private static SavedAccount readInserted(ResultSet rows) throws SQLException {
        Long id = rows.getObject("id", Long.class);
        long version = readSecurityVersion(rows);
        if (id == null || id <= 0 || version != 0 || rows.next()) {
            throw new JdbcPersistenceException("La inserción devolvió metadatos de cuenta inesperados.");
        }
        return new SavedAccount(id, version);
    }

    private static long readSecurityVersion(ResultSet rows) throws SQLException {
        Long version = rows.getObject("security_version", Long.class);
        if (version == null || version < 0) {
            throw new JdbcPersistenceException("La versión de seguridad persistida es inválida.");
        }
        return version;
    }

    private static void bindAccount(PreparedStatement statement, UserAccount user, int start) throws SQLException {
        statement.setString(start, sqlTrim(user.getUsername()));
        statement.setString(start + 1, user.getPasswordHash());
        statement.setString(start + 2, user.getRole().name());
        statement.setBoolean(start + 3, user.isActive());
        if (user.getSellerId() == null) statement.setNull(start + 4, Types.VARCHAR);
        else statement.setString(start + 4, user.getSellerId());
    }

    private static Optional<UserAccount> readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(map(rows)) : Optional.empty();
        }
    }

    private static UserAccount map(ResultSet rows) throws SQLException {
        try {
            UserAccount user = new UserAccount(rows.getLong("id"), rows.getString("username"),
                    rows.getString("password_hash"), UserRole.valueOf(rows.getString("role")),
                    rows.getString("seller_id"));
            user.setActive(rows.getBoolean("active"));
            user.setCreatedAt(rows.getObject("created_at", LocalDateTime.class));
            user.setSecurityVersion(readSecurityVersion(rows));
            // Leer no aplica la política de hashes nuevos: conserva el valor histórico opaco.
            // AuthenticationService/PasswordHasher deciden si ese valor permite autenticar.
            validateFields(user);
            return user;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new JdbcPersistenceException("La cuenta persistida contiene datos inválidos.");
        }
    }

    private <T> T execute(String operationName, SqlFunction<T> operation) {
        try {
            return connections.withConnection(connection -> {
                try {
                    return operation.apply(connection);
                } catch (SQLException failure) {
                    if ("23505".equals(failure.getSQLState())) {
                        throw new IllegalArgumentException("Ya existe un usuario con el ID o nombre indicado.");
                    }
                    if ("23503".equals(failure.getSQLState())) {
                        throw new IllegalArgumentException("El vendedor no existe o una referencia impide la operación.");
                    }
                    throw sqlFailure(operationName, failure);
                }
            });
        } catch (JdbcPersistenceException failure) {
            if (failure.getCause() != null || failure.getSuppressed().length > 0) {
                throw sqlFailure(operationName, findSqlCause(failure));
            }
            throw failure;
        } catch (ConcurrentModificationException failure) {
            // El cierre también puede adjuntar un SQLException sensible al conflicto de versión.
            if (failure.getSuppressed().length > 0) {
                throw new ConcurrentModificationException("La cuenta no existe o fue modificada; vuelva a cargarla.");
            }
            throw failure;
        } catch (IllegalArgumentException failure) {
            // Un fallo del cierre no debe adjuntar detalles SQL sensibles a un error de dominio.
            if (failure.getSuppressed().length > 0) throw new IllegalArgumentException(failure.getMessage());
            throw failure;
        }
    }

    private static JdbcPersistenceException sqlFailure(String operationName, SQLException failure) {
        String state = failure == null ? null : failure.getSQLState();
        String safeState = state != null && state.matches("[0-9A-Z]{5}") ? state : "NO_DISPONIBLE";
        int vendorCode = failure == null ? 0 : failure.getErrorCode();
        // Nunca conservar mensajes, parámetros ni Throwable originales: pueden contener la fila completa.
        return new JdbcPersistenceException("No se pudo completar la operación de usuarios [operación="
                + operationName + "; SQLSTATE=" + safeState + "; código=" + vendorCode + "].");
    }

    private static SQLException findSqlCause(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof SQLException sql) return sql;
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed instanceof SQLException sql) return sql;
            }
        }
        return null;
    }

    private static void validate(UserAccount user) {
        validateFields(user);
        if (!isPasswordHash(user.getPasswordHash())) {
            throw new IllegalArgumentException("Se requiere un hash PBKDF2 válido, no una contraseña en claro.");
        }
    }

    private static void validateFields(UserAccount user) {
        if (user == null) throw new IllegalArgumentException("La cuenta de usuario es obligatoria.");
        if (user.getId() != null && user.getId() <= 0) throw new IllegalArgumentException("El ID debe ser positivo.");
        if (user.getUsername() == null || user.getUsername().isBlank()
                || sqlTrim(user.getUsername()).length() > 100 || user.getUsername().indexOf('\0') >= 0) {
            throw new IllegalArgumentException("El nombre de usuario debe tener entre 1 y 100 caracteres válidos.");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()
                || user.getPasswordHash().length() > 255 || user.getPasswordHash().indexOf('\0') >= 0) {
            throw new IllegalArgumentException("El hash almacenado no cumple los límites del esquema.");
        }
        if (user.getRole() == null || user.getCreatedAt() == null) {
            throw new IllegalArgumentException("El rol y la fecha de creación son obligatorios.");
        }
        if (user.getSellerId() != null && (user.getSellerId().isBlank() || user.getSellerId().length() > 32
                || user.getSellerId().indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("El ID de vendedor debe tener entre 1 y 32 caracteres válidos.");
        }
    }

    private static boolean isPasswordHash(String hash) {
        if (hash == null || hash.length() > 255) return false;
        String[] parts = hash.split("\\$", -1);
        if (parts.length != 5 || !parts[1].equals("pbkdf2-sha256") || !parts[0].isEmpty()
                || !parts[2].matches("i=[1-9][0-9]{0,9}")) return false;
        try {
            return Integer.parseInt(parts[2].substring(2)) > 0
                    && Base64.getDecoder().decode(parts[3]).length >= 16
                    && Base64.getDecoder().decode(parts[4]).length == 32;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static Long parseId(String id) {
        if (id == null) return null;
        try {
            long parsed = Long.parseLong(id.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** Equivale a btrim(text) sin segundo argumento: no elimina tabulaciones ni cambia el case en Java. */
    private static String sqlTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') start++;
        while (end > start && value.charAt(end - 1) == ' ') end--;
        return value.substring(start, end);
    }

    private static LocalDateTime normalize(LocalDateTime timestamp) {
        return timestamp.withNano(timestamp.getNano() / 1_000 * 1_000);
    }

}
