package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.repository.jdbc.CustomerDebtQueryJdbc;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas con JDBC simulado; no conectan ni certifican el comportamiento de PostgreSQL real. */
class CustomerDebtQueryJdbcTest {

    private FakeDatabase database;
    private JdbcConnectionProvider provider;
    private JdbcTransactionManager transactionManager;
    private CustomerDebtQueryJdbc query;

    @BeforeEach
    void setUp() {
        database = new FakeDatabase();
        provider = new JdbcConnectionProvider(
                new DatabaseConfig("jdbc:postgresql://localhost:5432/la_exclusiva_test", "test-user", "test-pass"),
                database::openConnection);
        transactionManager = new JdbcTransactionManager(provider);
        query = new CustomerDebtQueryJdbc(provider);
    }

    @Test
    void sumsOutstandingBalancesBySaleWithoutMultiplyingLinesAndPayments() {
        database.sales.add(new SaleData("C-1", List.of("100.00", "100.00"), List.of("50.00", "25.00")));
        database.sales.add(new SaleData("C-1", List.of("225.00"), List.of("25.00", "100.00")));
        database.sales.add(new SaleData("C-1", List.of("150.00"), List.of("150.00")));
        database.sales.add(new SaleData("C-2", List.of("500.00"), List.of("100.00")));

        BigDecimal debt = query.findOutstandingSalesDebt("C-1");

        assertEquals(0, debt.compareTo(new BigDecimal("225.00")));
        assertEquals("C-1", database.lastCustomerId);
        String sql = database.lastSql;
        assertEquals(2, occurrences(sql, "group by sale_id"));
        assertTrue(sql.contains("left join line_totals"));
        assertTrue(sql.contains("left join payment_totals"));
        assertTrue(!sql.contains("customer.balance"), "La deuda general no forma parte de la consulta.");
    }

    @Test
    void overpaymentOnOneSaleDoesNotOffsetAnotherSalesOutstandingDebt() {
        database.sales.add(new SaleData("C-OVERPAY", List.of("50.00"), List.of("80.00")));
        database.sales.add(new SaleData("C-OVERPAY", List.of("200.00"), List.of("75.00")));

        BigDecimal debt = query.findOutstandingSalesDebt("C-OVERPAY");

        assertEquals(0, debt.compareTo(new BigDecimal("125.00")));
        assertTrue(database.lastSql.contains("greatest("),
                "El SQL debe truncar a cero el saldo de cada venta antes de sumarlo.");
    }

    @Test
    void returnsZeroForCustomerWithoutSales() {
        assertEquals(0, query.findOutstandingSalesDebt("C-WITHOUT-SALES").compareTo(BigDecimal.ZERO));
        assertEquals("C-WITHOUT-SALES", database.lastCustomerId);
    }

    @Test
    void reusesTheOuterTransactionConnection() {
        database.sales.add(new SaleData("C-TX", List.of("40.00"), List.of("10.00")));

        BigDecimal debt = transactionManager.inTransaction(() -> {
            assertEquals(0, query.findOutstandingSalesDebt("C-TX").compareTo(new BigDecimal("30.00")));
            assertEquals(0, query.findOutstandingSalesDebt("C-TX").compareTo(new BigDecimal("30.00")));
            assertEquals(1, database.connectionsOpened);
            return query.findOutstandingSalesDebt("C-TX");
        });

        assertEquals(0, debt.compareTo(new BigDecimal("30.00")));
        assertEquals(1, database.connectionsOpened);
        assertEquals(1, database.commits);
        assertEquals(0, database.rollbacks);
    }

    @Test
    void rejectsBlankCustomerIdBeforeOpeningAConnection() {
        assertThrows(IllegalArgumentException.class, () -> query.findOutstandingSalesDebt(null));
        assertThrows(IllegalArgumentException.class, () -> query.findOutstandingSalesDebt("  "));
        assertEquals(0, database.connectionsOpened);
    }

    @Test
    void translatesSqlFailuresThroughTheSharedProvider() {
        database.failQueries = true;
        assertThrows(JdbcPersistenceException.class, () -> query.findOutstandingSalesDebt("C-1"));
    }

    private static int occurrences(String value, String fragment) {
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(fragment, from)) >= 0) {
            count++;
            from += fragment.length();
        }
        return count;
    }

    private record SaleData(String customerId, List<String> lineAmounts, List<String> paymentAmounts) { }

    /** Controlador mínimo que reproduce la agregación por venta sin servidor PostgreSQL. */
    private static final class FakeDatabase {
        private final List<SaleData> sales = new ArrayList<>();
        private int connectionsOpened;
        private int commits;
        private int rollbacks;
        private boolean failQueries;
        private String lastCustomerId;
        private String lastSql;

        private Connection openConnection() {
            connectionsOpened++;
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> preparedStatement((String) args[0]);
                case "commit" -> { commits++; yield null; }
                case "rollback" -> { rollbacks++; yield null; }
                case "close", "setAutoCommit" -> null;
                case "getAutoCommit" -> true;
                case "isClosed" -> false;
                case "toString" -> "Fake customer debt connection";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
            return proxy(Connection.class, handler);
        }

        private PreparedStatement preparedStatement(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            lastSql = normalized;
            InvocationHandler handler = new InvocationHandler() {
                private final Map<Integer, Object> parameters = new HashMap<>();

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("setString")) {
                        parameters.put((Integer) args[0], args[1]);
                        return null;
                    }
                    return switch (method.getName()) {
                        case "executeQuery" -> resultSet(executeQuery(normalized, parameters));
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "toString" -> "Fake customer debt statement";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return proxy(PreparedStatement.class, handler);
        }

        private List<BigDecimal> executeQuery(String sql, Map<Integer, Object> parameters) throws SQLException {
            if (failQueries) throw new SQLException("fallo SQL simulado", "XX000");
            lastCustomerId = (String) parameters.get(1);
            BigDecimal debt = BigDecimal.ZERO;
            for (SaleData sale : sales) {
                if (!sale.customerId().equals(lastCustomerId)) continue;
                BigDecimal total = sale.lineAmounts().stream().map(BigDecimal::new)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal paid = sale.paymentAmounts().stream().map(BigDecimal::new)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                debt = debt.add(total.subtract(paid).max(BigDecimal.ZERO));
            }
            return List.of(debt);
        }

        private static ResultSet resultSet(List<BigDecimal> rows) {
            InvocationHandler handler = new InvocationHandler() {
                private int position = -1;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "next" -> ++position < rows.size();
                        case "getBigDecimal" -> rows.get(position);
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "toString" -> "Fake customer debt result set";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return proxy(ResultSet.class, handler);
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            return 0d;
        }
    }
}
