package com.laexclusiva.repository.jdbc;

import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.repository.CustomerDebtQuery;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Adaptador JDBC que deriva la cartera de ventas del historial inmutable de ventas y pagos. */
public final class CustomerDebtQueryJdbc implements CustomerDebtQuery {

    private static final String OUTSTANDING_DEBT_SQL = """
            WITH line_totals AS (
                SELECT sale_id, SUM(quantity * unit_price) AS sale_total
                FROM sale_line
                GROUP BY sale_id
            ), payment_totals AS (
                SELECT sale_id, SUM(amount) AS paid_total
                FROM sale_payment
                GROUP BY sale_id
            )
            SELECT COALESCE(SUM(GREATEST(COALESCE(line_totals.sale_total, 0)
                                         - COALESCE(payment_totals.paid_total, 0), 0)), 0)
                   AS outstanding_sales_debt
            FROM sale
            LEFT JOIN line_totals ON line_totals.sale_id = sale.id
            LEFT JOIN payment_totals ON payment_totals.sale_id = sale.id
            WHERE sale.customer_id = ?
            """;

    private final JdbcConnectionProvider connectionProvider;

    /**
     * Crea la consulta con el proveedor JDBC compartido por los demás adaptadores.
     *
     * @param connectionProvider proveedor que reutiliza la conexión transaccional activa
     * @throws IllegalArgumentException si el proveedor es nulo
     */
    public CustomerDebtQueryJdbc(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Suma primero líneas y pagos por venta para evitar multiplicar sus importes entre sí, y limita
     * cada saldo a cero para que un sobrepago histórico no reduzca la deuda de otra venta. La consulta
     * solo lee tablas de ventas; el saldo del cliente y la deuda general quedan excluidos.
     *
     * @param customerId identificador no vacío del cliente
     * @return deuda pendiente asociada a sus ventas, o cero si no tiene ventas
     * @throws IllegalArgumentException si el identificador es nulo o está en blanco
     * @throws JdbcPersistenceException si PostgreSQL no puede ejecutar la consulta
     */
    @Override
    public BigDecimal findOutstandingSalesDebt(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("El identificador del cliente es obligatorio.");
        }
        return connectionProvider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(OUTSTANDING_DEBT_SQL)) {
                statement.setString(1, customerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("La consulta no devolvió el saldo agregado del cliente.");
                    }
                    BigDecimal debt = resultSet.getBigDecimal("outstanding_sales_debt");
                    return debt == null ? BigDecimal.ZERO : debt;
                }
            }
        });
    }
}
