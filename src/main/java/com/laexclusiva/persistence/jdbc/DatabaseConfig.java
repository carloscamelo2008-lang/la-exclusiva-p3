package com.laexclusiva.persistence.jdbc;

import java.util.Map;

/**
 * Configuración local de PostgreSQL obtenida desde variables de entorno.
 * Las credenciales no deben almacenarse en el repositorio ni imprimirse en logs.
 */
public final class DatabaseConfig {
    public static final String URL_ENV = "LAEXCLUSIVA_DB_URL";
    public static final String TEST_URL_ENV = "LAEXCLUSIVA_TEST_DB_URL";
    public static final String USER_ENV = "LAEXCLUSIVA_DB_USER";
    public static final String PASSWORD_ENV = "LAEXCLUSIVA_DB_PASSWORD";

    private static final String POSTGRESQL_PREFIX = "jdbc:postgresql://";
    private static final String TEST_DATABASE_NAME = "la_exclusiva_test";

    private final String url;
    private final String user;
    private final String password;

    public DatabaseConfig(String url, String user, String password) {
        if (url == null || url.isBlank() || !url.startsWith(POSTGRESQL_PREFIX)) {
            throw new IllegalArgumentException("La URL debe ser una conexión JDBC de PostgreSQL.");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("LAEXCLUSIVA_DB_USER es obligatorio.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("LAEXCLUSIVA_DB_PASSWORD es obligatorio.");
        }
        this.url = url.trim();
        this.user = user.trim();
        this.password = password;
    }

    /** Carga la configuración de desarrollo desde el entorno del proceso. */
    public static DatabaseConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /**
     * Carga la configuración de desarrollo desde un mapa, útil para pruebas unitarias.
     *
     * @param environment variables de entorno disponibles
     * @return configuración validada
     */
    public static DatabaseConfig fromEnvironment(Map<String, String> environment) {
        if (environment == null) {
            throw new IllegalArgumentException("El entorno de configuración es obligatorio.");
        }
        return new DatabaseConfig(
                environment.get(URL_ENV),
                environment.get(USER_ENV),
                environment.get(PASSWORD_ENV));
    }

    /**
     * Carga la configuración exclusiva para pruebas de integración.
     * La URL debe apuntar exactamente a la base la_exclusiva_test.
     *
     * @return configuración de pruebas validada
     */
    public static DatabaseConfig fromTestEnvironment() {
        return fromTestEnvironment(System.getenv());
    }

    /**
     * Variante inyectable para comprobar la validación sin depender del entorno real.
     *
     * @param environment variables de entorno disponibles
     * @return configuración de pruebas validada
     */
    public static DatabaseConfig fromTestEnvironment(Map<String, String> environment) {
        if (environment == null) {
            throw new IllegalArgumentException("El entorno de configuración es obligatorio.");
        }
        String testUrl = environment.get(TEST_URL_ENV);
        String databaseName = extractDatabaseName(testUrl);
        if (!TEST_DATABASE_NAME.equals(databaseName)) {
            throw new IllegalArgumentException(
                    TEST_URL_ENV + " debe apuntar exclusivamente a la base " + TEST_DATABASE_NAME + ".");
        }
        return new DatabaseConfig(
                testUrl,
                environment.get(USER_ENV),
                environment.get(PASSWORD_ENV));
    }

    private static String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(POSTGRESQL_PREFIX)) {
            throw new IllegalArgumentException(TEST_URL_ENV + " debe ser una URL JDBC de PostgreSQL.");
        }
        String withoutQuery = jdbcUrl.substring(POSTGRESQL_PREFIX.length()).split("\\?", 2)[0];
        int databaseSeparator = withoutQuery.indexOf('/');
        if (databaseSeparator < 0 || databaseSeparator == withoutQuery.length() - 1) {
            throw new IllegalArgumentException(TEST_URL_ENV + " debe especificar el nombre de la base de datos.");
        }
        String databaseName = withoutQuery.substring(databaseSeparator + 1);
        if (databaseName.contains("/")) {
            throw new IllegalArgumentException(TEST_URL_ENV + " contiene una ruta de base de datos no válida.");
        }
        return databaseName;
    }

    public String getUrl() { return url; }
    public String getUser() { return user; }
    public String getPassword() { return password; }

    /** Evita exponer la contraseña si la configuración aparece en un log. */
    @Override
    public String toString() {
        return "DatabaseConfig{url='" + url + "', user='" + user + "', password='[PROTEGIDA]'}";
    }
}
