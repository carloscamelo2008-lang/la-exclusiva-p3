package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.CustomerBalanceAdjuster;
import com.laexclusiva.repository.CustomerDebtQuery;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.repository.IdempotentSalePaymentAppender;
import com.laexclusiva.repository.InventoryStockAdjuster;
import com.laexclusiva.repository.ProductRepository;
import com.laexclusiva.repository.SaleRepository;
import com.laexclusiva.repository.SellerRepositoryInterface;
import com.laexclusiva.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas simuladas del orquestador transaccional de ventas JDBC. */
class JdbcSalesWorkflowTest {
    private World world;
    private JdbcSalesWorkflow workflow;

    @BeforeEach
    void setUp() {
        world = new World();
        ProductRepository products = new Products(world);
        CustomerRepositoryInterface customers = new Customers(world);
        SellerRepositoryInterface sellers = new Sellers(world);
        SaleRepository sales = new Sales(world);
        world.salesRepository = (Sales) sales;
        workflow = new JdbcSalesWorkflow(products, customers, sellers, sales,
                new Payments(world, world.salesRepository), new DebtQuery(world),
                new Balances(world), new Stock(world), new Movements(world),
                new SnapshotTransaction(world));
        world.products.put("P1", product("P1"));
        world.sellers.put("SEL-1", new Seller("SEL-1", "Vendedora", "3000000000", "Dirección"));
    }

