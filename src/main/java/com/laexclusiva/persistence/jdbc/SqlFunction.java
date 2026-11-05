package com.laexclusiva.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/** Operación JDBC que utiliza una conexión proporcionada por JdbcConnectionProvider. */
@FunctionalInterface
public interface SqlFunction<T> {
    /**
     * Ejecuta una operación SQL.
     *
     * @param connection conexión administrada por el proveedor; no debe cerrarse ni confirmarse
     * @return resultado de la operación
     * @throws SQLException si falla una operación JDBC
     */
    T apply(Connection connection) throws SQLException;
}
