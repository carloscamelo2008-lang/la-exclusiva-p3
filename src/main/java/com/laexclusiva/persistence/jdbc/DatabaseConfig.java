package com.laexclusiva.persistence.jdbc;

import java.util.Map;

/**
 * Configuración local de PostgreSQL obtenida desde variables de entorno.
 * Las credenciales no deben almacenarse en el repositorio ni imprimirse en logs.
 */
public final class DatabaseConfig {
    public static final String URL_ENV = "LAEXCLUSIVA_DB_URL";
    public static final String USER_ENV = "LAEXCLUSIVA_DB_USER";
    public static final String PASSWORD_ENV = "LAEXCLUSIVA_DB_PASSWORD";

    private final String url;
    private final String user;
    private final String password;

    public DatabaseConfig(String url, String user, String password) {
        if (url == null || url.isBlank() || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("LAEXCLUSIVA_DB_URL debe ser una URL JDBC de PostgreSQL.");
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

    /** Carga la configuración desde el entorno del proceso. */
    public static DatabaseConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /**
     * Carga la configuración desde un mapa, útil para pruebas unitarias.
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

    public String getUrl() { return url; }
    public String getUser() { return user; }
    public String getPassword() { return password; }

    /** Evita exponer la contraseña si la configuración aparece en un log. */
    @Override
    public String toString() {
        return "DatabaseConfig{url='" + url + "', user='" + user + "', password='[PROTEGIDA]'}";
    }
}
