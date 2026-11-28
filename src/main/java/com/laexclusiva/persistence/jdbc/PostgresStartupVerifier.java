package com.laexclusiva.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Comprueba el contrato PostgreSQL V112 sin modificar la base.
 * El PR 1 entrega este verificador explícito. Su uso obligatorio en el arranque
 * queda para el PR 2 (feature/jesus-commercial-startup).
 */
public final class PostgresStartupVerifier {
    private static final List<String> TABLE_PRIVILEGES = List.of("SELECT", "INSERT", "UPDATE", "DELETE");
    private static final String SALE_ROW_LOCK = "UPDATE (id)";
    private static final Map<String, List<String>> REQUIRED_PRIVILEGES = requiredPrivileges();
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "seller.active", "customer.credit_limit", "sale_payment.received_by_user_id",
            "customer_general_debt_movement.received_by_user_id", "stock_movement.actor_user_id",
            "sale.id", "schema_migration.version");

    private static Map<String, List<String>> requiredPrivileges() {
        Map<String, List<String>> privileges = new LinkedHashMap<>();
        privileges.put("schema_migration", List.of("SELECT"));
        privileges.put("customer", TABLE_PRIVILEGES);
        for (String table : List.of("user_account", "seller", "product_price", "inventory_item", "supplier",
                "purchase_order", "purchase_order_line", "customer_collection_profile", "customer_collection_reminder")) {
            privileges.put(table, List.of("SELECT", "INSERT", "UPDATE"));
        }
        for (String table : List.of("beer_product", "stock_movement", "sale_line", "sale_payment",
                "customer_general_debt_movement", "cash_movement", "goods_receipt", "goods_receipt_line",
                "customer_collection_follow_up", "administrative_audit_event")) {
            privileges.put(table, List.of("SELECT", "INSERT"));
        }
        // SaleRepositoryJdbc bloquea la cabecera con FOR UPDATE OF s al registrar abonos.
        privileges.put("sale", List.of("SELECT", "INSERT", SALE_ROW_LOCK));
        return privileges;
    }

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
        Map<String, Set<String>> granted = new LinkedHashMap<>();
        Set<String> columns = new HashSet<>();
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery("""
                    SELECT c.relname, a.attname,
                           pg_catalog.has_table_privilege(current_user, c.oid, 'SELECT'),
                           pg_catalog.has_table_privilege(current_user, c.oid, 'INSERT'),
                           pg_catalog.has_table_privilege(current_user, c.oid, 'UPDATE'),
                           pg_catalog.has_table_privilege(current_user, c.oid, 'DELETE'),
                           CASE WHEN c.relname = 'sale' AND a.attname = 'id'
                                THEN pg_catalog.has_column_privilege(current_user, c.oid, a.attnum, 'UPDATE')
                                ELSE false END
                    FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    LEFT JOIN pg_catalog.pg_attribute a
                        ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
                    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                    """)) {
                while (rows.next()) {
                    String table = rows.getString(1);
                    columns.add(table + "." + rows.getString(2));
                    Set<String> privileges = granted.computeIfAbsent(table, key -> new HashSet<>());
                    for (int index = 0; index < TABLE_PRIVILEGES.size(); index++) {
                        if (rows.getBoolean(index + 3)) {
                            privileges.add(TABLE_PRIVILEGES.get(index));
                        }
                    }
                    if (rows.getBoolean(7)) {
                        privileges.add(SALE_ROW_LOCK);
                    }
                }
            }
            for (String table : REQUIRED_PRIVILEGES.keySet()) {
                if (!granted.containsKey(table)) {
                    throw incompatible("Falta la tabla public." + table + ".");
                }
            }
            for (String column : REQUIRED_COLUMNS) {
                if (!columns.contains(column)) {
                    throw incompatible("Falta la columna public." + column + ".");
                }
            }
            for (var requirement : REQUIRED_PRIVILEGES.entrySet()) {
                String table = requirement.getKey();
                for (String privilege : requirement.getValue()) {
                    if (!granted.get(table).contains(privilege)) {
                        throw insufficient("Falta " + privilege + " sobre public." + table + ".");
                    }
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
