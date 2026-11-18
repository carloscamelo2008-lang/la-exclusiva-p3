package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Comprueba en PostgreSQL las restricciones de identidad idempotente definidas por V100. */
class SaleRequestIdentityPostgresIntegrationTest {
    private JdbcConnectionProvider connections;

    @BeforeEach
    void connectOnlyToDedicatedTestDatabase() throws Exception {
        String testUrl = System.getenv(DatabaseConfig.TEST_URL_ENV);
        Assumptions.assumeTrue(testUrl != null && !testUrl.isBlank(),
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        connections = new JdbcConnectionProvider(DatabaseConfig.fromTestEnvironment());
        String database = connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT current_database()")) {
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("No se obtuvo la base de datos conectada.");
                    return rows.getString(1);
                }
            }
        });
        Assumptions.assumeTrue("la_exclusiva_test".equals(database),
                "Omitida: la prueba solo puede usar la_exclusiva_test.");
    }

    @Test
    void v100IdentityConstraintsAcceptLegacyRowsAndRejectInvalidIdentityPairsInPostgres() throws Exception {
        String migration = Files.readString(Path.of("database/migrations/V100__sale_creation_idempotency.sql"));
        String upgrade = Files.readString(
                Path.of("database/migrations/V103__enforce_sale_request_identity_coherence.sql"));
        assertTrue(migration.contains("uq_sale_idempotency_key"), "V100 debe mantener unicidad de la clave.");
        assertTrue(migration.contains("request_fingerprint IS NOT NULL"),
                "V100 debe rechazar una clave sin huella explícita.");
        assertTrue(migration.contains("request_fingerprint ~ '^[0-9a-f]{64}$'"),
                "V100 debe limitar las huellas a 64 caracteres hexadecimales en minúscula.");
        assertTrue(upgrade.contains("request_fingerprint IS NOT NULL"),
                "V103 debe corregir el esquema donde V100 ya se aplicó.");
        assertTrue(upgrade.contains("VALIDATE CONSTRAINT ck_sale_request_identity"),
                "V103 debe validar filas previas y abortar si encuentra identidades incompletas.");

        try (Connection connection = connections.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        CREATE TEMPORARY TABLE sale_request_identity_fixture (
                            id BIGINT GENERATED ALWAYS AS IDENTITY,
                            idempotency_key VARCHAR(64),
                            request_fingerprint CHAR(64),
                            CONSTRAINT uq_sale_idempotency_key UNIQUE (idempotency_key),
                            CONSTRAINT ck_sale_request_identity CHECK (
                                (idempotency_key IS NULL AND request_fingerprint IS NULL)
                                OR (idempotency_key IS NOT NULL
                                    AND request_fingerprint IS NOT NULL
                                    AND request_fingerprint ~ '^[0-9a-f]{64}$')
                            )
                        ) ON COMMIT DROP
                        """)) {
                    statement.execute();
                }

                insert(connection, null, null);
                insert(connection, null, null);
                String validFingerprint = "a".repeat(64);
                insert(connection, "sale-request-key", validFingerprint);

                assertSqlState(connection, "23505", () -> insert(connection, "sale-request-key", validFingerprint));
                assertSqlState(connection, "23514", () -> insert(connection, null, validFingerprint));
                assertSqlState(connection, "23514", () -> insert(connection, "key-without-fingerprint", null));
                assertSqlState(connection, "23514", () -> insert(connection, "invalid-fingerprint", "g".repeat(64)));
                assertEquals(3, countRows(connection), "Los rechazos SQL no deben dejar registros parciales.");
            } finally {
                connection.rollback();
            }
        }
    }

    private static void insert(Connection connection, String key, String fingerprint) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sale_request_identity_fixture (idempotency_key, request_fingerprint) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, fingerprint);
            statement.executeUpdate();
        }
    }

    private static void assertSqlState(Connection connection, String expectedState, SqlOperation operation)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException failure = assertThrows(SQLException.class, operation::run);
        connection.rollback(savepoint);
        assertEquals(expectedState, failure.getSQLState());
    }

    private static int countRows(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM sale_request_identity_fixture");
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new SQLException("No se obtuvo el conteo de las filas temporales.");
            return rows.getInt(1);
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
