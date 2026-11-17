package com.laexclusiva.persistence.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Proporciona conexiones PostgreSQL a los DAO.
 *
 * <p>Fuera de una transacción abre y cierra una conexión para la operación. Dentro de una
 * transacción reutiliza la conexión enlazada al hilo actual. Los DAO nunca deben cerrar,
 * confirmar ni revertir la conexión recibida por withConnection.</p>
 *
 * <p>Una transacción debe ejecutarse de principio a fin en el mismo hilo.</p>
 */
public final class JdbcConnectionProvider {
    private final ConnectionFactory connectionFactory;
    private final JdbcConnectionContext connectionContext = new JdbcConnectionContext();

    /** Fuente de conexiones inyectable para pruebas sin PostgreSQL real. */
    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    public JdbcConnectionProvider(DatabaseConfig config) {
        this(config, () -> DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword()));
    }

    /** Constructor accesible solo en el paquete para pruebas unitarias. */
    JdbcConnectionProvider(DatabaseConfig config, ConnectionFactory connectionFactory) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración JDBC es obligatoria.");
        }
        if (connectionFactory == null) {
            throw new IllegalArgumentException("La fábrica de conexiones JDBC es obligatoria.");
        }
        this.connectionFactory = connectionFactory;
    }

    /**
     * Firma acordada para los DAO: withConnection(SqlFunction<T> operation).
     * Si existe una transacción, reutiliza su conexión sin cerrarla. En caso contrario,
     * abre una conexión independiente y la cierra al terminar.
     *
     * @param operation operación que utilizará la conexión
     * @param <T> tipo del resultado
     * @return resultado de la operación
     */
    public <T> T withConnection(SqlFunction<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("La operación JDBC es obligatoria.");
        }

        Connection activeConnection = connectionContext.activeConnection();
        if (activeConnection != null) {
            try {
                return operation.apply(activeConnection);
            } catch (SQLException failure) {
                connectionContext.markRollbackOnly();
                throw new JdbcPersistenceException("No se pudo ejecutar la operación SQL.", failure);
            } catch (RuntimeException | Error failure) {
                connectionContext.markRollbackOnly();
                throw failure;
            }
        }

        try (Connection connection = openConnection()) {
            return operation.apply(connection);
        } catch (SQLException failure) {
            throw new JdbcPersistenceException("No se pudo ejecutar la operación SQL o cerrar la conexión.", failure);
        }
    }

    Connection openConnection() throws SQLException {
        return connectionFactory.open();
    }

    JdbcConnectionContext connectionContext() {
        return connectionContext;
    }
}
