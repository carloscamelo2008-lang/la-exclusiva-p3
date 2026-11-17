package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.jdbc.CustomerBalanceAdjusterJdbc;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas JDBC simuladas de clientes, vendedores y ajustes atómicos del saldo agregado. */
class CustomerSellerJdbcTest {

    private FakeDatabase database;
    private JdbcConnectionProvider provider;
    private JdbcTransactionManager transactionManager;
    private CustomerRepositoryJdbc customers;
    private SellerRepositoryJdbc sellers;
    private CustomerBalanceAdjusterJdbc balances;

    @BeforeEach
    void setUp() {
        database = new FakeDatabase();
        provider = new JdbcConnectionProvider(
                new DatabaseConfig("jdbc:postgresql://localhost:5432/la_exclusiva_test", "test-user", "test-pass"),
                database::openConnection);
        transactionManager = new JdbcTransactionManager(provider);
        customers = new CustomerRepositoryJdbc(provider);
        sellers = new SellerRepositoryJdbc(provider);
        balances = new CustomerBalanceAdjusterJdbc(provider);
    }

    @Test
    void customerRepositoryInsertsQueriesAndUpdatesWithoutReplacingDuplicateIds() {
        customers.save(customer("C-1", "Ana", "100.00"));
        assertTrue(customers.existsById("C-1"));
        assertEquals(0, customers.findById("C-1").getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(List.of("C-1"), customers.findAll().stream().map(Customer::getId).toList());

        assertThrows(IllegalArgumentException.class,
                () -> customers.save(customer("C-1", "Reemplazo", "900.00")));
        assertEquals("Ana", customers.findById("C-1").getName());
        assertEquals(0, customers.findById("C-1").getBalance().compareTo(new BigDecimal("100.00")));

        customers.update(customer("C-1", "Ana actualizada", "125.50"));
        assertEquals("Ana actualizada", customers.findById("C-1").getName());
        assertEquals(0, customers.findById("C-1").getBalance().compareTo(new BigDecimal("125.50")));
        assertThrows(IllegalArgumentException.class,
                () -> customers.update(customer("C-MISSING", "Sin fila", "0.00")));
        assertNull(customers.findById("C-MISSING"));
    }

    @Test
    void customerRepositoryValidatesSnapshotsAndReportsSqlErrors() {
        assertThrows(IllegalArgumentException.class, () -> customers.save(null));
        assertThrows(IllegalArgumentException.class, () -> customers.save(customer("C-SCALE", "Ana", "1.001")));
        assertThrows(IllegalArgumentException.class,
                () -> customers.save(customer("C-TOO-LONG-" + "X".repeat(60), "Ana", "0.00")));
        assertFalse(customers.existsById(null));
        assertFalse(customers.existsById("  "));
        assertThrows(IllegalArgumentException.class, () -> customers.findById(" "));

        database.failNextStatement = true;
        assertThrows(JdbcPersistenceException.class, customers::findAll);
    }

    @Test
    void sellerRepositoryPreservesIdsAndRejectsDuplicateInsertions() {
        Seller original = seller("SEL-1", "María", "3111111111");
        sellers.save(original);
        assertEquals("María", sellers.findById("SEL-1").orElseThrow().getName());
        assertEquals(List.of("SEL-1"), sellers.findAll().stream().map(Seller::getId).toList());
        assertTrue(sellers.findById("SEL-MISSING").isEmpty());
        assertTrue(sellers.findById(null).isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> sellers.save(seller("SEL-1", "No reemplazar", "3000000000")));
        assertEquals("María", sellers.findById("SEL-1").orElseThrow().getName());
        assertThrows(IllegalArgumentException.class, () -> sellers.save(null));
        assertThrows(IllegalArgumentException.class,
                () -> sellers.save(seller("SEL-" + "X".repeat(32), "Largo", "3000000000")));
    }

    @Test
    void sellerRepositoryTranslatesSqlFailures() {
        database.failNextStatement = true;
        assertThrows(JdbcPersistenceException.class, sellers::findAll);
    }

    @Test
    void balanceAdjustmentsAreAtomicAndCannotProduceNegativeValues() {
        customers.save(customer("C-BAL", "Balance", "100.00"));

        assertEquals(0, balances.increaseBalance("C-BAL", new BigDecimal("50.00"))
                .compareTo(new BigDecimal("150.00")));
        assertEquals(0, balances.decreaseBalance("C-BAL", new BigDecimal("120.00"))
                .compareTo(new BigDecimal("30.00")));
        assertEquals(0, balances.decreaseBalance("C-BAL", new BigDecimal("30.00"))
                .compareTo(BigDecimal.ZERO.setScale(2)));
        assertThrows(IllegalArgumentException.class,
                () -> balances.decreaseBalance("C-BAL", new BigDecimal("0.01")));
        assertEquals(0, customers.findById("C-BAL").getBalance().compareTo(BigDecimal.ZERO.setScale(2)));
        assertTrue(database.lastAtomicDecreaseSql.contains("balance >= ?"),
                "La condición de saldo debe formar parte del UPDATE atómico.");
    }

    @Test
    void balanceAdjustmentsValidateAmountsAndMissingCustomers() {
        assertThrows(IllegalArgumentException.class, () -> balances.increaseBalance(null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> balances.increaseBalance("C-1", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> balances.decreaseBalance("C-1", new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class,
                () -> balances.increaseBalance("C-" + "X".repeat(64), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> balances.increaseBalance("C-MISSING", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> balances.decreaseBalance("C-MISSING", BigDecimal.ONE));
    }

    @Test
    void balanceAdjustmentJoinsAndRollsBackWithTheOuterTransaction() {
        customers.save(customer("C-ROLLBACK", "Rollback", "100.00"));
        int connectionsBefore = database.connectionsOpened;

        assertThrows(IllegalStateException.class, () -> transactionManager.inTransaction(() -> {
            balances.increaseBalance("C-ROLLBACK", new BigDecimal("25.00"));
            balances.decreaseBalance("C-ROLLBACK", new BigDecimal("10.00"));
            throw new IllegalStateException("fallo posterior simulado");
        }));

        assertEquals(connectionsBefore + 1, database.connectionsOpened);
        assertEquals(1, database.rollbacks);
        assertEquals(0, database.commits);
        assertEquals(0, customers.findById("C-ROLLBACK").getBalance().compareTo(new BigDecimal("100.00")));
    }

    @Test
    void concurrentSimulatedPaymentsCannotMakeTheBalanceNegative() throws Exception {
        customers.save(customer("C-CONCURRENT", "Concurrente", "100.00"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> attemptPayment(start, "C-CONCURRENT"));
            Future<Boolean> second = executor.submit(() -> attemptPayment(start, "C-CONCURRENT"));
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            assertEquals(0, customers.findById("C-CONCURRENT").getBalance()
                    .compareTo(new BigDecimal("20.00")));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptPayment(CountDownLatch start, String customerId) throws InterruptedException {
        start.await();
        try {
            balances.decreaseBalance(customerId, new BigDecimal("80.00"));
            return true;
        } catch (IllegalArgumentException insufficientBalance) {
            return false;
        }
    }

    private static Customer customer(String id, String name, String balance) {
        return new Customer(id, name, "3000000000", "Dirección ficticia", new BigDecimal(balance));
    }

    private static Seller seller(String id, String name, String phone) {
        return new Seller(id, name, phone, "Dirección ficticia");
    }

    /** Base JDBC mínima con ejecución serializada de sentencias y snapshots transaccionales. */
    private static final class FakeDatabase {
        private final Map<String, CustomerRow> customers = new HashMap<>();
        private final Map<String, SellerRow> sellers = new HashMap<>();
        private int connectionsOpened;
        private int commits;
        private int rollbacks;
        private boolean failNextStatement;
        private String lastAtomicDecreaseSql;

        private synchronized Connection openConnection() {
            connectionsOpened++;
            InvocationHandler handler = new InvocationHandler() {
                private boolean autoCommit = true;
                private Snapshot transactionSnapshot;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    return switch (method.getName()) {
                        case "prepareStatement" -> preparedStatement((String) args[0]);
                        case "setAutoCommit" -> {
                            autoCommit = (boolean) args[0];
                            if (!autoCommit) transactionSnapshot = snapshot();
                            yield null;
                        }
                        case "getAutoCommit" -> autoCommit;
                        case "commit" -> { commits++; transactionSnapshot = null; yield null; }
                        case "rollback" -> {
                            rollbacks++;
                            restore(transactionSnapshot);
                            transactionSnapshot = null;
                            yield null;
                        }
                        case "close", "clearWarnings" -> null;
                        case "isClosed" -> false;
                        case "toString" -> "Fake customer and seller connection";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return proxy(Connection.class, handler);
        }

        private PreparedStatement preparedStatement(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            InvocationHandler handler = new InvocationHandler() {
                private final Map<Integer, Object> parameters = new HashMap<>();

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().startsWith("set") && args != null && args.length >= 2
                            && args[0] instanceof Integer index) {
                        parameters.put(index, args[1]);
                        return null;
                    }
                    return switch (method.getName()) {
                        case "executeUpdate" -> executeUpdate(normalized, parameters);
                        case "executeQuery" -> resultSet(executeQuery(normalized, parameters));
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "toString" -> "Fake customer and seller statement";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return proxy(PreparedStatement.class, handler);
        }

        private synchronized int executeUpdate(String sql, Map<Integer, Object> values) throws SQLException {
            failIfRequested();
            if (sql.startsWith("insert into customer ")) {
                String id = (String) values.get(1);
                if (customers.containsKey(id)) throw new SQLException("duplicate customer", "23505");
                customers.put(id, new CustomerRow(id, (String) values.get(2), (String) values.get(3),
                        (String) values.get(4), (BigDecimal) values.get(5)));
                return 1;
            }
            if (sql.startsWith("update customer set name =")) {
                String id = (String) values.get(5);
                CustomerRow current = customers.get(id);
                if (current == null) return 0;
                customers.put(id, new CustomerRow(id, (String) values.get(1), (String) values.get(2),
                        (String) values.get(3), (BigDecimal) values.get(4)));
                return 1;
            }
            if (sql.startsWith("insert into seller ")) {
                String id = (String) values.get(1);
                if (sellers.containsKey(id)) throw new SQLException("duplicate seller", "23505");
                sellers.put(id, new SellerRow(id, (String) values.get(2), (String) values.get(3),
                        (String) values.get(4)));
                return 1;
            }
            throw new SQLException("SQL simulado sin implementación: " + sql, "0A000");
        }

        private synchronized List<Map<String, Object>> executeQuery(String sql, Map<Integer, Object> values)
                throws SQLException {
            failIfRequested();
            if (sql.startsWith("update customer set balance = balance - ?")) {
                lastAtomicDecreaseSql = sql;
            }
            if (sql.startsWith("select id, name, phone, address, balance from customer where id = ?")) {
                CustomerRow row = customers.get((String) values.get(1));
                return row == null ? List.of() : List.of(row.asMap());
            }
            if (sql.startsWith("select id, name, phone, address, balance from customer order by id")) {
                return customers.values().stream().sorted((first, second) -> first.id().compareTo(second.id()))
                        .map(CustomerRow::asMap).toList();
            }
            if (sql.startsWith("select 1 from customer where id = ?")) {
                return customers.containsKey((String) values.get(1))
                        ? List.of(Map.of("?column?", 1)) : List.of();
            }
            if (sql.startsWith("select id, name, phone, address from seller where id = ?")) {
                SellerRow row = sellers.get((String) values.get(1));
                return row == null ? List.of() : List.of(row.asMap());
            }
            if (sql.startsWith("select id, name, phone, address from seller order by id")) {
                return sellers.values().stream().sorted((first, second) -> first.id().compareTo(second.id()))
                        .map(SellerRow::asMap).toList();
            }
            if (sql.startsWith("update customer set balance = balance + ?")) {
                String id = (String) values.get(2);
                CustomerRow row = customers.get(id);
                if (row == null) return List.of();
                BigDecimal balance = row.balance().add((BigDecimal) values.get(1));
                customers.put(id, row.withBalance(balance));
                return List.of(Map.of("balance", balance));
            }
            if (sql.startsWith("update customer set balance = balance - ?")) {
                String id = (String) values.get(2);
                CustomerRow row = customers.get(id);
                BigDecimal amount = (BigDecimal) values.get(1);
                if (row == null || row.balance().compareTo(amount) < 0) return List.of();
                BigDecimal balance = row.balance().subtract(amount);
                customers.put(id, row.withBalance(balance));
                return List.of(Map.of("balance", balance));
            }
            throw new SQLException("SQL simulado sin implementación: " + sql, "0A000");
        }

        private void failIfRequested() throws SQLException {
            if (failNextStatement) {
                failNextStatement = false;
                throw new SQLException("fallo SQL simulado", "XX000");
            }
        }

        private synchronized Snapshot snapshot() {
            return new Snapshot(new HashMap<>(customers), new HashMap<>(sellers));
        }

        private synchronized void restore(Snapshot snapshot) {
            if (snapshot == null) return;
            customers.clear();
            customers.putAll(snapshot.customers());
            sellers.clear();
            sellers.putAll(snapshot.sellers());
        }

        private static ResultSet resultSet(List<Map<String, Object>> rows) {
            InvocationHandler handler = new InvocationHandler() {
                private int position = -1;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getName().equals("next")) return ++position < rows.size();
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("isClosed")) return false;
                    if (method.getName().equals("toString")) return "Fake customer and seller result set";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("getString") || method.getName().equals("getBigDecimal")) {
                        return rows.get(position).get(String.valueOf(args[0]).toLowerCase(Locale.ROOT));
                    }
                    return defaultValue(method.getReturnType());
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

    private record CustomerRow(String id, String name, String phone, String address, BigDecimal balance) {
        private Map<String, Object> asMap() {
            return Map.of("id", id, "name", name, "phone", phone, "address", address, "balance", balance);
        }

        private CustomerRow withBalance(BigDecimal newBalance) {
            return new CustomerRow(id, name, phone, address, newBalance);
        }
    }

    private record SellerRow(String id, String name, String phone, String address) {
        private Map<String, Object> asMap() {
            return Map.of("id", id, "name", name, "phone", phone, "address", address);
        }
    }

    private record Snapshot(Map<String, CustomerRow> customers, Map<String, SellerRow> sellers) { }
}
