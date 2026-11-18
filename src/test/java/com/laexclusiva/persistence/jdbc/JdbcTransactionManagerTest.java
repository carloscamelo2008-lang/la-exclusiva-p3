package com.laexclusiva.persistence.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.CallableStatement;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de la unidad de trabajo con una conexión JDBC simulada. */
class JdbcTransactionManagerTest {

    private static JdbcConnectionProvider providerFor(RecordingConnection recording) {
        DatabaseConfig config = new DatabaseConfig(
                "jdbc:postgresql://localhost:5432/la_exclusiva_test", "postgres", "claveDePrueba");
        return new JdbcConnectionProvider(config, () -> recording.connection);
    }

    @Test
    void withoutTransactionOpensAndClosesConnection() {
        RecordingConnection recording = new RecordingConnection();
        JdbcConnectionProvider provider = providerFor(recording);

        String result = provider.withConnection(connection -> {
            assertSame(recording.connection, connection);
            return "consulta";
        });

        assertEquals("consulta", result);
        assertEquals(1, recording.closes);
        assertEquals(0, recording.commits);
        assertEquals(0, recording.rollbacks);
    }

    @Test
    void commitsOnceAndReusesConnectionAcrossNestedCalls() {
        RecordingConnection recording = new RecordingConnection();
        JdbcConnectionProvider provider = providerFor(recording);
        JdbcTransactionManager tx = new JdbcTransactionManager(provider);

        String result = tx.inTransaction(() -> {
            assertTrue(tx.isTransactionActive());
            assertFalse(recording.autoCommit);
            Connection outerConnection = provider.withConnection(connection -> connection);
            return tx.inTransaction(() -> provider.withConnection(connection -> {
                assertSame(outerConnection, connection);
                assertNotSame(recording.connection, connection);
                assertFalse(connection.getAutoCommit());
                assertThrows(UnsupportedOperationException.class, connection::close);
                assertThrows(UnsupportedOperationException.class, connection::commit);
                assertThrows(UnsupportedOperationException.class, connection::rollback);
                assertThrows(UnsupportedOperationException.class, () -> connection.setAutoCommit(true));
                assertEquals(0, recording.closes);
                assertEquals(0, recording.commits);
                assertEquals(0, recording.rollbacks);
                assertFalse(recording.autoCommit);
                return "correcto";
            }));
        });

        assertEquals("correcto", result);
        assertEquals(1, recording.commits);
        assertEquals(0, recording.rollbacks);
        assertEquals(1, recording.closes);
        assertFalse(tx.isTransactionActive(), "No filtrar contexto transaccional entre operaciones.");
    }

    @Test
    void protectsJdbcBackReferencesAndPreservesBatchAndGeneratedKeys() {
        RecordingConnection recording = new RecordingConnection();
        JdbcConnectionProvider provider = providerFor(recording);
        JdbcTransactionManager tx = new JdbcTransactionManager(provider);
        tx.inTransaction(() -> provider.withConnection(connection -> {
            assertRestricted(connection, Connection.class);
            try (Statement statement = connection.createStatement();
                 PreparedStatement prepared = connection.prepareStatement("INSERT", Statement.RETURN_GENERATED_KEYS);
                 CallableStatement callable = connection.prepareCall("CALL")) {
                assertRestricted(statement, Statement.class);
                assertRestricted(prepared, PreparedStatement.class);
                assertRestricted(callable, CallableStatement.class);
                assertLifecycleBlocked(connection, statement.getConnection());
                assertLifecycleBlocked(connection, prepared.getConnection());
                assertLifecycleBlocked(connection, callable.getConnection());
                try (ResultSet rows = statement.executeQuery("SELECT");
                     ResultSet current = statement.getResultSet();
                     ResultSet called = callable.executeQuery()) {
                    assertRowsProtected(connection, statement, rows);
                    assertRowsProtected(connection, statement, current);
                    assertRowsProtected(connection, callable, called);
                }
                prepared.setInt(1, 42);
                prepared.addBatch();
                assertArrayEquals(new int[]{1}, prepared.executeBatch());
                try (ResultSet keys = prepared.getGeneratedKeys()) {
                    assertRowsProtected(connection, prepared, keys);
                    assertTrue(keys.next());
                    assertEquals(42, keys.getInt(1));
                }
            }
            DatabaseMetaData metadata = connection.getMetaData();
            assertRestricted(metadata, DatabaseMetaData.class);
            assertLifecycleBlocked(connection, metadata.getConnection());
            try (ResultSet tables = metadata.getTables(null, null, null, null)) {
                assertRestricted(tables, ResultSet.class);
                assertLifecycleBlocked(connection, tables.getStatement().getConnection());
            }
            assertEquals(0, recording.commits);
            assertEquals(0, recording.rollbacks);
            assertEquals(0, recording.closes);
            assertFalse(recording.autoCommit);
            return null;
        }));
        assertEquals(1, recording.commits);
        assertEquals(1, recording.closes);
        assertEquals(5, recording.resultSetCloses);
        assertEquals(3, recording.statementCloses);
    }

