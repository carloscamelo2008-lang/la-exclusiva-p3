package com.laexclusiva.persistence.jdbc;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** JDBC simulado: estas pruebas no abren conexiones reales ni prueban bloqueos de PostgreSQL. */
class JdbcUserAdministrationBoundaryTest {
    @Test
    void rejectsNullProvider() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcUserAdministrationBoundary(null));
    }

    @Test
    void constructionAndNullOperationDoNotOpenConnections() {
        var jdbc = new RecordingJdbc();
        var boundary = new JdbcUserAdministrationBoundary(jdbc.provider);
        assertEquals(0, jdbc.opens);
        assertThrows(IllegalArgumentException.class, () -> boundary.execute(null));
        assertEquals(0, jdbc.opens);
    }

    @Test
    void beginsLocksClosesOwnResourcesRunsOperationAndCommitsOnTheSameConnection() {
        var jdbc = new RecordingJdbc();
        var boundary = new JdbcUserAdministrationBoundary(jdbc.provider);
        assertEquals("result", boundary.execute(() -> jdbc.provider.withConnection(connection -> {
            assertFalse(connection.getAutoCommit());
            assertTrue(jdbc.locked);
            assertEquals(1, jdbc.opens);
            jdbc.events.add("operation");
            try (PreparedStatement statement = connection.prepareStatement("SELECT protected_operation")) {
                assertSame(connection, statement.getConnection());
            }
            return "result";
        })));
        assertEquals(List.of("begin", "lock", "rows.close", "statement.close", "operation",
                "statement.close", "commit", "connection.close"), jdbc.events);
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertEquals(10, jdbc.timeout);
    }

    @ParameterizedTest
    @ValueSource(ints = {Connection.TRANSACTION_NONE, Connection.TRANSACTION_READ_UNCOMMITTED,
            Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE})
    void rejectsIncompatibleIsolationBeforeLockOrOperation(int isolation) {
        var jdbc = new RecordingJdbc();
        jdbc.isolation = isolation;
        assertThrows(IllegalStateException.class, () -> new JdbcUserAdministrationBoundary(jdbc.provider)
                .execute(() -> fail("No ejecutar")));
        assertFalse(jdbc.locked);
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
    }

    @Test
    void rejectsReadOnlyTransactions() {
        var jdbc = new RecordingJdbc();
        jdbc.readOnly = true;
        assertThrows(IllegalStateException.class, () -> new JdbcUserAdministrationBoundary(jdbc.provider)
                .execute(() -> fail("No ejecutar")));
        assertFalse(jdbc.locked);
        assertEquals(1, jdbc.rollbacks);
    }

    @Test
    void joinsAnExistingTransactionWithoutAnIndependentConnectionOrEarlyCommit() {
        var jdbc = new RecordingJdbc();
        var tx = new JdbcTransactionManager(jdbc.provider);
        var boundary = new JdbcUserAdministrationBoundary(jdbc.provider);
        tx.inTransaction(() -> {
            Connection outer = jdbc.provider.withConnection(connection -> connection);
            int result = boundary.execute(() -> jdbc.provider.withConnection(connection -> {
                assertSame(outer, connection);
                assertEquals(0, jdbc.commits);
                return 4;
            }));
            assertEquals(4, result);
            assertEquals(0, jdbc.commits);
            assertEquals(1, jdbc.opens);
        });
        assertEquals(1, jdbc.commits);
    }

    @Test
    void caughtNestedOperationFailureStillMarksOuterTransactionRollbackOnly() {
        var jdbc = new RecordingJdbc();
        var tx = new JdbcTransactionManager(jdbc.provider);
        var boundary = new JdbcUserAdministrationBoundary(jdbc.provider);
        var failure = new IllegalArgumentException("expected");
        assertThrows(JdbcPersistenceException.class, () -> tx.inTransaction(() -> {
            assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> boundary.execute(() -> { throw failure; })));
        }));
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
    }

    @Test
    void propagatesOperationFailureAndRollsBack() {
        var jdbc = new RecordingJdbc();
        var failure = new IllegalStateException("expected");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new JdbcUserAdministrationBoundary(jdbc.provider).execute(() -> { throw failure; })));
        assertEquals(1, jdbc.rollbacks);
        assertEquals(1, jdbc.closes);
        assertEquals(0, jdbc.commits);
    }

    @Test
    void lockFailurePreventsOperationAndRollsBackEvenWhenCaughtByOuterCaller() {
        var jdbc = new RecordingJdbc();
        jdbc.failLock = true;
        var tx = new JdbcTransactionManager(jdbc.provider);
        var called = new AtomicBoolean();
        assertThrows(JdbcPersistenceException.class, () -> tx.inTransaction(() -> {
            assertThrows(JdbcPersistenceException.class,
                    () -> new JdbcUserAdministrationBoundary(jdbc.provider).execute(() -> {
                        called.set(true);
                        return null;
                    }));
        }));
        assertFalse(called.get());
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
        assertTrue(jdbc.events.contains("statement.close"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void unexpectedLockResultPreventsOperation(int rows) {
        var jdbc = new RecordingJdbc();
        jdbc.rows = rows;
        assertThrows(JdbcPersistenceException.class, () -> new JdbcUserAdministrationBoundary(jdbc.provider)
                .execute(() -> fail("No ejecutar")));
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
        assertTrue(jdbc.events.contains("rows.close"));
    }

    @Test
    void failedResourceClosurePreventsOperation() {
        var jdbc = new RecordingJdbc();
        jdbc.failStatementClose = true;
        assertThrows(JdbcPersistenceException.class, () -> new JdbcUserAdministrationBoundary(jdbc.provider)
                .execute(() -> fail("No ejecutar")));
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
    }

    @Test
    void unexpectedSqlFailureDoesNotExposeRawServerDetails() {
        var jdbc = new RecordingJdbc();
        jdbc.failLock = true;
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class,
                () -> new JdbcUserAdministrationBoundary(jdbc.provider).execute(() -> fail("No ejecutar")));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertTrue(failure.getMessage().contains("55P03"));
        assertFalse(failure.getMessage().contains("sensitive-server-fixture"));
        assertEquals(1, jdbc.rollbacks);
    }

    @Test
    void rollbackFailureDoesNotLeakSuppressedSqlDetails() {
        var jdbc = new RecordingJdbc();
        jdbc.failRollback = true;
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class,
                () -> new JdbcUserAdministrationBoundary(jdbc.provider).execute(() -> {
                    throw new IllegalStateException("expected domain failure");
                }));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertTrue(failure.getMessage().contains("08006"));
        assertFalse(failure.getMessage().contains("sensitive-server-fixture"));
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.closes);
    }

    @Test
    void failedCloseAfterCommitIsSanitizedWithoutRepeatingTheOperation() {
        var jdbc = new RecordingJdbc();
        jdbc.failConnectionClose = true;
        int[] calls = {0};
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class,
                () -> new JdbcUserAdministrationBoundary(jdbc.provider).execute(() -> ++calls[0]));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertTrue(failure.getMessage().contains("08006"));
        assertEquals(1, calls[0]);
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
    }

    private static final class RecordingJdbc {
        final List<String> events = new ArrayList<>();
        final Connection connection;
        final JdbcConnectionProvider provider;
        int opens, commits, rollbacks, closes, timeout;
        int isolation = Connection.TRANSACTION_READ_COMMITTED;
        int rows = 1;
        boolean autoCommit = true, readOnly, locked, failLock, failStatementClose;
        boolean failRollback, failConnectionClose;

        RecordingJdbc() {
            connection = proxy(Connection.class, (method, args) -> switch (method) {
                case "setAutoCommit" -> { autoCommit = (boolean) args[0]; events.add("begin"); yield null; }
                case "getAutoCommit" -> autoCommit;
                case "getTransactionIsolation" -> isolation;
                case "isReadOnly" -> readOnly;
                case "prepareStatement" -> statement((String) args[0]);
                case "commit" -> { commits++; events.add("commit"); yield null; }
                case "rollback" -> {
                    rollbacks++;
                    events.add("rollback");
                    if (failRollback) throw new SQLException("sensitive-server-fixture", "08006");
                    yield null;
                }
                case "close" -> {
                    closes++;
                    events.add("connection.close");
                    if (failConnectionClose) throw new SQLException("sensitive-server-fixture", "08006");
                    yield null;
                }
                default -> throw new AssertionError("Método de conexión inesperado: " + method);
            });
            provider = new JdbcConnectionProvider(new DatabaseConfig(
                    "jdbc:postgresql://localhost:5432/la_exclusiva_test", "fixture", "fictional"), () -> {
                        opens++;
                        return connection;
                    });
        }

        PreparedStatement statement(String sql) {
            assertTrue(sql.equals("SELECT pg_advisory_xact_lock(1279346008, 1)")
                    || sql.equals("SELECT protected_operation"));
            assertFalse(autoCommit);
            return proxy(PreparedStatement.class, (method, args) -> switch (method) {
                case "getConnection" -> connection;
                case "setQueryTimeout" -> { timeout = (int) args[0]; yield null; }
                case "executeQuery" -> {
                    assertEquals("SELECT pg_advisory_xact_lock(1279346008, 1)", sql);
                    events.add("lock");
                    if (failLock) throw new SQLException("sensitive-server-fixture", "55P03");
                    locked = true;
                    int[] cursor = {0};
                    yield proxy(ResultSet.class, (name, values) -> switch (name) {
                        case "next" -> ++cursor[0] <= rows;
                        case "close" -> { events.add("rows.close"); yield null; }
                        default -> throw new AssertionError("Método de resultado inesperado: " + name);
                    });
                }
                case "close" -> {
                    events.add("statement.close");
                    if (failStatementClose) throw new SQLException("simulated close", "08006");
                    yield null;
                }
                default -> throw new AssertionError("Método de sentencia inesperado: " + method);
            });
        }
    }

    @FunctionalInterface
    private interface Call { Object invoke(String method, Object[] args) throws Throwable; }

    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> call.invoke(method.getName(), args)));
    }
}
