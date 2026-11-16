package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.jdbc.SaleRepositoryJdbc;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias con un controlador JDBC simulado; no requieren ni afirman usar PostgreSQL. */
class SaleRepositoryJdbcTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 10, 15, 30, 123_456_789);

    private FakeDatabase database;
    private JdbcConnectionProvider provider;
    private JdbcTransactionManager transactionManager;
    private SaleRepositoryJdbc repository;
    private Seller seller;
    private Customer customer;
    private BeerProduct costenita;
    private BeerProduct poker;

    @BeforeEach
    void setUp() {
        database = new FakeDatabase();
        provider = new JdbcConnectionProvider(
                new DatabaseConfig("jdbc:postgresql://localhost:5432/la_exclusiva_test", "test-user", "test-pass"),
                database::openConnection);
        transactionManager = new JdbcTransactionManager(provider);
        repository = new SaleRepositoryJdbc(provider, transactionManager);

        seller = new Seller("SEL-TEST", "Vendedora", "3001234567", "Calle 1");
        customer = new Customer("C-TEST", "Cliente", "3007654321", "Calle 2", BigDecimal.ZERO);
        costenita = catalogProduct("COSTENITA_175");
        poker = catalogProduct("POKER_750");
        database.addSeller(seller);
        database.addCustomer(customer);
        database.addProduct(costenita);
        database.addProduct(poker);
    }

    @Test
    void rejectsNullSaleAndAmountsOutsideNumericScaleBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        Sale invalidMoney = sale("SALE-MONEY", CREATED_AT,
                List.of(line(costenita, SalePresentation.FULL_BASKET, 1, new BigDecimal("85.001"), 38)),
                List.of(new SalePayment(new BigDecimal("85.001"), CREATED_AT)), customer);

        assertThrows(IllegalArgumentException.class, () -> repository.save(invalidMoney));
        assertEquals(0, database.connectionsOpened);
    }

    @Test
    void savePersistsHeaderMultipleLinesHistoricalSnapshotsAndInitialPaymentAtomically() {
        LocalDateTime paymentAt = CREATED_AT.plusMinutes(2).withNano(987_654_321);
        Sale sale = sale("SALE-SAVE-1", CREATED_AT,
                List.of(
                        line(costenita, SalePresentation.FULL_BASKET, 1, BigDecimal.valueOf(80_000), 36),
                        line(poker, SalePresentation.HALF_BASKET, 2, BigDecimal.valueOf(45_000), 30)),
                List.of(new SalePayment(BigDecimal.valueOf(20_000), paymentAt)), customer);

        repository.save(sale);

        assertEquals(1, database.sales.size());
        assertEquals(2, database.lines.size());
        assertEquals(1, database.payments.size());
        assertEquals(CREATED_AT.withNano(123_456_000), database.sales.get("SALE-SAVE-1").get("created_at"));
        assertEquals(36, database.lines.get(0).get("units_per_basket_snapshot"));
        assertEquals(BigDecimal.valueOf(80_000).setScale(2), database.lines.get(0).get("unit_price"));
        assertEquals(paymentAt.withNano(987_654_000), database.payments.get(0).get("occurred_at"));
        assertEquals(1, database.commits);
        assertEquals(0, database.rollbacks);
    }

    @Test
    void savesPaidSaleWithoutCustomerAndSaleWithoutInitialPayment() {
        Sale paid = sale("SALE-PAID", CREATED_AT,
                List.of(line(costenita, SalePresentation.UNIT, 1, BigDecimal.valueOf(2_500), 38)),
                List.of(new SalePayment(BigDecimal.valueOf(2_500), CREATED_AT)), null);
        Sale pending = sale("SALE-NO-PAYMENT", CREATED_AT.plusDays(1),
                List.of(line(poker, SalePresentation.UNIT, 2, BigDecimal.valueOf(3_000), 16)),
                List.of(), customer);

        repository.save(paid);
        repository.save(pending);

        assertEquals(2, database.sales.size());
        assertEquals(1, database.payments.size());
        assertEquals(null, database.sales.get("SALE-PAID").get("customer_id"));
        assertEquals("C-TEST", database.sales.get("SALE-NO-PAYMENT").get("customer_id"));
    }

    @Test
    void rejectsDuplicateSaleIdWithoutReplacingExistingHistory() {
        Sale original = simpleSale("SALE-DUP");
        repository.save(original);
        Sale duplicate = sale("SALE-DUP", CREATED_AT.plusDays(1),
                List.of(line(costenita, SalePresentation.FULL_BASKET, 1, BigDecimal.valueOf(90_000), 38)),
                List.of(), customer);

        assertThrows(IllegalArgumentException.class, () -> repository.save(duplicate));

        assertEquals(1, database.sales.size());
        assertEquals(CREATED_AT.withNano(123_456_000), database.sales.get("SALE-DUP").get("created_at"));
        assertEquals(1, database.lines.size());
        assertEquals(1, database.rollbacks);
    }

    @Test
    void rollsBackHeaderAndPriorLinesWhenALaterSqlWriteFails() {
        Sale sale = sale("SALE-FAIL", CREATED_AT,
                List.of(
                        line(costenita, SalePresentation.FULL_BASKET, 1, BigDecimal.valueOf(80_000), 38),
                        line(poker, SalePresentation.UNIT, 2, BigDecimal.valueOf(3_000), 16)),
                List.of(new SalePayment(BigDecimal.valueOf(1_000), CREATED_AT)), customer);
        database.failOnUpdateNumber = 4;

        assertThrows(JdbcPersistenceException.class, () -> repository.save(sale));

        assertTrue(database.sales.isEmpty());
        assertTrue(database.lines.isEmpty());
        assertTrue(database.payments.isEmpty());
        assertEquals(0, database.commits);
        assertEquals(1, database.rollbacks);
    }

    @Test
    void findByIdPreservesIdentityPriceCapacityAndPaymentDates() {
        Sale original = sale("SALE-READ", CREATED_AT,
                List.of(line(costenita, SalePresentation.FULL_BASKET, 2, BigDecimal.valueOf(70_000), 36)),
                List.of(
                        new SalePayment(BigDecimal.valueOf(50_000), CREATED_AT),
                        new SalePayment(BigDecimal.valueOf(20_000), CREATED_AT.plusDays(2))), customer);
        repository.save(original);

        Sale loaded = repository.findById("SALE-READ").orElseThrow();

        assertEquals("SALE-READ", loaded.getId());
        assertEquals(CREATED_AT.withNano(123_456_000), loaded.getCreatedAt());
        assertEquals(BigDecimal.valueOf(70_000).setScale(2), loaded.getLines().get(0).getUnitPrice());
        assertEquals(36, loaded.getLines().get(0).getUnitsPerBasketSnapshot());
        assertEquals(CREATED_AT.plusDays(2).withNano(123_456_000), loaded.getPayments().get(1).getTimestamp());
        assertEquals(BigDecimal.valueOf(70_000).setScale(2), loaded.calculateBalance());
    }

    @Test
    void findByIdReturnsEmptyForMissingIdAndRejectsBlankId() {
        assertTrue(repository.findById("SALE-MISSING").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repository.findById(" "));
    }

    @Test
    void findAllLoadsSalesLinesAndPaymentsWithoutCartesianDuplicationInStableOrder() {
        Sale later = sale("SALE-Z", CREATED_AT.plusDays(1),
                List.of(line(costenita, SalePresentation.UNIT, 2, BigDecimal.valueOf(2_500), 38),
                        line(poker, SalePresentation.UNIT, 1, BigDecimal.valueOf(3_000), 16)),
                List.of(new SalePayment(BigDecimal.valueOf(5_000), CREATED_AT.plusDays(1))), customer);
        Sale earlier = simpleSale("SALE-A");
        repository.save(later);
        repository.save(earlier);

        List<Sale> found = repository.findAll();

        assertEquals(List.of("SALE-A", "SALE-Z"), found.stream().map(Sale::getId).toList());
        assertEquals(2, found.get(1).getLines().size());
        assertEquals(1, found.get(1).getPayments().size());
        assertEquals(1, found.get(0).getLines().size());
        assertEquals(3, database.executedSql.stream().filter(sql -> sql.startsWith("select")).count());
    }

    @Test
    void appendPaymentIsIdempotentAndLocksTheSaleRow() {
        Sale original = simpleSale("SALE-UPDATE");
        repository.save(original);
        LocalDateTime originalTime = LocalDateTime.of(2026, 11, 12, 9, 0);
        SaleRepositoryJdbc firstAttempt = repositoryWithClock(originalTime);
        SaleRepositoryJdbc laterRetry = repositoryWithClock(originalTime.plusDays(1));
        SalePayment first = firstAttempt.appendPayment("SALE-UPDATE", BigDecimal.valueOf(5_000), "retry-key");
        SalePayment retry = laterRetry.appendPayment("SALE-UPDATE", BigDecimal.valueOf(5_000), "retry-key");

        assertEquals(2, database.payments.size());
        assertTrue(database.executedSql.stream().anyMatch(sql -> sql.contains("for update of s")));
        assertEquals(first.getTimestamp(), retry.getTimestamp());
        assertEquals(originalTime, retry.getTimestamp());
        assertEquals(BigDecimal.valueOf(5_000).setScale(2), retry.getAmount());
    }

    @Test
    void distinctKeysAllowLegitimatePaymentsWithEqualAmountAndTimestamp() {
        Sale original = sale("SALE-SAME-PAYMENTS", CREATED_AT,
                List.of(line(costenita, SalePresentation.UNIT, 1, BigDecimal.valueOf(10_000), 38)),
                List.of(), customer);
        repository.save(original);
        LocalDateTime fixedTime = LocalDateTime.of(2026, 11, 13, 12, 0);
        SaleRepositoryJdbc fixedClockRepository = new SaleRepositoryJdbc(provider, transactionManager,
                Clock.fixed(fixedTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        SalePayment first = fixedClockRepository.appendPayment(original.getId(), BigDecimal.valueOf(2_000), "payment-a");
        SalePayment second = fixedClockRepository.appendPayment(original.getId(), BigDecimal.valueOf(2_000), "payment-b");

        assertEquals(2, database.payments.size());
        assertEquals(first.getTimestamp(), second.getTimestamp());
        assertEquals(fixedTime, first.getTimestamp());
    }

    @Test
    void updateRejectsSnapshotsAsAmbiguousIncludingStaleSnapshots() {
        Sale original = sale("SALE-IMMUTABLE", CREATED_AT,
                List.of(line(costenita, SalePresentation.UNIT, 1, BigDecimal.valueOf(10_000), 38)),
                List.of(new SalePayment(BigDecimal.valueOf(2_000), CREATED_AT)), customer);
        repository.save(original);
        Sale withoutOldPayment = sale("SALE-IMMUTABLE", CREATED_AT,
                original.getLines(), List.of(), customer);
        Sale changedLine = sale("SALE-IMMUTABLE", CREATED_AT,
                List.of(line(costenita, SalePresentation.UNIT, 1, BigDecimal.valueOf(9_000), 38)),
                original.getPayments(), customer);

        assertThrows(UnsupportedOperationException.class, () -> repository.update(withoutOldPayment));
        assertThrows(UnsupportedOperationException.class, () -> repository.update(changedLine));
        assertThrows(UnsupportedOperationException.class, () -> repository.update(simpleSale("SALE-MISSING")));
        assertEquals(1, database.payments.size());
        assertEquals(1, database.lines.size());
    }

    @Test
    void appendPaymentRejectsAnAbonoAboveTheOutstandingBalance() {
        Sale sale = simpleSale("SALE-OVERPAY");
        repository.save(sale);
        assertThrows(IllegalArgumentException.class,
                () -> repository.appendPayment(sale.getId(), BigDecimal.valueOf(75_001), "too-much"));
        assertEquals(1, database.payments.size());
    }

    @Test
    void rejectsReusedKeyWithDifferentAmountAndNonpositiveAmounts() {
        Sale sale = simpleSale("SALE-KEY-CONFLICT");
        repository.save(sale);
        repository.appendPayment(sale.getId(), BigDecimal.valueOf(5_000), "stable-key");
        assertThrows(IllegalArgumentException.class,
                () -> repository.appendPayment(sale.getId(), BigDecimal.valueOf(6_000), "stable-key"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.appendPayment(sale.getId(), BigDecimal.ZERO, "zero"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.appendPayment(sale.getId(), BigDecimal.valueOf(-1), "negative"));
        assertEquals(2, database.payments.size());
    }

    @Test
    void joinsOuterTransactionAndRollsBackPaymentWhenLaterSqlFails() {
        Sale sale = simpleSale("SALE-APPENDER-OUTER");
        assertThrows(IllegalStateException.class, () -> transactionManager.inTransaction(() -> {
            repository.save(sale);
            repository.appendPayment(sale.getId(), BigDecimal.valueOf(1_000), "outer-key");
            throw new IllegalStateException("fallo posterior");
        }));
        assertTrue(database.sales.isEmpty());
        assertTrue(database.payments.isEmpty());
        assertEquals(1, database.rollbacks);
        assertEquals(0, database.commits);
    }

    @Test
    void rollsBackWhenPaymentInsertFailsWithSqlError() {
        Sale sale = simpleSale("SALE-APPENDER-FAIL");
        repository.save(sale);
        database.failOnUpdateNumber = database.updateStatements + 1;
        assertThrows(JdbcPersistenceException.class,
                () -> repository.appendPayment(sale.getId(), BigDecimal.valueOf(1_000), "sql-failure"));
        assertEquals(1, database.payments.size());
        assertEquals(1, database.rollbacks);
    }

    @Test
    void refusesPhysicalDeletionAndRollsBackAnOuterTransaction() {
        Sale sale = simpleSale("SALE-OUTER");
        assertThrows(UnsupportedOperationException.class, () -> repository.deleteById(sale.getId()));

        assertThrows(IllegalStateException.class, () -> transactionManager.inTransaction(() -> {
            repository.save(sale);
            throw new IllegalStateException("fallo posterior en el caso de uso");
        }));

        assertTrue(database.sales.isEmpty());
        assertEquals(1, database.rollbacks);
        assertEquals(0, database.commits);
    }

    @Test
    void findByIdTreatsTheSaleIdAsAParameterEvenWhenItContainsSqlSyntax() {
        String unusualId = "SALE-X' OR 1=1 --";
        repository.save(simpleSale(unusualId));

        assertTrue(repository.findById(unusualId).isPresent());
        assertTrue(database.executedSql.stream().filter(sql -> sql.contains("where s.id = ?"))
                .noneMatch(sql -> sql.contains(unusualId.toLowerCase(java.util.Locale.ROOT))));
    }

    private Sale simpleSale(String id) {
        return sale(id, CREATED_AT,
                List.of(line(costenita, SalePresentation.FULL_BASKET, 1, BigDecimal.valueOf(85_000), 38)),
                List.of(new SalePayment(BigDecimal.valueOf(10_000), CREATED_AT)), customer);
    }

    private SaleRepositoryJdbc repositoryWithClock(LocalDateTime now) {
        return new SaleRepositoryJdbc(provider, transactionManager,
                Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private static Sale sale(String id, LocalDateTime createdAt, List<SaleLine> lines,
                             List<SalePayment> payments, Customer customer) {
        return Sale.reconstitute(id, createdAt,
                new Seller("SEL-TEST", "Vendedora", "3001234567", "Calle 1"), customer, lines, payments);
    }

    private static SaleLine line(BeerProduct product, SalePresentation presentation, int quantity,
                                 BigDecimal price, int basketSnapshot) {
        return SaleLine.reconstitute(product, presentation, quantity, price, basketSnapshot);
    }

    private static BeerProduct catalogProduct(String id) {
        return InitialCatalog.create().stream().filter(product -> product.getId().equals(id))
                .findFirst().orElseThrow();
    }

    /** Fake driver pequeño que interpreta únicamente el SQL parametrizado emitido por el adaptador. */
    private static final class FakeDatabase {
        private final Map<String, Map<String, Object>> sales = new LinkedHashMap<>();
        private final List<Map<String, Object>> lines = new ArrayList<>();
        private final List<Map<String, Object>> payments = new ArrayList<>();
        private final Map<String, Map<String, Object>> sellers = new HashMap<>();
        private final Map<String, Map<String, Object>> customers = new HashMap<>();
        private final Map<String, Map<String, Object>> products = new HashMap<>();
        private final List<String> executedSql = new ArrayList<>();
        private int nextLineId = 1;
        private int nextPaymentId = 1;
        private int connectionsOpened;
        private int commits;
        private int rollbacks;
        private int updateStatements;
        private int failOnUpdateNumber = -1;

        private void addSeller(Seller seller) {
            sellers.put(seller.getId(), row("id", seller.getId(), "name", seller.getName(),
                    "phone", seller.getPhone(), "address", seller.getAddress()));
        }

        private void addCustomer(Customer customer) {
            customers.put(customer.getId(), row("id", customer.getId(), "name", customer.getName(),
                    "phone", customer.getPhone(), "address", customer.getAddress(),
                    "balance", customer.getBalance()));
        }

        private void addProduct(BeerProduct product) {
            products.put(product.getId(), row("id", product.getId(), "name", product.getName(),
                    "volume_ml", product.getVolumeMl(), "units_per_basket", product.getUnitsPerBasket(),
                    "container_type", product.getContainerType().name(),
                    "container_group", product.getContainerGroup()));
        }

        private Connection openConnection() {
            connectionsOpened++;
            InvocationHandler handler = new InvocationHandler() {
                private Map<String, Map<String, Object>> salesBefore;
                private List<Map<String, Object>> linesBefore;
                private List<Map<String, Object>> paymentsBefore;
                private boolean autoCommit = true;
                private boolean closed;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    return switch (method.getName()) {
                        case "prepareStatement" -> preparedStatement((String) args[0]);
                        case "setAutoCommit" -> {
                            autoCommit = (boolean) args[0];
                            if (!autoCommit) {
                                salesBefore = copyRows(sales);
                                linesBefore = copyRows(lines);
                                paymentsBefore = copyRows(payments);
                            }
                            yield null;
                        }
                        case "getAutoCommit" -> autoCommit;
                        case "commit" -> {
                            commits++;
                            salesBefore = null;
                            linesBefore = null;
                            paymentsBefore = null;
                            yield null;
                        }
                        case "rollback" -> {
                            rollbacks++;
                            if (salesBefore != null) {
                                sales.clear();
                                sales.putAll(salesBefore);
                                lines.clear();
                                lines.addAll(linesBefore);
                                payments.clear();
                                payments.addAll(paymentsBefore);
                            }
                            yield null;
                        }
                        case "close" -> { closed = true; yield null; }
                        case "isClosed" -> closed;
                        case "isWrapperFor" -> false;
                        case "unwrap" -> throw new SQLException("No se admite unwrap en el controlador simulado.");
                        case "toString" -> "Fake JDBC Connection";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return proxy(Connection.class, handler);
        }

        private PreparedStatement preparedStatement(String sql) {
            String normalized = sql.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
            executedSql.add(normalized);
            InvocationHandler handler = new InvocationHandler() {
                private final Map<Integer, Object> parameters = new HashMap<>();
                private boolean closed;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String name = method.getName();
                    if (name.startsWith("set") && args != null && args.length >= 2
                            && args[0] instanceof Integer index) {
                        parameters.put(index, name.equals("setNull") ? null : args[1]);
                        return null;
                    }
                    return switch (name) {
                        case "executeUpdate" -> executeUpdate(normalized, parameters);
                        case "executeQuery" -> resultSet(query(normalized, parameters));
                        case "close" -> { closed = true; yield null; }
                        case "isClosed" -> closed;
                        case "toString" -> "Fake JDBC PreparedStatement: " + normalized;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return proxy(PreparedStatement.class, handler);
        }

        private int executeUpdate(String sql, Map<Integer, Object> values) throws SQLException {
            updateStatements++;
            if (updateStatements == failOnUpdateNumber) {
                throw new SQLException("fallo SQL simulado", "XX000");
            }
            if (sql.startsWith("insert into sale (")) {
                String id = (String) values.get(1);
                if (sales.containsKey(id)) {
                    throw new SQLException("duplicate key", "23505");
                }
                sales.put(id, row("sale_id", id, "created_at", values.get(2),
                        "seller_id", values.get(3), "customer_id", values.get(4)));
                return 1;
            }
            if (sql.startsWith("insert into sale_line")) {
                lines.add(row("id", nextLineId++, "sale_id", values.get(1), "product_id", values.get(2),
                        "presentation_code", values.get(3), "quantity", values.get(4),
                        "units_per_basket_snapshot", values.get(5), "unit_price", values.get(6)));
                return 1;
            }
            if (sql.startsWith("insert into sale_payment")) {
                Object key = values.get(4);
                if (key != null && payments.stream().anyMatch(payment ->
                        payment.get("sale_id").equals(values.get(1)) && key.equals(payment.get("idempotency_key")))) {
                    throw new SQLException("duplicate idempotency key", "23505");
                }
                payments.add(row("id", nextPaymentId++, "sale_id", values.get(1),
                        "amount", values.get(2), "occurred_at", values.get(3), "idempotency_key", key));
                return 1;
            }
            throw new SQLException("SQL de escritura no soportado por fake: " + sql);
        }

        private List<Map<String, Object>> query(String sql, Map<Integer, Object> parameters) throws SQLException {
            if (sql.startsWith("select amount, occurred_at from sale_payment where sale_id = ?")) {
                return payments.stream().filter(payment -> payment.get("sale_id").equals(parameters.get(1))
                                && java.util.Objects.equals(payment.get("idempotency_key"), parameters.get(2)))
                        .map(payment -> row("amount", payment.get("amount"),
                                "occurred_at", payment.get("occurred_at"))).toList();
            }
            if (sql.startsWith("select coalesce((select sum(quantity * unit_price)")) {
                String saleId = (String) parameters.get(1);
                BigDecimal total = lines.stream().filter(line -> line.get("sale_id").equals(saleId))
                        .map(line -> ((BigDecimal) line.get("unit_price")).multiply(
                                BigDecimal.valueOf((Integer) line.get("quantity"))))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal paid = payments.stream().filter(payment -> payment.get("sale_id").equals(saleId))
                        .map(payment -> (BigDecimal) payment.get("amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                return List.of(row("total", total, "paid", paid));
            }
            if (sql.contains("from sale s")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                if (sql.contains("where s.id = ?")) {
                    Map<String, Object> sale = sales.get(parameters.get(1));
                    if (sale != null) rows.add(headerRow(sale));
                } else {
                    List<Map<String, Object>> orderedSales = sales.values().stream()
                            .sorted(Comparator.comparing((Map<String, Object> sale) -> (LocalDateTime) sale.get("created_at"))
                                    .thenComparing(sale -> (String) sale.get("sale_id")))
                            .toList();
                    for (Map<String, Object> sale : orderedSales) {
                        rows.add(headerRow(sale));
                    }
                }
                return rows;
            }
            if (sql.contains("from sale_line l")) {
                List<String> ids = parameters.values().stream().map(String.class::cast).toList();
                return lines.stream().filter(line -> ids.contains(line.get("sale_id")))
                        .sorted(Comparator.comparing((Map<String, Object> line) -> (String) line.get("sale_id"))
                                .thenComparing(line -> (Integer) line.get("id")))
                        .map(this::lineRow).toList();
            }
            if (sql.contains("from sale_payment")) {
                List<String> ids = parameters.values().stream().map(String.class::cast).toList();
                return payments.stream().filter(payment -> ids.contains(payment.get("sale_id")))
                        .sorted(Comparator.comparing((Map<String, Object> payment) -> (String) payment.get("sale_id"))
                                .thenComparing(payment -> (LocalDateTime) payment.get("occurred_at"))
                                .thenComparing(payment -> (Integer) payment.get("id")))
                        .map(payment -> row("sale_id", payment.get("sale_id"), "amount", payment.get("amount"),
                                "occurred_at", payment.get("occurred_at"))).toList();
            }
            throw new SQLException("SQL de consulta no soportado por fake: " + sql);
        }

        private Map<String, Object> headerRow(Map<String, Object> sale) throws SQLException {
            Map<String, Object> seller = sellers.get(sale.get("seller_id"));
            if (seller == null) throw new SQLException("seller FK no encontrada");
            Map<String, Object> customer = customers.get(sale.get("customer_id"));
            Map<String, Object> row = row("sale_id", sale.get("sale_id"), "created_at", sale.get("created_at"),
                    "seller_id", seller.get("id"), "seller_name", seller.get("name"),
                    "seller_phone", seller.get("phone"), "seller_address", seller.get("address"));
            if (customer != null) {
                row.putAll(row("customer_id", customer.get("id"), "customer_name", customer.get("name"),
                        "customer_phone", customer.get("phone"), "customer_address", customer.get("address"),
                        "customer_balance", customer.get("balance")));
            }
            return row;
        }

        private Map<String, Object> lineRow(Map<String, Object> line) {
            Map<String, Object> product = products.get(line.get("product_id"));
            if (product == null) throw new IllegalStateException("Producto de prueba no configurado.");
            Map<String, Object> row = new HashMap<>(line);
            row.put("product_name", product.get("name"));
            row.put("volume_ml", product.get("volume_ml"));
            row.put("units_per_basket", product.get("units_per_basket"));
            row.put("container_type", product.get("container_type"));
            row.put("container_group", product.get("container_group"));
            return row;
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            InvocationHandler handler = new InvocationHandler() {
                private int position = -1;
                private boolean closed;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if (name.equals("next")) return ++position < rows.size();
                    if (name.equals("close")) { closed = true; return null; }
                    if (name.equals("isClosed")) return closed;
                    if (name.equals("toString")) return "Fake JDBC ResultSet";
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.equals("getString") || name.equals("getInt") || name.equals("getBigDecimal")
                            || name.equals("getObject")) {
                        Object value = rows.get(position).get(args[0]);
                        if (name.equals("getString")) return value == null ? null : value.toString();
                        if (name.equals("getInt")) return value == null ? 0 : ((Number) value).intValue();
                        if (name.equals("getBigDecimal")) return value;
                        if (name.equals("getObject") && args.length == 2 && value instanceof Timestamp timestamp) {
                            value = timestamp.toLocalDateTime();
                        }
                        return value;
                    }
                    return defaultValue(method.getReturnType());
                }
            };
            return proxy(ResultSet.class, handler);
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private static Map<String, Object> row(Object... entries) {
            Map<String, Object> row = new HashMap<>();
            for (int index = 0; index < entries.length; index += 2) {
                row.put((String) entries[index], entries[index + 1]);
            }
            return row;
        }

        private static Map<String, Map<String, Object>> copyRows(Map<String, Map<String, Object>> source) {
            Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(key, new HashMap<>(value)));
            return copy;
        }

        private static List<Map<String, Object>> copyRows(List<Map<String, Object>> source) {
            List<Map<String, Object>> copy = new ArrayList<>(source.size());
            source.forEach(row -> copy.add(new HashMap<>(row)));
            return copy;
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
