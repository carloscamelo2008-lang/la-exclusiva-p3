package com.laexclusiva.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Comprueba el contrato del esquema comercial sin modificar la base. */
public final class PostgresStartupVerifier {
    private static final List<String> REQUIRED_TABLES = List.of(
            "schema_migration", "user_account", "seller", "customer", "beer_product",
            "product_price", "inventory_item", "stock_movement", "sale", "sale_line",
            "sale_payment", "customer_general_debt_movement", "cash_movement", "supplier",
            "purchase_order", "purchase_order_line", "goods_receipt", "goods_receipt_line");
    private static final List<String> CRUD_TABLES = List.of(
            "user_account", "customer", "beer_product", "inventory_item", "stock_movement",
            "sale", "sale_line", "sale_payment", "customer_general_debt_movement", "cash_movement",
            "purchase_order", "goods_receipt");
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "seller.active", "customer.credit_limit", "sale_payment.received_by_user_id",
            "customer_general_debt_movement.received_by_user_id", "stock_movement.actor_user_id",
            "schema_migration.version");

    public enum Failure {
        CONNECTION_FAILED,
        INCOMPATIBLE_SCHEMA,
        INSUFFICIENT_PRIVILEGES
    }

    public static final class VerificationException extends JdbcPersistenceException {
        private final Failure failure;

        private VerificationException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public Failure getFailure() {
            return failure;
        }
    }

    private final JdbcConnectionProvider connections;

    public PostgresStartupVerifier(JdbcConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "El proveedor JDBC es obligatorio.");
    }

    /** Sale normalmente si la conexión, el esquema y los permisos son compatibles. */
    public void verify() {
        // El arranque usa una conexión propia, independiente de cualquier transacción de negocio.
        try (Connection connection = connections.openConnection()) {
            if (!connection.isValid(5)) {
                throw connectionFailure();
            }
            verifySchema(connection);
        } catch (VerificationException failure) {
            // El cierre de una conexión puede adjuntar detalles del controlador como suprimidos.
            throw new VerificationException(failure.getFailure(), failure.getMessage());
        } catch (SQLException failure) {
            throw connectionFailure();
        }
    }

    private static void verifySchema(Connection connection) {
        Set<String> tables = new HashSet<>();
        Set<String> columns = new HashSet<>();
        Set<String> writableTables = new HashSet<>();
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery("""
                    SELECT c.relname, a.attname,
                           pg_catalog.has_table_privilege(current_user, c.oid, 'SELECT')
                           AND pg_catalog.has_table_privilege(current_user, c.oid, 'INSERT')
                           AND pg_catalog.has_table_privilege(current_user, c.oid, 'UPDATE')
                           AND pg_catalog.has_table_privilege(current_user, c.oid, 'DELETE')
                    FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    LEFT JOIN pg_catalog.pg_attribute a
                        ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
                    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                    """)) {
                while (rows.next()) {
                    String table = rows.getString(1);
                    tables.add(table);
                    columns.add(table + "." + rows.getString(2));
                    if (rows.getBoolean(3)) {
                        writableTables.add(table);
                    }
                }
            }
            for (String table : REQUIRED_TABLES) {
                if (!tables.contains(table)) {
                    throw incompatible("Falta la tabla public." + table + ".");
                }
            }
            for (String column : REQUIRED_COLUMNS) {
                if (!columns.contains(column)) {
                    throw incompatible("Falta la columna public." + column + ".");
                }
            }
            try (var rows = statement.executeQuery("""
                    SELECT EXISTS (
                        SELECT FROM public.schema_migration
                        WHERE CASE WHEN version ~ '^V[0-9]{1,31}$'
                                   THEN substring(version FROM 2)::numeric >= 112
                                   ELSE false END
                    )
                    """)) {
                if (!rows.next() || !rows.getBoolean(1)) {
                    throw incompatible("Se requiere la versión V112 o posterior.");
                }
            }
            for (String table : CRUD_TABLES) {
                if (!writableTables.contains(table)) {
                    throw insufficient("Se requieren SELECT, INSERT, UPDATE y DELETE sobre public." + table + ".");
                }
            }
        } catch (SQLException failure) {
            String state = failure.getSQLState();
            if ("42501".equals(state)) {
                throw insufficient("Se requiere acceso al esquema public y lectura de public.schema_migration.");
            }
            if (state != null && state.startsWith("42")) {
                throw incompatible("No se pudo consultar el registro de migraciones.");
            }
            throw connectionFailure();
        }
    }

    private static VerificationException incompatible(String detail) {
        return new VerificationException(Failure.INCOMPATIBLE_SCHEMA,
                "El esquema de PostgreSQL no es compatible. " + detail);
    }

    private static VerificationException insufficient(String detail) {
        return new VerificationException(Failure.INSUFFICIENT_PRIVILEGES,
                "La cuenta de PostgreSQL tiene permisos insuficientes. " + detail);
    }

    private static VerificationException connectionFailure() {
        return new VerificationException(Failure.CONNECTION_FAILED,
                "No se pudo establecer o verificar la conexión con PostgreSQL.");
    }
}
