package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PostgresStartupVerifierTest {
    @Test
    void acceptsCompleteV112SchemaAndClosesConnection() {
        Fixture fixture = new Fixture();
        assertDoesNotThrow(fixture.verifier()::verify);
        assertEquals(true, fixture.closed.get());
    }

    @Test
    void rejectsEmptySchema() {
        Fixture fixture = new Fixture();
        fixture.columns.clear();
        assertFailure(fixture.verifier(), PostgresStartupVerifier.Failure.INCOMPATIBLE_SCHEMA);
    }

    @Test
    void rejectsSchemaWithoutMigrationHistory() {
        Fixture fixture = new Fixture();
        fixture.columns.remove("schema_migration");
        assertFailure(fixture.verifier(), PostgresStartupVerifier.Failure.INCOMPATIBLE_SCHEMA);
    }

    @Test
    void rejectsVersionBelowV112() {
        Fixture fixture = new Fixture();
        fixture.versionCompatible = false;
        assertFailure(fixture.verifier(), PostgresStartupVerifier.Failure.INCOMPATIBLE_SCHEMA);
    }

    @Test
    void rejectsMissingCriticalColumn() {
        Fixture fixture = new Fixture();
        fixture.columns.put("customer", "id");
        assertFailure(fixture.verifier(), PostgresStartupVerifier.Failure.INCOMPATIBLE_SCHEMA);
    }

    @Test
    void rejectsMissingTablePrivileges() {
        Fixture fixture = new Fixture();
        fixture.denied = Set.of("sale");
        assertFailure(fixture.verifier(), PostgresStartupVerifier.Failure.INSUFFICIENT_PRIVILEGES);
    }

    @Test
    void rejectsUnreadableMigrationHistory() {
        Fixture fixture = new Fixture();
        fixture.historyReadable = false;
        assertFailure(fixture.verifier(), PostgresStartupVerifier.Failure.INSUFFICIENT_PRIVILEGES);
    }

    @Test
    void rejectsInvalidConnectionAndClosesIt() {
        Fixture fixture = new Fixture();
        fixture.valid = false;
        assertFailure(fixture.verifier(), PostgresStartupVerifier.Failure.CONNECTION_FAILED);
        assertEquals(true, fixture.closed.get());
    }

    @Test
    void sanitizesFailureToOpenConnection() {
        var verifier = verifier(() -> {
            throw new SQLException("Mensaje interno del controlador", "08001");
        });
        assertFailure(verifier, PostgresStartupVerifier.Failure.CONNECTION_FAILED);
    }

    private static void assertFailure(PostgresStartupVerifier verifier, PostgresStartupVerifier.Failure expected) {
        var failure = assertThrows(PostgresStartupVerifier.VerificationException.class, verifier::verify);
        assertEquals(expected, failure.getFailure());
        assertNull(failure.getCause());
    }

    private static PostgresStartupVerifier verifier(JdbcConnectionProvider.ConnectionFactory connection) {
        var config = new DatabaseConfig("jdbc:postgresql://localhost/la_exclusiva_test", "test", "unused");
        return new PostgresStartupVerifier(new JdbcConnectionProvider(config, connection));
    }

    private static final class Fixture {
        private final Map<String, String> columns = new LinkedHashMap<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private Set<String> denied = Set.of();
        private boolean valid = true;
        private boolean versionCompatible = true;
        private boolean historyReadable = true;

        private Fixture() {
            for (String table : new String[]{"user_account", "beer_product", "product_price", "inventory_item",
                    "sale", "sale_line", "cash_movement", "supplier", "purchase_order", "purchase_order_line",
                    "goods_receipt", "goods_receipt_line"}) {
                columns.put(table, "id");
            }
            columns.put("schema_migration", "version");
            columns.put("seller", "active");
            columns.put("customer", "credit_limit");
            columns.put("sale_payment", "received_by_user_id");
            columns.put("customer_general_debt_movement", "received_by_user_id");
            columns.put("stock_movement", "actor_user_id");
        }

        private PostgresStartupVerifier verifier() {
            return PostgresStartupVerifierTest.verifier(() -> proxy(Connection.class, (ignored, method, args) ->
                    switch (method.getName()) {
                        case "isValid" -> {
                            assertEquals(5, args[0]);
                            yield valid;
                        }
                        case "createStatement" -> statement();
                        case "close" -> {
                            closed.set(true);
                            yield null;
                        }
                        default -> throw new AssertionError(method);
                    }));
        }

        private Statement statement() {
            return proxy(Statement.class, (ignored, method, args) -> switch (method.getName()) {
                case "close", "setQueryTimeout" -> null;
                case "executeQuery" -> {
                    String sql = (String) args[0];
                    if (sql.contains("FROM pg_catalog.pg_class")) {
                        yield rows(columns.entrySet().stream()
                                .map(entry -> new Object[]{entry.getKey(), entry.getValue(),
                                        !denied.contains(entry.getKey())}).toArray(Object[][]::new));
                    }
                    if (!sql.contains("FROM public.schema_migration")) {
                        throw new AssertionError(sql);
                    }
                    if (!historyReadable) {
                        throw new SQLException("Detalle interno", "42501");
                    }
                    yield rows(new Object[][]{{versionCompatible}});
                }
                default -> throw new AssertionError(method);
            });
        }

        private ResultSet rows(Object[][] values) {
            return proxy(ResultSet.class, new InvocationHandler() {
                private int row = -1;

                @Override
                public Object invoke(Object ignored, java.lang.reflect.Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "next" -> ++row < values.length;
                        case "getString", "getBoolean" -> values[row][(int) args[0] - 1];
                        case "close" -> null;
                        default -> throw new AssertionError(method);
                    };
                }
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
