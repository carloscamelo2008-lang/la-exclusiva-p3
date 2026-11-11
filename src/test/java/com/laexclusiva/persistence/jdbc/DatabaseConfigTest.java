package com.laexclusiva.persistence.jdbc;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Validaciones JDBC sin depender de una instancia PostgreSQL. */
class DatabaseConfigTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/la_exclusiva";
    private static final String TEST_URL = "jdbc:postgresql://localhost:5432/la_exclusiva_test";

    @Test
    void loadsDevelopmentConfigurationFromMap() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(Map.of(
                DatabaseConfig.URL_ENV, URL,
                DatabaseConfig.USER_ENV, "postgres",
                DatabaseConfig.PASSWORD_ENV, "secretoLocal"));
        assertEquals(URL, config.getUrl());
        assertEquals("postgres", config.getUser());
        assertEquals("secretoLocal", config.getPassword());
        assertFalse(config.toString().contains("secretoLocal"), "No revelar la contraseña en logs.");
    }

    @Test
    void rejectsMissingCredentialsOrInvalidJDBCUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("jdbc:mysql://localhost/la_exclusiva", "postgres", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig(URL, "", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig(URL, "postgres", ""));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromEnvironment(Map.of(DatabaseConfig.URL_ENV, URL)));
    }

    @Test
    void acceptsOnlyDedicatedIntegrationTestDatabase() {
        DatabaseConfig test = DatabaseConfig.fromTestEnvironment(Map.of(
                DatabaseConfig.TEST_URL_ENV, TEST_URL + "?sslmode=disable",
                DatabaseConfig.USER_ENV, "postgres",
                DatabaseConfig.PASSWORD_ENV, "secretoLocal"));
        assertEquals(TEST_URL + "?sslmode=disable", test.getUrl());

        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromTestEnvironment(Map.of(
                DatabaseConfig.TEST_URL_ENV, URL,
                DatabaseConfig.USER_ENV, "postgres",
                DatabaseConfig.PASSWORD_ENV, "secretoLocal")));
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromTestEnvironment(Map.of(
                DatabaseConfig.TEST_URL_ENV, TEST_URL + "_copy",
                DatabaseConfig.USER_ENV, "postgres",
                DatabaseConfig.PASSWORD_ENV, "secretoLocal")));
    }

    @Test
    void rejectsMissingOrMalformedTestDatabaseUrl() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromTestEnvironment(Map.of(
                DatabaseConfig.USER_ENV, "postgres",
                DatabaseConfig.PASSWORD_ENV, "secretoLocal")));
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromTestEnvironment(Map.of(
                DatabaseConfig.TEST_URL_ENV, "jdbc:postgresql://localhost:5432/",
                DatabaseConfig.USER_ENV, "postgres",
                DatabaseConfig.PASSWORD_ENV, "secretoLocal")));
    }
}
