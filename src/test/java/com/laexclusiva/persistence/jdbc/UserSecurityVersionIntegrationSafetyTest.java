package com.laexclusiva.persistence.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica las barreras de seguridad con JDBC simulado; nunca utiliza DriverManager. */
class UserSecurityVersionIntegrationSafetyTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:postgresql://localhost/la_exclusiva_test",
        "jdbc:postgresql://localhost:5432/la_exclusiva_test",
        "jdbc:postgresql://127.0.0.1:5432/la_exclusiva_test",
        "jdbc:postgresql://[::1]:5432/la_exclusiva_test"
    })
    void permitsOnlyExplicitLocalTestDestinations(String url) {
        assertDoesNotThrow(() -> UserSecurityVersionPostgresIntegrationTest.validateLocalUrl(url));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "jdbc:postgresql://localhost:5432/la_exclusiva",
        "jdbc:postgresql://remote.example:5432/la_exclusiva_test",
        "jdbc:postgresql://localhost:5433/la_exclusiva_test",
        "jdbc:postgresql://localhost:5432/la_exclusiva_test?options=-csearch_path=other",
        "jdbc:postgresql://localhost:5432,remote.example:5432/la_exclusiva_test",
        "jdbc:postgresql://someone@localhost:5432/la_exclusiva_test",
        "jdbc:postgresql:la_exclusiva_test",
        "jdbc:postgresql://localhost:5432/la_exclusiva_test/other",
        "jdbc:postgresql://localhost:5432/LA_EXCLUSIVA_TEST"
    })
    void rejectsAlternativeDatabasesPortsHostsAndUrlOverrides(String url) {
        assertThrows(IllegalArgumentException.class,
                () -> UserSecurityVersionPostgresIntegrationTest.validateLocalUrl(url));
    }

    @Test
    void validConnectionChecksDestinationAndSchemaWithoutDml() throws Exception {
        FakeConnection jdbc = new FakeConnection();
        UserSecurityVersionPostgresIntegrationTest.verifyConnection(jdbc.connection());
        assertEquals(2, jdbc.queries.size());
        assertTrue(jdbc.queries.get(0).contains("current_database()"));
        assertTrue(jdbc.queries.get(1).contains("trg_user_account_security_version"));
        assertTrue(jdbc.queries.get(1).contains("pg_get_expr(ck.conbin, ck.conrelid, false)"));
        assertTrue(jdbc.queries.get(1).contains("ck.conkey = ARRAY[a.attnum]"));
        assertTrue(jdbc.queries.get(1).contains("t.tgqual IS NULL"));
        assertTrue(jdbc.queries.get(1).contains("t.tgattr::text"));
        assertEquals(List.of("SET search_path = pg_catalog, public", "SET statement_timeout = '15s'",
                "SET lock_timeout = '10s'"), jdbc.settings);
        assertEquals(2, jdbc.closedStatements);
        assertEquals(2, jdbc.closedRows);
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "address", "port", "version", "replication"})
    void rejectsUnsafePhysicalConnectionsBeforeChangingAnySessionSettings(String mismatch) {
        FakeConnection jdbc = new FakeConnection();
        switch (mismatch) {
            case "database" -> jdbc.database = "la_exclusiva";
            case "address" -> jdbc.address = "192.0.2.1";
            case "port" -> jdbc.port = 5433;
            case "version" -> jdbc.version = 17;
            default -> jdbc.origin = false;
        }
        assertThrows(SQLException.class,
                () -> UserSecurityVersionPostgresIntegrationTest.verifyConnection(jdbc.connection()));
        assertEquals(1, jdbc.queries.size());
        assertTrue(jdbc.settings.isEmpty());
        assertEquals(1, jdbc.closedStatements);
        assertEquals(1, jdbc.closedRows);
    }

    @Test
    void rejectsMissingV104WithoutRunningFixtureWrites() {
        FakeConnection jdbc = new FakeConnection();
        jdbc.schema.put("column_ready", false);
        assertThrows(SQLException.class,
                () -> UserSecurityVersionPostgresIntegrationTest.verifyConnection(jdbc.connection()));
        assertEquals(2, jdbc.queries.size());
        assertTrue(jdbc.settings.stream().allMatch(sql -> sql.startsWith("SET ")));
        assertEquals(2, jdbc.closedStatements);
        assertEquals(2, jdbc.closedRows);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "(security_version >= 0)", "security_version >= 0", "((security_version >= 0))",
        "(security_version >= (0)::bigint)", "(security_version >= 0::bigint)"
    })
    void acceptsOnlyExpectedCanonicalCheckForms(String expression) throws Exception {
        FakeConnection jdbc = new FakeConnection();
        jdbc.schema.put("check_expression", expression);
        UserSecurityVersionPostgresIntegrationTest.verifyConnection(jdbc.connection());
        assertEquals(2, jdbc.queries.size());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "true", "(TRUE)", "(active = TRUE)", "(security_version >= -1)",
        "(security_version >= 0 OR TRUE)", "(security_version IS NOT NULL)",
        "(security_version >= 1)", "(security_version <= 0)", "(security_version + 1 >= 0)",
        "security_(version) >= 0", "(security_version >= 0) OR (active)",
        "(security_version >= 0 /* comentario */)"
    })
    void rejectsTrivialUnrelatedOrWeakerChecksEvenWithTheCorrectName(String expression) {
        FakeConnection jdbc = new FakeConnection();
        jdbc.schema.put("check_expression", expression);
        assertRejectedBeforeFixtureWrites(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "wrong-table", "missing-column", "wrong-check-column", "when", "update-of",
        "insert-only", "update-only", "statement-trigger", "after-trigger", "disabled",
        "wrong-function", "unsafe-function", "changed-function-body", "missing-function-body"
    })
    void rejectsEachIncompleteProtectionIndependently(String mismatch) {
        FakeConnection jdbc = new FakeConnection();
        switch (mismatch) {
            case "wrong-table" -> jdbc.schema.put("tables_ready", false);
            case "missing-column" -> jdbc.schema.put("column_ready", false);
            case "wrong-check-column" -> jdbc.schema.put("check_ready", false);
            case "when" -> jdbc.schema.put("trigger_unconditional", false);
            case "update-of" -> jdbc.schema.put("trigger_columns", "4");
            case "insert-only" -> jdbc.schema.put("trigger_type", 7);
            case "update-only" -> jdbc.schema.put("trigger_type", 19);
            case "statement-trigger" -> jdbc.schema.put("trigger_type", 22);
            case "after-trigger" -> jdbc.schema.put("trigger_type", 21);
            case "disabled" -> jdbc.schema.put("trigger_enabled", "D");
            case "wrong-function" -> jdbc.schema.put("trigger_function_ready", false);
            case "unsafe-function" -> jdbc.schema.put("function_ready", false);
            case "changed-function-body" -> jdbc.schema.put("function_body", "BEGIN RETURN NEW; END;");
            default -> jdbc.schema.put("function_body", null);
        }
        assertRejectedBeforeFixtureWrites(jdbc);
    }

    @Test
    void acceptsTheExpectedFunctionBodyWithWindowsLineEndings() throws Exception {
        FakeConnection jdbc = new FakeConnection();
        jdbc.schema.put("function_body", ((String) jdbc.schema.get("function_body")).replace("\n", "\r\n"));
        assertDoesNotThrow(() -> UserSecurityVersionPostgresIntegrationTest.verifyConnection(jdbc.connection()));
    }

    private static void assertRejectedBeforeFixtureWrites(FakeConnection jdbc) {
        SQLException failure = assertThrows(SQLException.class,
                () -> UserSecurityVersionPostgresIntegrationTest.verifyConnection(jdbc.connection()));
        assertEquals("55000", failure.getSQLState());
        assertEquals(2, jdbc.queries.size());
        assertTrue(jdbc.queries.stream().allMatch(sql -> sql.stripLeading().startsWith("SELECT")));
        assertTrue(jdbc.settings.stream().allMatch(sql -> sql.startsWith("SET ")));
        assertEquals(2, jdbc.closedStatements);
        assertEquals(2, jdbc.closedRows);
    }

    private static final class FakeConnection {
        String database = "la_exclusiva_test";
        String address = "127.0.0.1";
        int port = 5432;
        int version = 18;
        boolean origin = true;
        final Map<String, Object> schema = expectedSchema();
        int closedStatements;
        int closedRows;
        final List<String> queries = new ArrayList<>();
        final List<String> settings = new ArrayList<>();

        private static Map<String, Object> expectedSchema() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("tables_ready", true);
            schema.put("column_ready", true);
            schema.put("check_ready", true);
            schema.put("check_expression", "(security_version >= 0)");
            schema.put("trigger_type", 23);
            schema.put("trigger_enabled", "O");
            schema.put("trigger_unconditional", true);
            schema.put("trigger_columns", "");
            schema.put("trigger_function_ready", true);
            schema.put("function_ready", true);
            try {
                // El fixture positivo representa el cuerpo aprobado, no una implementación alternativa del trigger.
                String migration = Files.readString(Path.of("database/migrations/V104__user_security_version.sql"));
                schema.put("function_body", migration.split("\\$function\\$", -1)[1].replace("\r\n", "\n"));
            } catch (IOException failure) {
                throw new IllegalStateException("No se pudo preparar el fixture de catálogo de V104.");
            }
            return schema;
        }

        Connection connection() {
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> {
                    String sql = (String) args[0];
                    assertTrue(sql.stripLeading().startsWith("SELECT"));
                    yield statement(sql);
                }
                case "createStatement" -> proxy(Statement.class, (object2, method2, args2) -> switch (method2.getName()) {
                    case "execute" -> {
                        String sql = (String) args2[0];
                        assertTrue(sql.startsWith("SET "));
                        settings.add(sql);
                        yield false;
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method2.getName());
                });
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        PreparedStatement statement(String sql) {
            return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
                case "setQueryTimeout" -> null;
                case "executeQuery" -> {
                    queries.add(sql);
                    yield rows(sql.contains("current_database()"));
                }
                case "close" -> { closedStatements++; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        ResultSet rows(boolean destination) {
            boolean[] visited = {false};
            return proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
                case "next" -> { boolean next = !visited[0]; visited[0] = true; yield next; }
                case "getString" -> destination ? ((int) args[0] == 1 ? database : address) : schema.get(args[0]);
                case "getInt" -> destination ? ((int) args[0] == 3 ? port : version)
                        : ((Number) schema.get(args[0])).intValue();
                case "getBoolean" -> destination ? origin : Boolean.TRUE.equals(schema.get(args[0]));
                case "close" -> { closedRows++; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}
