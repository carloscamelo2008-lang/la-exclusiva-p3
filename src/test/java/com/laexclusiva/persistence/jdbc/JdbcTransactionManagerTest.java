package com.laexclusiva.persistence.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

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
            return tx.inTransaction(() -> provider.withConnection(connection -> {
                assertSame(recording.connection, connection);
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
        RecordingConnection recording = new RecordingConnection();
        JdbcTransactionManager tx = new JdbcTransactionManager(providerFor(recording));

        assertThrows(JdbcPersistenceException.class, () -> tx.inTransaction((Runnable) () -> {
            try {
                tx.inTransaction((Runnable) () -> {
                    throw new IllegalStateException("fallo interno");
                });
            } catch (IllegalStateException expected) {
                // Se captura, pero la transacción exterior no debe confirmar.
            }
        }));

        assertEquals(0, recording.commits);
        assertEquals(1, recording.rollbacks);
        assertEquals(1, recording.closes);
        assertFalse(tx.isTransactionActive());
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

    private static final class RecordingConnection implements InvocationHandler {
        private final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, this);
        private boolean autoCommit = true;
        private int commits;
        private int rollbacks;
        private int closes;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "setAutoCommit" -> {
                    autoCommit = (Boolean) args[0];
                    yield null;
                }
                case "getAutoCommit" -> autoCommit;
                case "commit" -> {
                    commits++;
                    yield null;
                }
                case "rollback" -> {
                    rollbacks++;
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
