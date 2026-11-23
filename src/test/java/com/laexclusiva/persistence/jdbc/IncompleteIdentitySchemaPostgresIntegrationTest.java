package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.ui.ApplicationServices;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Comprueba que la composición PostgreSQL rechace esquemas de identidad incompletos. */
class IncompleteIdentitySchemaPostgresIntegrationTest {
    @Test
    void incompleteIdentitySchemaFailsClosedWithoutMemoryFallback() {
        Assumptions.assumeTrue(Boolean.getBoolean("laexclusiva.test.postgres.incompleteIdentitySchema"),
                "Esta regresión se ejecuta solo en el laboratorio PostgreSQL efímero.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        verifyLaboratory(config);
        ApplicationServices app = ApplicationServices.create(ApplicationServices.POSTGRES_MODE, Map.of(
                ApplicationServices.MODE_ENV, ApplicationServices.POSTGRES_MODE,
                DatabaseConfig.URL_ENV, config.getUrl(),
                DatabaseConfig.USER_ENV, config.getUser(),
                DatabaseConfig.PASSWORD_ENV, config.getPassword()));

        assertInstanceOf(UserRepositoryJdbc.class, app.identity().users(),
                "La selección PostgreSQL debe conservar su repositorio JDBC, nunca usar memoria como fallback.");
        assertThrows(JdbcPersistenceException.class, () -> app.identity().users().findAll(),
                "V001 sin las migraciones de identidad debe producir un fallo cerrado explícito.");
    }

    private static void verifyLaboratory(DatabaseConfig config) {
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT current_database(), current_user, current_setting('cluster_name'), "
                             + "current_setting('server_version_num')::integer / 10000");
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || !"la_exclusiva_test".equals(rows.getString(1))
                    || !"identity_validation_owner".equals(rows.getString(2))
                    || !"laexclusiva-identity-validation".equals(rows.getString(3)) || rows.getInt(4) != 18) {
                throw new AssertionError("La prueba requiere el cluster PostgreSQL 18 efímero autorizado.");
            }
        } catch (java.sql.SQLException failure) {
            throw new JdbcPersistenceException("No se pudo verificar el laboratorio PostgreSQL efímero.", failure);
        }
    }
}
