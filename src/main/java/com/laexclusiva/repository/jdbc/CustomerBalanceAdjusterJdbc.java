package com.laexclusiva.repository.jdbc;

import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.repository.CustomerBalanceAdjuster;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Ajusta el saldo agregado mediante sentencias atómicas que pueden unirse a una transacción exterior. */
public final class CustomerBalanceAdjusterJdbc implements CustomerBalanceAdjuster {

    private final JdbcConnectionProvider connectionProvider;

    /**
     * Crea el adaptador con el proveedor de conexiones compartido por clientes, ventas y cartera.
     *
     * @param connectionProvider proveedor JDBC requerido
     * @throws IllegalArgumentException si el proveedor es nulo
     */
    public CustomerBalanceAdjusterJdbc(JdbcConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Aumenta el saldo en SQL sin leer ni reemplazar un snapshot del cliente.
     *
     * @param customerId identificador del cliente
     * @param amount importe positivo con máximo dos decimales
     * @return saldo resultante
     * @throws IllegalArgumentException si el ID o importe son inválidos, o no existe el cliente
     * @throws JdbcPersistenceException si falla PostgreSQL
     */
    @Override
    public BigDecimal increaseBalance(String customerId, BigDecimal amount) {
        String id = requireId(customerId);
        BigDecimal normalizedAmount = requirePositiveMoney(amount);
        return connectionProvider.withConnection(connection -> {
            String sql = "UPDATE customer SET balance = balance + ? WHERE id = ? RETURNING balance";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBigDecimal(1, normalizedAmount);
                statement.setString(2, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getBigDecimal("balance");
                    }
                }
            }
            throw new IllegalArgumentException("No existe un cliente con el ID: " + id);
        });
    }

    /**
     * Reduce el saldo con una condición en el mismo UPDATE, evitando saldos negativos bajo concurrencia.
     * Si participa en una transacción exterior, el ajuste se confirma o revierte junto con ella.
     *
     * @param customerId identificador del cliente
     * @param amount abono positivo con máximo dos decimales
     * @return saldo resultante
     * @throws IllegalArgumentException si el ID o importe son inválidos, el cliente no existe o
     *                                  el abono supera el saldo disponible
     * @throws JdbcPersistenceException si falla PostgreSQL
     */
    @Override
    public BigDecimal decreaseBalance(String customerId, BigDecimal amount) {
        String id = requireId(customerId);
        BigDecimal normalizedAmount = requirePositiveMoney(amount);
        return connectionProvider.withConnection(connection -> {
            String updateSql = """
                    UPDATE customer
                    SET balance = balance - ?
                    WHERE id = ? AND balance >= ?
                    RETURNING balance
                    """;
            try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                statement.setBigDecimal(1, normalizedAmount);
                statement.setString(2, id);
                statement.setBigDecimal(3, normalizedAmount);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getBigDecimal("balance");
                    }
                }
            }
            if (!customerExists(connection, id)) {
                throw new IllegalArgumentException("No existe un cliente con el ID: " + id);
            }
            throw new IllegalArgumentException("El abono supera el saldo disponible del cliente.");
        });
    }

    private static boolean customerExists(java.sql.Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM customer WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El ID del cliente es obligatorio.");
        }
        if (id.length() > 64) {
            throw new IllegalArgumentException("El ID del cliente supera el límite VARCHAR(64) de V001.");
        }
        return id;
    }

    private static BigDecimal requirePositiveMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El ajuste de saldo debe ser mayor que cero.");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw new IllegalArgumentException("El ajuste supera NUMERIC(19,2).");
            }
            return normalized;
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("El ajuste no admite más de dos decimales.", failure);
        }
    }
}
