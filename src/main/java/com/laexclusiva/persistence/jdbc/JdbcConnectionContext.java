package com.laexclusiva.persistence.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Mantiene el estado transaccional de un proveedor en el hilo actual. */
final class JdbcConnectionContext {
    private final ThreadLocal<TransactionState> transactionState = new ThreadLocal<>();

    private static Connection protect(Connection connection) {
        return guardedProxy(Connection.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "close", "commit", "rollback", "setAutoCommit", "abort" ->
                        throw new UnsupportedOperationException(
                                "El coordinador JDBC controla " + method.getName() + " durante la transacción.");
                case "createStatement", "prepareStatement", "prepareCall" ->
                        protectStatement((Statement) invoke(connection, method, args), (Connection) proxy);
                case "getMetaData" ->
                        protectMetadata((DatabaseMetaData) invoke(connection, method, args), (Connection) proxy);
                default -> invoke(connection, method, args);
            };
        });
    }

    private static Statement protectStatement(Statement statement, Connection connection) {
        Class<? extends Statement> type = Statement.class;
        if (statement instanceof CallableStatement) {
            type = CallableStatement.class;
        } else if (statement instanceof PreparedStatement) {
            type = PreparedStatement.class;
        }
        return guardedProxy(type, (proxy, method, args) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            Object result = invoke(statement, method, args);
            if (result instanceof ResultSet rows) {
                return protectResultSet(rows, connection, (Statement) proxy);
            }
            return result;
        });
    }

    private static DatabaseMetaData protectMetadata(DatabaseMetaData metadata, Connection connection) {
        return guardedProxy(DatabaseMetaData.class, (proxy, method, args) -> {
            if (method.getName().equals("getConnection")) {
                return connection;
            }
            Object result = invoke(metadata, method, args);
            if (result instanceof ResultSet rows) {
                return protectResultSet(rows, connection, null);
            }
            return result;
        });
    }

    private static ResultSet protectResultSet(ResultSet rows, Connection connection, Statement owner) {
        return guardedProxy(ResultSet.class, (proxy, method, args) -> {
            if (method.getName().equals("getStatement")) {
                if (owner != null) {
                    return owner;
                }
                Statement statement = rows.getStatement();
                return statement == null ? null : protectStatement(statement, connection);
            }
            Object result = invoke(rows, method, args);
            if (result instanceof ResultSet nestedRows) {
                return protectResultSet(nestedRows, connection, null);
            }
            return result;
        });
    }

    private static Object invoke(Object delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static <T> T guardedProxy(Class<T> jdbcType, java.lang.reflect.InvocationHandler handler) {
        return jdbcType.cast(Proxy.newProxyInstance(
                jdbcType.getClassLoader(), new Class<?>[]{jdbcType},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return jdbcType.getSimpleName() + " transaccional JDBC";
                        case "isWrapperFor":
                            return args[0] instanceof Class<?> type && type.isInstance(proxy);
                        case "unwrap":
                            Class<?> type = (Class<?>) args[0];
                            if (type != null && type.isInstance(proxy)) {
                                return type.cast(proxy);
                            }
                            throw new SQLException("El proxy transaccional no expone el objeto JDBC del controlador.");
                        default:
                            return handler.invoke(proxy, method, args);
                    }
                }));
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
