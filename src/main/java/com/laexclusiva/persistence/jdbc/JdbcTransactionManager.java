package com.laexclusiva.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Coordina transacciones JDBC. Las llamadas anidadas se unen a la transacción exterior:
 * solo quien abrió la conexión puede hacer commit o rollback.
 */
public final class JdbcTransactionManager implements TransactionBoundary {
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcConnectionContext connectionContext;

    public JdbcTransactionManager(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
        this.connectionContext = connectionProvider.connectionContext();
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
    @Override
    public <T> T inTransaction(Supplier<T> operation) {
        return inTransaction(operation, null);
    }

    /**
     * Ejecuta varias lecturas en una transacción PostgreSQL con una instantánea repetible.
     * Si la llamada está anidada, solo se une cuando la transacción exterior ya usa el mismo
     * aislamiento; nunca afirma consistencia bajo una transacción {@code READ COMMITTED}.
     *
     * @param operation lecturas que deben observar el mismo estado confirmado
     * @param <T> tipo del resultado
     * @return resultado leído
     * @throws IllegalStateException si una transacción exterior usa otro aislamiento
     * @throws JdbcPersistenceException si no se puede abrir, leer, confirmar o cerrar la transacción
     */
    @Override
    public <T> T inReadOnlyRepeatableRead(Supplier<T> operation) {
        return inTransaction(operation, Connection.TRANSACTION_REPEATABLE_READ);
    }

    private <T> T inTransaction(Supplier<T> operation, Integer requiredIsolation) {
        if (operation == null) {
            throw new IllegalArgumentException("La operación transaccional es obligatoria.");
        }

        if (connectionContext.hasActiveTransaction()) {
            try {
                if (requiredIsolation != null) {
                    int activeIsolation = connectionProvider.withConnection(Connection::getTransactionIsolation);
                    if (activeIsolation != requiredIsolation) {
                        throw new IllegalStateException("La lectura consistente requiere una transacción "
                                + "REPEATABLE_READ; no puede unirse a la transacción actual.");
                    }
                }
                return operation.get();
            } catch (RuntimeException | Error failure) {
                connectionContext.markRollbackOnly();
                throw failure;
            }
        }

        try (Connection connection = connectionProvider.openConnection()) {
            if (requiredIsolation != null) {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(requiredIsolation);
            }
            connection.setAutoCommit(false);
            connectionContext.bindTransaction(connection);
            try {
                T result = operation.get();
                if (connectionContext.isRollbackOnly()) {
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
                connectionContext.clearTransaction();
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
        return connectionContext.hasActiveTransaction();
    }

    /** Variante para operaciones que no necesitan devolver un resultado. */
    @Override
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
