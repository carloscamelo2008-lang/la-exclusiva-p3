package com.laexclusiva.persistence.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/** Mantiene el estado transaccional de un proveedor en el hilo actual. */
final class JdbcConnectionContext {
    private final ThreadLocal<TransactionState> transactionState = new ThreadLocal<>();

    private static Connection protect(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "close", "commit", "rollback", "setAutoCommit":
                            throw new UnsupportedOperationException(
                                    "El coordinador JDBC controla " + method.getName() + " durante la transacción.");
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "Conexión transaccional JDBC";
                        case "isWrapperFor":
                            return ((Class<?>) args[0]).isInstance(proxy);
                        case "unwrap":
                            Class<?> type = (Class<?>) args[0];
                            if (type.isInstance(proxy)) {
                                return type.cast(proxy);
                            }
                            throw new SQLException("La conexión transaccional no expone la conexión física.");
                        default:
                            try {
                                return method.invoke(connection, args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                    }
                });
    }

    Connection activeConnection() {
        TransactionState state = transactionState.get();
        return state == null ? null : state.connection;
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
        transactionState.set(new TransactionState(protect(connection)));
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