    private static void assertRowsProtected(Connection connection, Statement statement, ResultSet rows)
            throws SQLException {
        assertSame(statement, rows.getStatement());
        assertRestricted(rows, ResultSet.class);
        assertLifecycleBlocked(connection, rows.getStatement().getConnection());
    }

    private static <T> void assertRestricted(Wrapper wrapper, Class<T> type) throws SQLException {
        assertTrue(wrapper.isWrapperFor(type));
        assertSame(wrapper, wrapper.unwrap(type));
        assertFalse(wrapper.isWrapperFor(DriverObject.class));
        assertThrows(SQLException.class, () -> wrapper.unwrap(DriverObject.class));
    }

    private static void assertLifecycleBlocked(Connection expected, Connection actual) {
        assertSame(expected, actual);
        assertThrows(UnsupportedOperationException.class, actual::commit);
        assertThrows(UnsupportedOperationException.class, actual::rollback);
        assertThrows(UnsupportedOperationException.class, actual::close);
        assertThrows(UnsupportedOperationException.class, () -> actual.setAutoCommit(true));
    }

    @Test
    void rollsBackOnRuntimeExceptionAndCleansContext() {
        RecordingConnection recording = new RecordingConnection();
        JdbcTransactionManager tx = new JdbcTransactionManager(providerFor(recording));
        IllegalStateException failure = new IllegalStateException("fallo");

        IllegalStateException caught = assertThrows(IllegalStateException.class,
                () -> tx.inTransaction((Runnable) () -> { throw failure; }));

        assertSame(failure, caught);
        assertEquals(0, recording.commits);
        assertEquals(1, recording.rollbacks);
        assertEquals(1, recording.closes);
        assertFalse(tx.isTransactionActive());
    }

    @Test
    void caughtNestedFailureMarksOuterTransactionRollbackOnly() {
        verifyNestedRollback(true);
    }

    @Test
    void propagatedNestedFailureRollsBackAllWrites() {
        verifyNestedRollback(false);
    }

    private static void verifyNestedRollback(boolean catchFailure) {
        RecordingConnection recording = new RecordingConnection();
        JdbcConnectionProvider provider = providerFor(recording);
        JdbcTransactionManager tx = new JdbcTransactionManager(provider);
        IllegalStateException failure = new IllegalStateException("fallo interno");

        Runnable outer = () -> tx.inTransaction(() -> {
            write(provider);
            try {
                tx.inTransaction((Runnable) () -> {
                    write(provider);
                    throw failure;
                });
            } catch (IllegalStateException expected) {
                if (!catchFailure) {
                    throw expected;
                }
                assertSame(failure, expected);
            }
            assertEquals(2, recording.pendingWrites);
        });
        if (catchFailure) {
            assertThrows(JdbcPersistenceException.class, outer::run);
        } else {
            assertSame(failure, assertThrows(IllegalStateException.class, outer::run));
        }

        assertEquals(0, recording.commits);
        assertEquals(1, recording.rollbacks);
        assertEquals(1, recording.closes);
        assertEquals(2, recording.revertedWrites);
        assertEquals(0, recording.pendingWrites);
        assertFalse(tx.isTransactionActive());
    }

