package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.ui.IdentityServices;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityServicesJdbcCompositionTest {
    // Configuración ficticia: las fábricas inyectadas nunca llaman al controlador PostgreSQL.
    private static final DatabaseConfig CONFIG = new DatabaseConfig(
            "jdbc:postgresql://unused.invalid/la_exclusiva_test", "test-only", "test-only");

    @Test
    void rejectsANullProvider() {
        assertThrows(IllegalArgumentException.class, () -> IdentityServices.postgres(null));
    }

    @Test
    void jdbcConstructionAndAccessorsDoNotOpenConnections() {
        JdbcConnectionProvider provider = new JdbcConnectionProvider(CONFIG,
                () -> { throw new AssertionError("La construcción no debe abrir conexiones."); });
        IdentityServices services = IdentityServices.postgres(provider);

        assertInstanceOf(UserRepositoryJdbc.class, services.users());
        assertSame(services.users(), services.users());
        assertSame(services.authentication(), services.authentication());
        assertNotNull(services.authentication());
    }

    @Test
    void authenticationQueriesTheSuppliedProviderAndClosesResources() {
        EmptyJdbc jdbc = new EmptyJdbc();
        IdentityServices services = IdentityServices.postgres(jdbc.provider());

        assertTrue(services.authentication().createSession("missing", "Composition-Test-Only").isEmpty());
        assertEquals(1, jdbc.opened);
        assertEquals(1, jdbc.closedConnections);
        assertEquals(1, jdbc.closedStatements);
        assertEquals(1, jdbc.closedRows);
    }

    @Test
    void repositoryJoinsTheTransactionOfTheSuppliedProvider() {
        EmptyJdbc jdbc = new EmptyJdbc();
        JdbcConnectionProvider provider = jdbc.provider();
        IdentityServices services = IdentityServices.postgres(provider);

        new JdbcTransactionManager(provider).inTransaction(() -> {
            assertTrue(services.users().findAll().isEmpty());
            assertTrue(services.users().findAll().isEmpty());
            assertEquals(1, jdbc.opened);
            assertEquals(0, jdbc.closedConnections);
            assertEquals(0, jdbc.commits);
        });
        assertEquals(1, jdbc.opened);
        assertEquals(1, jdbc.commits);
        assertEquals(1, jdbc.closedConnections);
        assertEquals(2, jdbc.closedStatements);
        assertEquals(2, jdbc.closedRows);
    }

    @Test
    void jdbcFailuresPropagateWithoutSwitchingToMemory() {
        int[] attempts = {0};
        JdbcConnectionProvider provider = new JdbcConnectionProvider(CONFIG, () -> {
            attempts[0]++;
            throw new SQLException("Fallo de conexión simulado", "08001");
        });
        IdentityServices services = IdentityServices.postgres(provider);

        assertThrows(JdbcPersistenceException.class,
                () -> services.authentication().createSession("member", "Composition-Test-Only"));
        assertThrows(JdbcPersistenceException.class, () -> services.users().findAll());
        assertEquals(2, attempts[0]);
        assertInstanceOf(UserRepositoryJdbc.class, services.users());
    }

    /** Simula respuestas vacías y el ciclo de recursos JDBC, sin interpretar SQL ni abrir sockets. */
    private static final class EmptyJdbc {
        int opened;
        int closedConnections;
        int closedStatements;
        int closedRows;
        int commits;

        JdbcConnectionProvider provider() {
            return new JdbcConnectionProvider(CONFIG, () -> {
                opened++;
                return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> statement();
                    case "setAutoCommit" -> null;
                    case "commit" -> { commits++; yield null; }
                    case "close" -> { closedConnections++; yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            });
        }

        private PreparedStatement statement() {
            return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
                case "setString" -> null;
                case "executeQuery" -> proxy(ResultSet.class, (row, rowMethod, rowArgs) -> switch (rowMethod.getName()) {
                    case "next" -> false;
                    case "close" -> { closedRows++; yield null; }
                    default -> throw new UnsupportedOperationException(rowMethod.getName());
                });
                case "close" -> { closedStatements++; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}
