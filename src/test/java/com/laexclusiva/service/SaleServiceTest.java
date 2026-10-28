package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemorySaleRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.SellerRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaleServiceTest {

    private FailingSaleRepository saleRepository;
    private FailingCustomerRepository customerRepository;
    private FailingMovementRepository movementRepository;
    private ProductService products;
    private InventoryService inventory;
    private CustomerService customers;
    private SellerService sellers;
    private SaleService sales;
    private Seller seller;

    @BeforeEach
    void setUp() {
        products = new ProductService(new InMemoryProductRepository());
        products.registerAll(InitialCatalog.create());
        movementRepository = new FailingMovementRepository();
        inventory = new InventoryService(products, new InMemoryInventoryRepository(), movementRepository);
        customerRepository = new FailingCustomerRepository();
        customers = new CustomerService(customerRepository);
        sellers = new SellerService(new SellerRepository());
        saleRepository = new FailingSaleRepository();
        sales = new SaleService(saleRepository, products, inventory, customers, sellers);
        seller = sellers.registerSeller("Vendedora", "3001234567", "Calle 1");
        customers.registerCustomer("C001", "Cliente", "3007654321", "Calle 2");
    }

    @Test
    void registersMixedSaleUsingConfiguredPricesAndAddsOnlyUnpaidBalanceAsDebt() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        inventory.addStock("POKER_750", 1, 0, "Inicial");
        inventory.addStock("POKER_250", 0, 10, "Inicial");

        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1),
                request("POKER_750", SalePresentation.HALF_BASKET, 2),
                request("POKER_250", SalePresentation.UNIT, 3)),
                seller.getId(), "C001", BigDecimal.valueOf(100_000L));

        assertEquals(3, sale.getLines().size());
        assertEquals(BigDecimal.valueOf(85_000L), sale.getLines().get(0).getSubtotal());
        assertEquals(BigDecimal.valueOf(90_000L), sale.getLines().get(1).getSubtotal());
        assertEquals(BigDecimal.valueOf(7_500L), sale.getLines().get(2).getSubtotal());
        assertEquals(BigDecimal.valueOf(182_500L), sale.calculateTotal());
        assertEquals(BigDecimal.valueOf(100_000L), sale.calculateTotalPaid());
        assertEquals(BigDecimal.valueOf(82_500L), sale.calculateBalance());
        assertEquals(BigDecimal.valueOf(82_500L), customers.getCustomerById("C001").getBalance());
        assertNotSame(sale, sales.findById(sale.getId()));
        assertEquals(1, sales.listAll().size());
        assertEquals(0, inventory.availableUnits("COSTENITA_175"));
        assertEquals(0, inventory.availableUnits("POKER_750"));
        assertEquals(7, inventory.availableUnits("POKER_250"));
    }

    @Test
    void acceptsFullyPaidSaleWithoutCustomer() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");

        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), null, BigDecimal.valueOf(85_000L));

        assertEquals(null, sale.getCustomer());
        assertEquals(BigDecimal.ZERO, sale.calculateBalance());
        assertEquals(BigDecimal.ZERO, customers.getCustomerById("C001").getBalance());
        assertEquals(1, sale.getPayments().size());
    }

    @Test
    void rejectsUnknownProductSellerOrRequiredCustomerBeforeChangingState() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        List<StockRequest> knownProduct = List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1));

        assertThrows(IllegalArgumentException.class, () -> sales.registerSale(
                List.of(request("NO_EXISTE", SalePresentation.UNIT, 1)), seller.getId(), null,
                BigDecimal.valueOf(2_500L)));
        assertThrows(IllegalArgumentException.class, () -> sales.registerSale(
                knownProduct, "SEL-DESCONOCIDO", null, BigDecimal.valueOf(85_000L)));
        assertThrows(IllegalArgumentException.class, () -> sales.registerSale(
                knownProduct, seller.getId(), "C999", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> sales.registerSale(
                knownProduct, seller.getId(), null, BigDecimal.ZERO));

        assertEquals(38, inventory.availableUnits("COSTENITA_175"));
        assertEquals(1, inventory.allMovements().size());
        assertTrue(sales.listAll().isEmpty());
        assertEquals(BigDecimal.ZERO, customers.getCustomerById("C001").getBalance());
    }

    @Test
    void insufficientStockLeavesAllProductsMovementsSalesAndCustomerDebtUnchanged() {
        inventory.addStock("POKER_250", 1, 0, "Inicial");
        inventory.addStock("POKER_750", 0, 3, "Inicial");
        int originalMovementCount = inventory.allMovements().size();

        assertThrows(IllegalStateException.class, () -> sales.registerSale(List.of(
                request("POKER_250", SalePresentation.HALF_BASKET, 1),
                request("POKER_750", SalePresentation.HALF_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(20_000L)));

        assertEquals(38, inventory.availableUnits("POKER_250"));
        assertEquals(3, inventory.availableUnits("POKER_750"));
        assertEquals(originalMovementCount, inventory.allMovements().size());
        assertTrue(inventory.allMovements().stream().noneMatch(m -> m.type() == MovementType.EXIT));
        assertTrue(sales.listAll().isEmpty());
        assertEquals(BigDecimal.ZERO, customers.getCustomerById("C001").getBalance());
    }

    @Test
    void discountsFullBasketsAndLooseUnitsAndKeepsRepeatedProductLinesSeparate() {
        inventory.addStock("POKER_250", 1, 5, "Inicial");

        Sale sale = sales.registerSale(List.of(
                request("POKER_250", SalePresentation.FULL_BASKET, 1),
                request("POKER_250", SalePresentation.UNIT, 3)),
                seller.getId(), "C001", BigDecimal.valueOf(90_000L));

        assertEquals(2, sale.getLines().size());
        assertEquals(BigDecimal.valueOf(92_500L), sale.calculateTotal());
        assertEquals(BigDecimal.valueOf(2_500L), sale.calculateBalance());
        assertEquals(0, inventory.getStock("POKER_250").fullBaskets());
        assertEquals(2, inventory.getStock("POKER_250").looseUnits());
        assertEquals(2, inventory.availableUnits("POKER_250"));
        List<com.laexclusiva.model.StockMovement> exits = inventory.allMovements().stream()
                .filter(m -> m.type() == MovementType.EXIT).toList();
        assertEquals(1, exits.size());
        assertEquals(41, exits.get(0).units());
    }

    @Test
    void laterPaymentUpdatesOnlyItsSaleAndTheCustomersAggregatedDebt() {
        inventory.addStock("COSTENITA_175", 2, 0, "Inicial");
        inventory.addStock("POKER_750", 1, 0, "Inicial");
        Sale first = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(10_000L));
        Sale second = sales.registerSale(List.of(
                request("POKER_750", SalePresentation.HALF_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(5_000L));

        assertEquals(BigDecimal.valueOf(115_000L), customers.getCustomerById("C001").getBalance());
        Sale paidSale = sales.registerPayment(first.getId(), BigDecimal.valueOf(30_000L));

        assertNotSame(first, paidSale);
        assertEquals(2, sales.listAll().size());
        assertEquals(1, first.getPayments().size());
        assertEquals(BigDecimal.valueOf(75_000L), first.calculateBalance());
        assertEquals(2, paidSale.getPayments().size());
        assertEquals(BigDecimal.valueOf(45_000L), paidSale.calculateBalance());
        assertEquals(BigDecimal.valueOf(45_000L), sales.findById(first.getId()).calculateBalance());
        assertEquals(BigDecimal.valueOf(40_000L), second.calculateBalance());
        assertEquals(BigDecimal.valueOf(85_000L), customers.getCustomerById("C001").getBalance());
        assertEquals(paidSale.calculateBalance().add(sales.findById(second.getId()).calculateBalance()),
                customers.getCustomerById("C001").getBalance());
    }

    @Test
    void rejectsInvalidPaymentsWithoutChangingSaleOrCustomerDebt() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(10_000L));
        int originalPaymentCount = sale.getPayments().size();
        BigDecimal originalSaleBalance = sale.calculateBalance();
        BigDecimal originalCustomerBalance = customers.getCustomerById("C001").getBalance();

        assertThrows(IllegalArgumentException.class, () -> sales.registerPayment(sale.getId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> sales.registerPayment(sale.getId(), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> sales.registerPayment(sale.getId(), BigDecimal.valueOf(-1L)));
        assertThrows(IllegalArgumentException.class,
                () -> sales.registerPayment(sale.getId(), BigDecimal.valueOf(80_000L)));
        assertThrows(IllegalArgumentException.class,
                () -> sales.registerPayment("SALE-NO-EXISTE", BigDecimal.ONE));

        assertEquals(originalPaymentCount, sale.getPayments().size());
        assertEquals(originalSaleBalance, sale.calculateBalance());
        assertEquals(originalCustomerBalance, customers.getCustomerById("C001").getBalance());
        assertFalse(sales.listAll().isEmpty());
    }

    @Test
    void rejectsGeneralPaymentThatWouldSilentlyChangeSalesDebt() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(10_000L));
        assertThrows(IllegalArgumentException.class,
                () -> customers.applyPaymentToCustomer("C001", BigDecimal.ONE));

        assertEquals(1, sale.getPayments().size());
        assertEquals(BigDecimal.valueOf(75_000L), sale.calculateBalance());
        assertEquals(BigDecimal.valueOf(75_000L), customers.getCustomerById("C001").getBalance());
        Sale updated = sales.registerPayment(sale.getId(), BigDecimal.valueOf(10_000L));
        assertEquals(BigDecimal.valueOf(65_000L), updated.calculateBalance());
        assertEquals(BigDecimal.valueOf(65_000L), customers.getCustomerById("C001").getBalance());
    }

    @Test
    void generalPaymentsOnlyReduceGeneralDebtWhenSalesDebtAlsoExists() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(10_000L));
        customers.addDebtToCustomer("C001", BigDecimal.valueOf(10_000L));

        customers.applyPaymentToCustomer("C001", BigDecimal.valueOf(5_000L));
        assertEquals(BigDecimal.valueOf(75_000L), sales.findById(sale.getId()).calculateBalance());
        assertEquals(BigDecimal.valueOf(80_000L), customers.getCustomerById("C001").getBalance());
        assertThrows(IllegalArgumentException.class,
                () -> customers.applyPaymentToCustomer("C001", BigDecimal.valueOf(6_000L)));

        Sale paid = sales.registerPayment(sale.getId(), BigDecimal.valueOf(30_000L));
        assertEquals(BigDecimal.valueOf(45_000L), paid.calculateBalance());
        assertEquals(BigDecimal.valueOf(50_000L), customers.getCustomerById("C001").getBalance());
    }

    @Test
    void rejectsPaymentWhenCustomerBalanceIsInconsistentWithSalesDebt() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(10_000L));
        customerRepository.update(new com.laexclusiva.model.Customer(
                "C001", "Cliente", "3007654321", "Calle 2", BigDecimal.valueOf(10_000L)));

        assertThrows(IllegalStateException.class,
                () -> sales.registerPayment(sale.getId(), BigDecimal.ONE));
        assertEquals(BigDecimal.valueOf(75_000L), sales.findById(sale.getId()).calculateBalance());
        assertEquals(BigDecimal.valueOf(10_000L), customers.getCustomerById("C001").getBalance());
    }

    @Test
    void repositoryQueriesAndReturnedSalesCannotMutateStoredSale() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        Sale returned = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(10_000L));
        returned.registerPayment(new com.laexclusiva.model.SalePayment(BigDecimal.ONE,
                java.time.LocalDateTime.now()));

        Sale query = saleRepository.findById(returned.getId()).orElseThrow();
        query.registerPayment(new com.laexclusiva.model.SalePayment(BigDecimal.ONE,
                java.time.LocalDateTime.now()));
        query.getCustomer().addDebt(BigDecimal.valueOf(9_000L));
        query.getSeller().updateData("Otro", "3000000000", "Otra dirección");
        query.getLines().get(0).getProduct().definePrice(
                SalePresentation.FULL_BASKET, BigDecimal.ONE);
        List<Sale> all = saleRepository.findAll();
        all.get(0).registerPayment(new com.laexclusiva.model.SalePayment(BigDecimal.ONE,
                java.time.LocalDateTime.now()));
        all.clear();

        Sale stored = sales.findById(returned.getId());
        assertEquals(BigDecimal.valueOf(75_000L), stored.calculateBalance());
        assertEquals(1, stored.getPayments().size());
        assertEquals("Cliente", stored.getCustomer().getName());
        assertEquals(BigDecimal.ZERO, stored.getCustomer().getBalance());
        assertEquals("Vendedora", stored.getSeller().getName());
        assertEquals(BigDecimal.valueOf(85_000L),
                stored.getLines().get(0).getProduct().priceFor(SalePresentation.FULL_BASKET));
        assertEquals(BigDecimal.valueOf(75_000L), customers.getCustomerById("C001").getBalance());
    }

    @Test
    void failedMovementSaveRestoresStockAndRemovesPartialMovements() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        inventory.addStock("POKER_750", 1, 0, "Inicial");
        int originalMovementCount = inventory.allMovements().size();
        movementRepository.failOnExitSaveNumber = 2;

        assertThrows(IllegalStateException.class, () -> sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1),
                request("POKER_750", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.ZERO));

        assertEquals(38, inventory.availableUnits("COSTENITA_175"));
        assertEquals(16, inventory.availableUnits("POKER_750"));
        assertEquals(originalMovementCount, inventory.allMovements().size());
        assertTrue(sales.listAll().isEmpty());
        assertEquals(BigDecimal.ZERO, customers.getCustomerById("C001").getBalance());
    }

    @Test
    void failedCustomerUpdateRestoresStockDebtAndSale() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        customerRepository.failNextUpdateAfterPersist = true;

        assertThrows(IllegalStateException.class, () -> sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.ZERO));

        assertEquals(38, inventory.availableUnits("COSTENITA_175"));
        assertEquals(1, inventory.allMovements().size());
        assertTrue(sales.listAll().isEmpty());
        assertEquals(BigDecimal.ZERO, customers.getCustomerById("C001").getBalance());
        assertEquals(BigDecimal.ZERO, customers.saleDebtBalance("C001"));
    }

    @Test
    void failedFinalSaleSaveCompensatesStockMovementsAndCustomerDebt() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        saleRepository.failNextSaveAfterPersist = true;

        assertThrows(IllegalStateException.class, () -> sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.ZERO));

        assertEquals(38, inventory.availableUnits("COSTENITA_175"));
        assertEquals(1, inventory.allMovements().size());
        assertTrue(sales.listAll().isEmpty());
        assertEquals(BigDecimal.ZERO, customers.getCustomerById("C001").getBalance());
        assertEquals(BigDecimal.ZERO, customers.saleDebtBalance("C001"));
    }

    @Test
    void failedPaymentPersistenceRollsCustomerAndSaleBack() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), "C001", BigDecimal.valueOf(10_000L));
        saleRepository.failNextUpdateAfterPersist = true;

        assertThrows(IllegalStateException.class,
                () -> sales.registerPayment(sale.getId(), BigDecimal.valueOf(20_000L)));

        assertEquals(BigDecimal.valueOf(75_000L), sales.findById(sale.getId()).calculateBalance());
        assertEquals(1, sales.findById(sale.getId()).getPayments().size());
        assertEquals(BigDecimal.valueOf(75_000L), customers.getCustomerById("C001").getBalance());
        assertEquals(BigDecimal.valueOf(75_000L), customers.saleDebtBalance("C001"));
    }

    @Test
    void saleRepositoryRejectsDuplicateIdsAndReturnsIndependentListSnapshots() {
        inventory.addStock("COSTENITA_175", 1, 0, "Inicial");
        Sale sale = sales.registerSale(List.of(
                request("COSTENITA_175", SalePresentation.FULL_BASKET, 1)),
                seller.getId(), null, BigDecimal.valueOf(85_000L));

        assertThrows(IllegalArgumentException.class, () -> saleRepository.save(sale));
        List<Sale> snapshot = saleRepository.findAll();
        snapshot.clear();

        assertTrue(saleRepository.findById(sale.getId()).isPresent());
        assertEquals(1, saleRepository.findAll().size());
        assertThrows(IllegalArgumentException.class, () -> saleRepository.save(null));
    }

    private static StockRequest request(String productId, SalePresentation presentation, int quantity) {
        return new StockRequest(productId, presentation, quantity);
    }

    private static final class FailingSaleRepository extends InMemorySaleRepository {
        private boolean failNextSaveAfterPersist;
        private boolean failNextUpdateAfterPersist;

        @Override
        public void save(Sale sale) {
            super.save(sale);
            if (failNextSaveAfterPersist) {
                failNextSaveAfterPersist = false;
                throw new IllegalStateException("fallo simulado después de guardar la venta");
            }
        }

        @Override
        public void update(Sale sale) {
            super.update(sale);
            if (failNextUpdateAfterPersist) {
                failNextUpdateAfterPersist = false;
                throw new IllegalStateException("fallo simulado después de actualizar la venta");
            }
        }
    }

    private static final class FailingCustomerRepository extends CustomerRepository {
        private boolean failNextUpdateAfterPersist;

        @Override
        public void update(com.laexclusiva.model.Customer customer) {
            super.update(customer);
            if (failNextUpdateAfterPersist) {
                failNextUpdateAfterPersist = false;
                throw new IllegalStateException("fallo simulado después de actualizar el cliente");
            }
        }
    }

    private static final class FailingMovementRepository extends InMemoryStockMovementRepository {
        private int failOnExitSaveNumber = -1;
        private int exitSaveCount;

        @Override
        public void save(StockMovement movement) {
            super.save(movement);
            if (movement.type() == MovementType.EXIT && failOnExitSaveNumber > 0
                    && ++exitSaveCount == failOnExitSaveNumber) {
                failOnExitSaveNumber = -1;
                throw new IllegalStateException("fallo simulado después de guardar el movimiento");
            }
        }
    }
}
