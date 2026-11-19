package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.service.UserAdministrationBoundary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Serializa operaciones administrativas cooperativas con el bloqueo reservado de identidad.
 * El coordinador usa el contexto del proveedor recibido: se une a sus transacciones exteriores
 * sin crear otro proveedor ni contexto JDBC. No realiza consultas de cuentas ni aplica permisos.
 * Todos los participantes deben usar ese proveedor, en el mismo hilo, y adquirir este bloqueo
 * antes de bloquear cuentas. No protege escrituras ajenas al protocolo.
 */
public final class JdbcUserAdministrationBoundary implements UserAdministrationBoundary {
    private final JdbcConnectionProvider connections;
    private final JdbcTransactionManager transactions;

    /**
     * Prepara la frontera sin abrir conexiones. El coordinador comparte el contexto existente.
     * @param connections proveedor utilizado también por los repositorios de identidad
     * @throws IllegalArgumentException si el proveedor es nulo
     */
    public JdbcUserAdministrationBoundary(JdbcConnectionProvider connections) {
        if (connections == null) throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        this.connections = connections;
        this.transactions = new JdbcTransactionManager(connections);
    }

    /**
     * Exige READ COMMITTED de escritura sin alterar el aislamiento de la sesión.
     * El bloqueo se libera al finalizar la transacción exterior, no al salir de una llamada anidada.
     * Fallos internos marcan rollback-only aunque el llamador los capture fuera de esta frontera.
     * Los errores SQL se traducen sin mensajes brutos, causas ni excepciones suprimidas.
     * Un fallo al confirmar o cerrar puede dejar resultado incierto: no se reintenta.
     * @param operation sección administrativa que utiliza el mismo proveedor y el mismo hilo
     * @param <T> tipo del resultado
     * @return resultado provisional si existe una transacción exterior
     */
    @Override
    public <T> T execute(Supplier<T> operation) {
        if (operation == null) throw new IllegalArgumentException("La operación administrativa es obligatoria.");
        try {
            return transactions.inTransaction(() -> {
                connections.withConnection(connection -> {
                    if (connection.getAutoCommit() || connection.isReadOnly()
                            || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                        throw new IllegalStateException("La administración requiere una transacción READ COMMITTED de escritura.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT pg_advisory_xact_lock(1279346008, 1)")) {
                        statement.setQueryTimeout(10);
                        try (ResultSet rows = statement.executeQuery()) {
                            if (!rows.next() || rows.next()) {
                                throw new JdbcPersistenceException("El bloqueo administrativo devolvió un resultado inesperado.");
                            }
                        }
                    }
                    return null;
                });
                return operation.get();
            });
        } catch (RuntimeException failure) {
            SQLException sql = findSqlFailure(failure, 0);
            if (sql == null && !(failure instanceof JdbcPersistenceException)) throw failure;
            String state = sql == null ? null : sql.getSQLState();
            String safeState = state != null && state.matches("[0-9A-Z]{5}") ? state : "NO_DISPONIBLE";
            throw new JdbcPersistenceException("No se pudo completar la operación administrativa "
                    + "[SQLSTATE=" + safeState + "]. El resultado puede ser incierto; no reintentar automáticamente.");
        }
    }

    private static SQLException findSqlFailure(Throwable failure, int depth) {
        if (failure == null || depth >= 8) return null;
        if (failure instanceof SQLException sql) return sql;
        SQLException cause = findSqlFailure(failure.getCause(), depth + 1);
        if (cause != null) return cause;
        for (Throwable suppressed : failure.getSuppressed()) {
            SQLException sql = findSqlFailure(suppressed, depth + 1);
            if (sql != null) return sql;
        }
        return null;
    }
}
