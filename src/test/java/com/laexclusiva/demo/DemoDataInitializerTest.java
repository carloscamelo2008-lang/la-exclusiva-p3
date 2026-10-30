package com.laexclusiva.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemorySaleRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.SellerRepository;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SaleService;
import com.laexclusiva.service.SellerService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoDataInitializerTest {

    private ProductService productService;
    private InventoryService inventoryService;
    private CustomerService customerService;
    private SaleService saleService;
    private DemoDataInitializer initializer;

    @BeforeEach
    void setUp() {
        // Se arma la aplicación igual que en Main, pero con repositorios nuevos en cada prueba.
        productService = new ProductService(new InMemoryProductRepository());
        productService.registerAll(InitialCatalog.create());
        inventoryService = new InventoryService(productService, new InMemoryInventoryRepository(),
                new InMemoryStockMovementRepository());
        customerService = new CustomerService(new CustomerRepository());
        SellerService sellerService = new SellerService(new SellerRepository());
        saleService = new SaleService(new InMemorySaleRepository(), productService,
                inventoryService, customerService, sellerService);
        initializer = new DemoDataInitializer(productService, inventoryService,
                customerService, sellerService, saleService);
    }

    /** Compara importes ignorando la escala (30000 y 30000.00 son el mismo valor). */
    private static void assertMoney(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                "Se esperaba " + expected + " y fue " + actual);
    }

    @Test
    void demo01_catalogIsNotDuplicated() {
        initializer.initialize();
        assertEquals(6, productService.listAll().size());
    }

    @Test
    void demo02_initialStockMatchesDocumentedTable() {
        initializer.initialize();
        // Sin venta: 76 / 76 / 60 / 60. Poker 250 y Poker 750 quedan tras la venta demo.
        assertEquals(76, inventoryService.availableUnits("COSTENITA_175"));
        assertEquals(76, inventoryService.availableUnits("AGUILITA_250"));
        assertEquals(60, inventoryService.availableUnits("COSTENA_BACANA_330"));
        assertEquals(60, inventoryService.availableUnits("COLA_Y_POLA_LATA_330"));
    }

    @Test
    void demo02_stockEntriesLeaveOneMovementPerReference() {
        initializer.initialize();
        long entries = inventoryService.allMovements().stream()
                .filter(m -> m.type() == MovementType.ENTRY).count();
        assertEquals(6, entries);
    }

    @Test
    void demo03_demoSaleTotalsAndDiscountsUnits() {
        DemoDataInitializer.DemoResult result = initializer.initialize();
        // Total $130.000 = $85.000 (canasta Poker 250) + $45.000 (media canasta Poker 750).
        assertMoney(130_000, saleService.previewTotal(DemoDataInitializer.demoSaleLines()));
        // Pago inicial $100.000 -> saldo $30.000, en la venta y en el cliente.
        assertMoney(30_000, saleService.findById(result.saleId()).calculateBalance());
        assertMoney(30_000, customerService.getCustomerById(result.customerId()).getBalance());
        // 76 - 38 = 38 y 16 - 8 = 8.
        assertEquals(38, inventoryService.availableUnits("POKER_250"));
        assertEquals(8, inventoryService.availableUnits("POKER_750"));
        assertEquals(1, saleService.listAll().size());
    }

    @Test
    void demo04_paymentReducesSaleAndCustomerBalanceByTheSameAmount() {
        DemoDataInitializer.DemoResult result = initializer.initialize();
        saleService.registerPayment(result.saleId(), BigDecimal.valueOf(10_000L));
        assertMoney(20_000, saleService.findById(result.saleId()).calculateBalance());
        assertMoney(20_000, customerService.getCustomerById(result.customerId()).getBalance());
    }

    @Test
    void demo05_withoutDemoThereAreNoSalesNorStock() {
        assertTrue(saleService.listAll().isEmpty());
        assertEquals(0, inventoryService.availableUnits("POKER_250"));
    }

    @Test
    void returnsIdentifiersForLinkingAccounts() {
        DemoDataInitializer.DemoResult result = initializer.initialize();
        assertNotNull(result.adminSellerId());
        assertNotNull(result.workerSellerId());
        assertEquals(DemoDataInitializer.DEMO_CUSTOMER_ID, result.customerId());
        assertNotNull(result.saleId());
    }

    @Test
    void secondRunOnSameInstanceFailsClearly() {
        initializer.initialize();
        assertThrows(IllegalStateException.class, () -> initializer.initialize());
    }

    @Test
    void secondInitializerOverPopulatedServicesFailsAndDoesNotDuplicate() {
        initializer.initialize();
        DemoDataInitializer another = new DemoDataInitializer(productService, inventoryService,
                customerService, new SellerService(new SellerRepository()), saleService);
        assertThrows(IllegalStateException.class, another::initialize);
        assertEquals(8, inventoryService.availableUnits("POKER_750"));
        assertEquals(1, saleService.listAll().size());
    }

    @Test
    void nullDependenciesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDataInitializer(null, inventoryService, customerService, null, saleService));
    }
}
