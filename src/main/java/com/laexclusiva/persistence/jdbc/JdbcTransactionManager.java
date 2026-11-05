package com.laexclusiva.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Coordina transacciones JDBC. Las llamadas anidadas se unen a la transacción exterior:
 * solo quien abrió la conexión puede hacer commit o rollback.
 */
public final class JdbcTransactionManager {
    private final JdbcConnectionProvider connectionProvider;

    public JdbcTransactionManager(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Ejecuta una operación dentro de una transacción. Abre una conexión, desactiva autocommit,
     * la enlaza al contexto del hilo y confirma al terminar. Cualquier excepción revierte la
     * operación. Si ya existe una transacción en el hilo, la operación se une a ella.
     *
     * @param operation lógica que debe ser atómica
     * @param <T> tipo del resultado
     * @return resultado de la operación
     */
    public <T> T inTransaction(Supplier<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("La operación transaccional es obligatoria.");
        }

        if (connectionProvider.hasActiveTransaction()) {
            try {
                return operation.get();
            } catch (RuntimeException | Error failure) {
                connectionProvider.markRollbackOnly();
                throw failure;
            }
        }

        try (Connection connection = connectionProvider.openConnection()) {
            connection.setAutoCommit(false);
            connectionProvider.bindTransaction(connection);
            try {
                T result = operation.get();
                if (connectionProvider.isRollbackOnly()) {
                    throw new JdbcPersistenceException(
                            "La transacción fue marcada para reversión por una operación interna.");
                }
                connection.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            } catch (SQLException failure) {
                rollback(connection, failure);
                throw new JdbcPersistenceException("No se pudo confirmar la transacción PostgreSQL.", failure);
            } finally {
                connectionProvider.clearTransaction();
            }
        } catch (SQLException failure) {
            throw new JdbcPersistenceException("No se pudo abrir o cerrar la conexión transaccional.", failure);
        }
    }

    /**
     * Indica si el hilo actual participa en una transacción administrada por este coordinador.
     * Los DAO deben exigir este estado antes de ejecutar consultas SELECT ... FOR UPDATE.
     *
     * @return true cuando existe una transacción activa en el hilo actual
     */
    public boolean isTransactionActive() {
        return connectionProvider.hasActiveTransaction();
    }

    /** Variante para operaciones que no necesitan devolver un resultado. */
    public void inTransaction(Runnable operation) {
        if (operation == null) {
            throw new IllegalArgumentException("La operación transaccional es obligatoria.");
        }
        inTransaction(() -> {
            operation.run();
            return null;
        });
    }

    private static void rollback(Connection connection, Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }
}