    private static void write(JdbcConnectionProvider provider) {
        provider.withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                return statement.executeUpdate("INSERT");
            }
        });
    }

    @Test
    void translatesSqlErrorAndRollsBack() {
        RecordingConnection recording = new RecordingConnection();
        JdbcConnectionProvider provider = providerFor(recording);
        JdbcTransactionManager tx = new JdbcTransactionManager(provider);

        assertThrows(JdbcPersistenceException.class, () -> tx.inTransaction((Runnable) () ->
                provider.withConnection(connection -> {
                    throw new SQLException("error SQL simulado");
                })));

        assertEquals(0, recording.commits);
        assertEquals(1, recording.rollbacks);
        assertEquals(1, recording.closes);
    }

    @Test
    void rejectsNullOperations() {
        RecordingConnection recording = new RecordingConnection();
        JdbcConnectionProvider provider = providerFor(recording);
        JdbcTransactionManager tx = new JdbcTransactionManager(provider);

        assertThrows(IllegalArgumentException.class, () -> tx.inTransaction((Runnable) null));
        assertThrows(IllegalArgumentException.class, () -> provider.withConnection(null));
        assertThrows(IllegalArgumentException.class, () -> new JdbcConnectionProvider(null));
    }

    public interface DriverObject { }

    private static final class RecordingConnection implements InvocationHandler {
        private final Connection connection = driverProxy(Connection.class, this);
        private boolean autoCommit = true;
        private int commits;
        private int rollbacks;
        private int closes;
        private int statementCloses;
        private int resultSetCloses;
        private int pendingWrites;
        private int revertedWrites;
        private int parameter;
        private int batchedParameter;

        private static <T> T driverProxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(JdbcTransactionManagerTest.class.getClassLoader(),
                    new Class<?>[]{type, DriverObject.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "unwrap" -> ((Class<?>) args[0]).cast(proxy);
                        case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
                        default -> handler.invoke(proxy, method, args);
                    }));
        }

        private Statement statement(Class<? extends Statement> type) {
            return driverProxy(type, (proxy, method, args) -> switch (method.getName()) {
                case "getConnection" -> connection;
                case "executeQuery", "getResultSet" -> rows((Statement) proxy, 0);
                case "getGeneratedKeys" -> rows((Statement) proxy, batchedParameter);
                case "setInt" -> {
                    parameter = (Integer) args[1];
                    yield null;
                }
                case "addBatch" -> {
                    batchedParameter = parameter;
                    yield null;
                }
                case "executeBatch" -> new int[]{1};
                case "executeUpdate" -> {
                    pendingWrites++;
                    yield 1;
                }
                case "close" -> {
                    statementCloses++;
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private ResultSet rows(Statement statement, int value) {
            return driverProxy(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
                case "getStatement" -> statement;
                case "next" -> true;
                case "getInt" -> value;
                case "close" -> {
                    resultSetCloses++;
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "setAutoCommit" -> {
                    autoCommit = (Boolean) args[0];
                    yield null;
                }
                case "getAutoCommit" -> autoCommit;
                case "createStatement" -> statement(Statement.class);
                case "prepareStatement" -> statement(PreparedStatement.class);
                case "prepareCall" -> statement(CallableStatement.class);
                case "getMetaData" -> driverProxy(DatabaseMetaData.class, (metadata, operation, values) ->
                        switch (operation.getName()) {
                            case "getConnection" -> connection;
                            case "getTables" -> rows(statement(Statement.class), 0);
                            default -> throw new UnsupportedOperationException(operation.getName());
                        });
                case "commit" -> {
                    commits++;
                    yield null;
                }
                case "rollback" -> {
                    rollbacks++;
                    revertedWrites += pendingWrites;
                    pendingWrites = 0;
                    yield null;
                }
                case "close" -> {
                    closes++;
                    yield null;
                }
                case "isClosed" -> closes > 0;
                case "toString" -> "RecordingConnection";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(
                        "Método no usado por esta prueba: " + method.getName());
            };
        }
    }
}
