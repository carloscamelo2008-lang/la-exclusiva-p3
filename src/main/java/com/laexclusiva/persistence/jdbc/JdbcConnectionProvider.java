package com.laexclusiva.persistence.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Proporciona conexiones PostgreSQL a los DAO.
 *
 * <p>Fuera de una transacción abre y cierra una conexión para la operación. Dentro de una
 * transacción reutiliza la conexión enlazada al hilo actual. Los DAO nunca deben cerrar,
 * confirmar ni revertir la conexión recibida por withConnection.</p>
 *
 * <p>Una transacción debe ejecutarse de principio a fin en el mismo hilo.</p>
 */
public final class JdbcConnectionProvider {
    private final DatabaseConfig config;
    private final ThreadLocal<TransactionState> transactionState = new ThreadLocal<>();

    public JdbcConnectionProvider(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración JDBC es obligatoria.");
        }
        this.config = config;
    }

    /**
     * Firma acordada para los DAO: withConnection(SqlFunction<T> operation).
     * Si existe una transacción, reutiliza su conexión sin cerrarla. En caso contrario,
     * abre una conexión independiente y la cierra al terminar.
     *
     * @param operation operación que utilizará la conexión
     * @param <T> tipo del resultado
     * @return resultado de la operación
     */
    public <T> T withConnection(SqlFunction<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("La operación JDBC es obligatoria.");
        }

        TransactionState state = transactionState.get();
        if (state != null) {
            try {
                return operation.apply(state.connection);
            } catch (SQLException failure) {
                state.rollbackOnly = true;
                throw new JdbcPersistenceException("No se pudo ejecutar la operación SQL.", failure);
            } catch (RuntimeException | Error failure) {
                state.rollbackOnly = true;
                throw failure;
            }
        }

        try (Connection connection = openConnection()) {
            return operation.apply(connection);
        } catch (SQLException failure) {
            throw new JdbcPersistenceException("No se pudo ejecutar la operación SQL o cerrar la conexión.", failure);
        }
    }

    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
    }

    boolean hasActiveTransaction() {
        return transactionState.get() != null;
    }

    void bindTransaction(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión transaccional es obligatoria.");
        }
        if (transactionState.get() != null) {
            throw new IllegalStateException("Ya existe una transacción JDBC activa en este hilo.");
        }
        transactionState.set(new TransactionState(connection));
    }

    boolean isRollbackOnly() {
        TransactionState state = transactionState.get();
        return state != null && state.rollbackOnly;
    }

    void markRollbackOnly() {
        TransactionState state = transactionState.get();
        if (state != null) {
            state.rollbackOnly = true;
        }
    }

    void clearTransaction() {
        transactionState.remove();
    }

    private static final class TransactionState {
        private final Connection connection;
        private boolean rollbackOnly;

        private TransactionState(Connection connection) {
            this.connection = connection;
        }
    }
}