    @Test
    void registersCashSaleWithHistoricalPriceInventoryAndMovement() {
        world.stock.put("P1", 5);

        Sale saved = workflow.registerSale(List.of(new StockRequest("P1", SalePresentation.UNIT, 1)),
                "SEL-1", null, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), saved.calculateTotal());
        assertEquals(0, saved.calculateBalance().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("100.00"), saved.getLines().get(0).getUnitPrice());
        assertEquals(4, world.stock.get("P1"));
        assertEquals(1, world.movements.size());
        assertEquals(MovementType.EXIT, world.movements.get(0).type());
        assertEquals(saved.getId(), workflow.findById(saved.getId()).getId());
        assertEquals(1, world.transaction.commits);
    }

    @Test
    void creditSaleAndIdempotentPaymentPreserveGeneralDebtSeparately() {
        world.stock.put("P1", 5);
        world.customers.put("C-1", new Customer("C-1", "Cliente", "3000000001", "Dirección",
                new BigDecimal("100.00")));

        Sale sale = workflow.registerSale(List.of(new StockRequest("P1", SalePresentation.UNIT, 1)),
                "SEL-1", "C-1", new BigDecimal("20.00"));
        assertEquals(new BigDecimal("180.00"), world.customers.get("C-1").getBalance());
        assertEquals(new BigDecimal("80.00"), workflow.outstandingSalesDebt("C-1"));

        Sale paid = workflow.registerPayment(sale.getId(), new BigDecimal("30.00"), "payment-retry-key");
        Sale retry = workflow.registerPayment(sale.getId(), new BigDecimal("30.00"), "payment-retry-key");
        assertEquals(new BigDecimal("150.00"), world.customers.get("C-1").getBalance());
        assertEquals(new BigDecimal("50.00"), workflow.outstandingSalesDebt("C-1"));
        assertEquals(new BigDecimal("30.00"), paid.getPayments().get(paid.getPayments().size() - 1).getAmount());
        assertEquals(paid.getPayments().size(), retry.getPayments().size());
        assertEquals(new BigDecimal("100.00"), world.customers.get("C-1").getBalance()
                .subtract(workflow.outstandingSalesDebt("C-1")));
        assertTrue(workflow.supportsDurableIdempotency());

        assertThrows(IllegalArgumentException.class,
                () -> workflow.registerPayment(sale.getId(), new BigDecimal("31.00"), "payment-retry-key"));
        assertEquals(new BigDecimal("150.00"), world.customers.get("C-1").getBalance());
    }

    @Test
    void insufficientStockRollsBackEarlierProductAdjustmentsAndMovements() {
        world.products.put("P2", product("P2"));
        world.stock.put("P1", 1);
        world.stock.put("P2", 0);
        List<StockRequest> lines = List.of(
                new StockRequest("P1", SalePresentation.UNIT, 1),
                new StockRequest("P2", SalePresentation.UNIT, 1));

        assertThrows(InsufficientStockException.class,
                () -> workflow.registerSale(lines, "SEL-1", null, new BigDecimal("200.00")));

        assertEquals(1, world.stock.get("P1"));
        assertEquals(0, world.stock.get("P2"));
        assertTrue(world.movements.isEmpty());
        assertTrue(world.sales.isEmpty());
        assertEquals(1, world.transaction.rollbacks);
    }

    @Test
    void saleFailureAfterBalanceAndMovementWritesRollsBackWholeUnit() {
        world.stock.put("P1", 2);
        world.customers.put("C-1", new Customer("C-1", "Cliente", "3000000001", "Dirección",
                new BigDecimal("40.00")));
        world.failSaleSave = true;

        assertThrows(IllegalStateException.class,
                () -> workflow.registerSale(List.of(new StockRequest("P1", SalePresentation.UNIT, 1)),
                        "SEL-1", "C-1", BigDecimal.ZERO));

        assertEquals(2, world.stock.get("P1"));
        assertTrue(world.movements.isEmpty());
        assertTrue(world.sales.isEmpty());
        assertEquals(new BigDecimal("40.00"), world.customers.get("C-1").getBalance());
        assertEquals(1, world.transaction.rollbacks);
    }

    @Test
    void customerDebtCannotBeConsumedByAnInconsistentSalesLedger() {
        world.stock.put("P1", 1);
        Customer customer = new Customer("C-1", "Cliente", "3000000001", "Dirección", BigDecimal.ZERO);
        world.customers.put("C-1", customer);
        Sale existing = new Sale(LocalDateTime.now(), world.sellers.get("SEL-1"), customer,
                List.of(new com.laexclusiva.model.SaleLine(world.products.get("P1"), SalePresentation.UNIT, 1)),
                BigDecimal.ZERO);
        world.sales.put(existing.getId(), existing);

        assertThrows(IllegalStateException.class,
                () -> workflow.registerSale(List.of(new StockRequest("P1", SalePresentation.UNIT, 1)),
                        "SEL-1", "C-1", BigDecimal.ZERO));
        assertEquals(1, world.stock.get("P1"));
        assertTrue(world.movements.isEmpty());
    }

    private static BeerProduct product(String id) {
        BeerProduct product = new BeerProduct(id, "Producto " + id, 330, 24,
                ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal("100.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("1000.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("2000.00"));
        return product;
    }

    private static final class World {
        private final Map<String, BeerProduct> products = new HashMap<>();
        private final Map<String, Customer> customers = new HashMap<>();
        private final Map<String, Seller> sellers = new HashMap<>();
        private final Map<String, Integer> stock = new HashMap<>();
        private final Map<String, Sale> sales = new HashMap<>();
        private final List<StockMovement> movements = new ArrayList<>();
        private final Map<String, BigDecimal> paymentKeys = new HashMap<>();
        private Sales salesRepository;
        private SnapshotTransaction transaction;
        private boolean failSaleSave;

        private Snapshot snapshot() {
            Map<String, Customer> customerCopy = new HashMap<>();
            customers.forEach((id, customer) -> customerCopy.put(id, customer.copy()));
            Map<String, Sale> saleCopy = new HashMap<>();
            sales.forEach((id, sale) -> saleCopy.put(id, sale.copy()));
            return new Snapshot(new HashMap<>(stock), customerCopy, saleCopy,
                    new ArrayList<>(movements), new HashMap<>(paymentKeys));
        }

        private void restore(Snapshot snapshot) {
            stock.clear(); stock.putAll(snapshot.stock());
            customers.clear(); customers.putAll(snapshot.customers());
            sales.clear(); sales.putAll(snapshot.sales());
            movements.clear(); movements.addAll(snapshot.movements());
            paymentKeys.clear(); paymentKeys.putAll(snapshot.paymentKeys());
        }
    }

    private record Snapshot(Map<String, Integer> stock, Map<String, Customer> customers,
                            Map<String, Sale> sales, List<StockMovement> movements,
                            Map<String, BigDecimal> paymentKeys) { }

    private static final class SnapshotTransaction implements TransactionBoundary {
        private final World world;
        private int commits;
        private int rollbacks;
        private SnapshotTransaction(World world) { this.world = world; world.transaction = this; }
        @Override public <T> T inTransaction(Supplier<T> operation) {
            Snapshot snapshot = world.snapshot();
            try {
                T result = operation.get();
                commits++;
                return result;
            } catch (RuntimeException | Error failure) {
                world.restore(snapshot);
                rollbacks++;
                throw failure;
            }
        }
    }

    private static final class Products implements ProductRepository {
        private final World world;
        private Products(World world) { this.world = world; }
        @Override public void save(BeerProduct product) { world.products.put(product.getId(), product.copy()); }
        @Override public void updatePrice(String id, SalePresentation presentation, BigDecimal price) { throw new UnsupportedOperationException(); }
        @Override public void addMissingPrice(String id, SalePresentation presentation, BigDecimal price) { throw new UnsupportedOperationException(); }
        @Override public Optional<BeerProduct> findById(String id) { return Optional.ofNullable(world.products.get(id)).map(BeerProduct::copy); }
        @Override public List<BeerProduct> findAll() { return world.products.values().stream().map(BeerProduct::copy).toList(); }
        @Override public boolean existsById(String id) { return world.products.containsKey(id); }
    }

    private static final class Customers implements CustomerRepositoryInterface {
        private final World world;
        private Customers(World world) { this.world = world; }
        @Override public void save(Customer customer) { world.customers.put(customer.getId(), customer.copy()); }
        @Override public Customer findById(String id) { Customer customer = world.customers.get(id); return customer == null ? null : customer.copy(); }
        @Override public List<Customer> findAll() { return world.customers.values().stream().map(Customer::copy).toList(); }
        @Override public boolean existsById(String id) { return world.customers.containsKey(id); }
        @Override public void update(Customer customer) { world.customers.put(customer.getId(), customer.copy()); }
    }

    private static final class Sellers implements SellerRepositoryInterface {
        private final World world;
        private Sellers(World world) { this.world = world; }
        @Override public void save(Seller seller) { world.sellers.put(seller.getId(), seller); }
        @Override public Optional<Seller> findById(String id) { return Optional.ofNullable(world.sellers.get(id)); }
        @Override public List<Seller> findAll() { return new ArrayList<>(world.sellers.values()); }
    }

    private static final class Sales implements SaleRepository {
        private final World world;
        private Sales(World world) { this.world = world; }
        @Override public void save(Sale sale) {
            if (world.sales.putIfAbsent(sale.getId(), sale.copy()) != null) throw new IllegalArgumentException("Venta duplicada.");
            if (world.failSaleSave) { world.failSaleSave = false; throw new IllegalStateException("Fallo simulado al guardar venta."); }
        }
        @Override public void update(Sale sale) { if (!world.sales.containsKey(sale.getId())) throw new IllegalArgumentException("Venta ausente."); world.sales.put(sale.getId(), sale.copy()); }
        @Override public void deleteById(String id) { world.sales.remove(id); }
        @Override public Optional<Sale> findById(String id) { return Optional.ofNullable(world.sales.get(id)).map(Sale::copy); }
        @Override public List<Sale> findAll() { return world.sales.values().stream().map(Sale::copy).toList(); }
    }

    private static final class Payments implements IdempotentSalePaymentAppender {
        private final World world;
        private final Sales sales;
        private Payments(World world, Sales sales) { this.world = world; this.sales = sales; }
        @Override public AppendResult appendPaymentOnce(String saleId, BigDecimal amount, String key) {
            String compoundKey = saleId + ":" + key;
            BigDecimal existing = world.paymentKeys.get(compoundKey);
            if (existing != null) {
                if (existing.compareTo(amount) != 0) throw new IllegalArgumentException("Clave con importe distinto.");
                Sale current = sales.findById(saleId).orElseThrow();
                List<SalePayment> currentPayments = current.getPayments();
                return new AppendResult(currentPayments.get(currentPayments.size() - 1), false);
            }
            Sale updated = sales.findById(saleId).orElseThrow();
            SalePayment payment = new SalePayment(amount, LocalDateTime.now());
            updated.registerPayment(payment);
            sales.update(updated);
            world.paymentKeys.put(compoundKey, amount);
            return new AppendResult(payment, true);
        }
    }

    private static final class DebtQuery implements CustomerDebtQuery {
        private final World world;
        private DebtQuery(World world) { this.world = world; }
        @Override public BigDecimal findOutstandingSalesDebt(String customerId) {
            return world.sales.values().stream()
                    .filter(sale -> sale.getCustomer() != null && customerId.equals(sale.getCustomer().getId()))
                    .map(Sale::calculateBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static final class Balances implements CustomerBalanceAdjuster {
        private final World world;
        private Balances(World world) { this.world = world; }
        @Override public BigDecimal increaseBalance(String id, BigDecimal amount) {
            Customer old = requireCustomer(id);
            BigDecimal balance = old.getBalance().add(amount);
            world.customers.put(id, withBalance(old, balance));
            return balance;
        }
        @Override public BigDecimal decreaseBalance(String id, BigDecimal amount) {
            Customer old = requireCustomer(id);
            if (old.getBalance().compareTo(amount) < 0) throw new IllegalArgumentException("Saldo insuficiente.");
            BigDecimal balance = old.getBalance().subtract(amount);
            world.customers.put(id, withBalance(old, balance));
            return balance;
        }
        private Customer requireCustomer(String id) {
            Customer customer = world.customers.get(id);
            if (customer == null) throw new IllegalArgumentException("Cliente ausente.");
            return customer;
        }
        private static Customer withBalance(Customer c, BigDecimal balance) {
            return new Customer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), balance);
        }
    }

    private static final class Stock implements InventoryStockAdjuster {
        private final World world;
        private Stock(World world) { this.world = world; }
        @Override public int increaseStock(String id, int units) { return world.stock.merge(id, units, Math::addExact); }
        @Override public int decreaseStock(String id, int units) {
            int available = world.stock.getOrDefault(id, 0);
            if (available < units) throw new InsufficientStockException(id, units, available);
            int remaining = available - units;
            world.stock.put(id, remaining);
            return remaining;
        }
    }

    private static final class Movements implements StockMovementRepository {
        private final World world;
        private Movements(World world) { this.world = world; }
        @Override public void save(StockMovement movement) { world.movements.add(movement); }
        @Override public List<StockMovement> findAll() { return List.copyOf(world.movements); }
        @Override public List<StockMovement> findByProductId(String productId) {
            return world.movements.stream().filter(movement -> movement.productId().equals(productId)).toList();
        }
    }
}
