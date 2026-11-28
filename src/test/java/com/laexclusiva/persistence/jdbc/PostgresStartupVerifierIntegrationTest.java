package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresStartupVerifierIntegrationTest {
    @Test
    void acceptsNonSuperuserWithExactMinimumGrantsThroughV112() throws SQLException {
        DatabaseConfig config = testConfig();
        try (Connection connection = new JdbcConnectionProvider(config).openConnection()) {
            useApplicationRole(connection, true);
            assertExactTableGrants(connection);
            try (var statement = connection.createStatement()) {
                // Comprueba que el permiso de columna basta para el bloqueo usado por el repositorio.
                statement.executeQuery("SELECT id FROM public.sale WHERE false FOR UPDATE").close();
            }
            assertDoesNotThrow(new PostgresStartupVerifier(
                    new JdbcConnectionProvider(config, () -> connection))::verify);
        }
    }

    @Test
    void rejectsNonSuperuserWithoutInsertOnSalePayment() throws SQLException {
        DatabaseConfig config = testConfig();
        try (Connection connection = new JdbcConnectionProvider(config).openConnection()) {
            useApplicationRole(connection, false);
            var verifier = new PostgresStartupVerifier(new JdbcConnectionProvider(config, () -> connection));
            var failure = assertThrows(PostgresStartupVerifier.VerificationException.class, verifier::verify);
            assertEquals(PostgresStartupVerifier.Failure.INSUFFICIENT_PRIVILEGES, failure.getFailure());
            assertTrue(failure.getMessage().contains("INSERT sobre public.sale_payment"));
        }
    }

    private static DatabaseConfig testConfig() {
        assumeTrue(Boolean.getBoolean("laexclusiva.test.postgres.commercialDeployment"),
                "Esta verificación requiere una base aislada preparada con el instalador comercial.");
        String url = System.getenv(DatabaseConfig.TEST_URL_ENV);
        assumeTrue(url != null && !url.isBlank(), "Configure la base aislada la_exclusiva_test.");
        return DatabaseConfig.fromTestEnvironment();
    }

    private static void useApplicationRole(Connection connection, boolean grantPaymentInsert) throws SQLException {
        // CREATE ROLE, GRANT y SET LOCAL ROLE se revierten al cerrar sin confirmar.
        connection.setAutoCommit(false);
        String role = "startup_verifier_" + UUID.randomUUID().toString().replace("-", "");
        try (var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT current_database()")) {
                assertTrue(rows.next());
                assertEquals("la_exclusiva_test", rows.getString(1));
            }
            statement.execute("CREATE ROLE " + role + " NOLOGIN NOSUPERUSER NOINHERIT NOCREATEDB NOCREATEROLE");
            statement.execute("GRANT " + role + " TO CURRENT_USER");
            statement.execute("GRANT USAGE ON SCHEMA public TO " + role);
            for (var entry : PostgresStartupVerifierTest.MINIMUM_GRANTS.entrySet()) {
                String grants = !grantPaymentInsert && entry.getKey().equals("sale_payment")
                        ? "SELECT" : String.join(", ", entry.getValue());
                statement.execute("GRANT " + grants + " ON public." + entry.getKey() + " TO " + role);
            }
            statement.execute("SET LOCAL ROLE " + role);
            try (var rows = statement.executeQuery(
                    "SELECT rolname, rolsuper FROM pg_catalog.pg_roles WHERE rolname = current_user")) {
                assertTrue(rows.next());
                assertEquals(role, rows.getString(1));
                assertFalse(rows.getBoolean(2));
            }
        }
    }

    private static void assertExactTableGrants(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT has_table_privilege(current_user, ?, ?)")) {
            for (var entry : PostgresStartupVerifierTest.MINIMUM_GRANTS.entrySet()) {
                statement.setString(1, "public." + entry.getKey());
                for (String permission : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
                    statement.setString(2, permission);
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(entry.getValue().contains(permission), rows.getBoolean(1),
                                permission + " sobre public." + entry.getKey());
                    }
                }
            }
        }
    }
}
