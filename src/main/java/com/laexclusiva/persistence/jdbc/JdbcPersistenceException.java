package com.laexclusiva.persistence.jdbc;

/** Excepción no comprobada para errores de conexión o persistencia JDBC. */
public class JdbcPersistenceException extends RuntimeException {
    public JdbcPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public JdbcPersistenceException(String message) {
        super(message);
    }
}
