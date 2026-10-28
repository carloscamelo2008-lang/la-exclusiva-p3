package com.laexclusiva.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsoleMenuTest {

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
        inventory = new InventoryService(products, new InMemoryInventoryRepository(),
                new InMemoryStockMovementRepository());
        customers = new CustomerService(new CustomerRepository());
        sellers = new SellerService(new SellerRepository());
        sales = new SaleService(new InMemorySaleRepository(), products, inventory, customers, sellers);
        seller = sellers.registerSeller("Vendedor de prueba", "3001234567", "Dirección de prueba");
        customers.registerCustomer("C001", "Cliente de prueba", "3007654321", "Dirección de prueba");
    }

    @Test
    void registersSaleWithMultipleProductsAndPresentationsUsingConfiguredPrices() {
        inventory.addStock("POKER_250", 1, 0, "Existencias recibidas");
        inventory.addStock("POKER_750", 1, 0, "Existencias recibidas");

        String output = runConsole(String.join("\n",
                "2", "1", "POKER_250", "3", "1", "s",
                "POKER_750", "2", "1", "n", seller.getId(), "100000", "C001", "s",
                "0", "0"));

        List<Sale> registered = sales.listAll();
        assertEquals(1, registered.size());
        Sale sale = registered.get(0);
        assertEquals(2, sale.getLines().size());
        assertEquals(SalePresentation.FULL_BASKET, sale.getLines().get(0).getPresentation());
        assertEquals(SalePresentation.HALF_BASKET, sale.getLines().get(1).getPresentation());
        assertEquals(new BigDecimal("130000"), sale.calculateTotal());
        assertEquals(new BigDecimal("100000"), sale.calculateTotalPaid());
        assertEquals(new BigDecimal("30000"), sale.calculateBalance());
        assertEquals(0, inventory.availableUnits("POKER_250"));
        assertEquals(8, inventory.availableUnits("POKER_750"));
        assertTrue(output.contains("Total: $ 130000 COP"));
        assertTrue(output.contains("Venta registrada correctamente."));
    }

    @Test
    void insufficientStockRejectsWholeSaleWithoutPartialChanges() {
        inventory.addStock("POKER_250", 1, 0, "Existencias recibidas");
        int initialMovements = inventory.allMovements().size();

        String output = runConsole(String.join("\n",
                "2", "1", "POKER_250", "3", "2", "n", "0", "2", "0", "0"));

        assertTrue(sales.listAll().isEmpty());
        assertEquals(38, inventory.availableUnits("POKER_250"));
        assertEquals(initialMovements, inventory.allMovements().size());
        assertTrue(output.contains("Stock insuficiente"));
    }

    @Test
    void invalidNumericInputsRecoverAndTheMenuCanContinue() {
        inventory.addStock("POKER_250", 0, 2, "Existencias recibidas");

        String output = runConsole(String.join("\n",
                "2", "1", "POKER_250", "x", "1", "cantidad", "1", "n",
                seller.getId(), "importe", "2500", "s", "opción inválida", "0", "opción inválida", "0"));

        assertEquals(1, sales.listAll().size());
        assertTrue(output.contains("Presentación no válida"));
        assertTrue(output.contains("número entero mayor que cero"));
        assertTrue(output.contains("importe válido"));
        assertTrue(output.contains("Opción no válida"));
        assertTrue(output.contains("Gracias por utilizar La Exclusiva."));
    }

    @Test
    void saleQueryShowsDateSellerCustomerAndFinancialTotals() {
        inventory.addStock("POKER_250", 1, 0, "Existencias recibidas");
        Sale sale = createPendingSale();

        String output = runConsole(String.join("\n", "2", "3", sale.getId(), "0", "0"));

        assertTrue(output.contains(sale.getId()));
        assertTrue(output.contains("Fecha:"));
        assertTrue(output.contains("Vendedor: Vendedor de prueba"));
        assertTrue(output.contains("Cliente: Cliente de prueba"));
        assertTrue(output.contains("Total: $ 85000 COP"));
        assertTrue(output.contains("Pagado: $ 20000 COP"));
        assertTrue(output.contains("Saldo: $ 65000 COP"));
    }

    @Test
    void listingShowsTheSameFinancialSummaryAsSaleQuery() {
        inventory.addStock("POKER_250", 1, 0, "Existencias recibidas");
        Sale sale = createPendingSale();

        String output = runConsole(String.join("\n", "2", "2", "0", "0"));

        assertTrue(output.contains(sale.getId()));
        assertTrue(output.contains("Total: $ 85000 COP"));
        assertTrue(output.contains("Pagado: $ 20000 COP"));
        assertTrue(output.contains("Saldo: $ 65000 COP"));
    }

    @Test
    void nonexistentProductAndSellerCanBeCorrectedWithoutClosingTheSaleFlow() {
        inventory.addStock("POKER_250", 0, 2, "Existencias recibidas");

        String output = runConsole(String.join("\n",
                "2", "1", "NO_EXISTE", "POKER_250", "1", "1", "n",
                "SEL-NO-EXISTE", seller.getId(), "2500", "s", "0", "0"));

        assertEquals(1, sales.listAll().size());
        assertTrue(output.contains("Producto inexistente"));
        assertTrue(output.contains("No existe un vendedor con ese ID"));
        assertFalse(output.contains("Exception in thread"));
    }

    @Test
    void paymentIsAppliedToSelectedSaleAndDisplaysUpdatedBalance() {
        inventory.addStock("POKER_250", 1, 0, "Existencias recibidas");
        Sale sale = createPendingSale();

        String output = runConsole(String.join("\n", "2", "4", sale.getId(), "10000", "0", "0"));

        assertEquals(1, sales.listAll().size());
        Sale updated = sales.findById(sale.getId());
        assertEquals(new BigDecimal("30000"), updated.calculateTotalPaid());
        assertEquals(new BigDecimal("55000"), updated.calculateBalance());
        assertEquals(new BigDecimal("55000"), customers.getCustomerById("C001").getBalance());
        assertTrue(output.contains("Abono registrado correctamente."));
        assertTrue(output.contains("Saldo pendiente: $ 55000 COP"));
    }

    @Test
    void excessivePaymentDoesNotChangeSaleOrCustomerBalance() {
        inventory.addStock("POKER_250", 1, 0, "Existencias recibidas");
        Sale sale = createPendingSale();

        String output = runConsole(String.join("\n", "2", "4", sale.getId(), "70000", "0", "0"));

        Sale unchanged = sales.findById(sale.getId());
        assertEquals(1, sales.listAll().size());
        assertEquals(new BigDecimal("20000"), unchanged.calculateTotalPaid());
        assertEquals(new BigDecimal("65000"), unchanged.calculateBalance());
        assertEquals(new BigDecimal("65000"), customers.getCustomerById("C001").getBalance());
        assertTrue(output.contains("no puede superar el saldo pendiente"));
        assertFalse(output.contains("Abono registrado correctamente."));
    }

    @Test
    void canReturnToMainMenuAndExitNormally() {
        String output = runConsole(String.join("\n", "2", "0", "0"));

        assertTrue(sales.listAll().isEmpty());
        assertTrue(output.contains("0. Volver al menú principal"));
        assertTrue(output.contains("Gracias por utilizar La Exclusiva."));
    }

    private Sale createPendingSale() {
        return sales.registerSale(List.of(new StockRequest("POKER_250",
                SalePresentation.FULL_BASKET, 1)), seller.getId(), "C001", new BigDecimal("20000"));
    }

    private String runConsole(String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Scanner scanner = new Scanner(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        ConsoleMenu menu = new ConsoleMenu(products, inventory, customers, sellers, sales,
                scanner, new PrintStream(output, true, StandardCharsets.UTF_8));
        menu.start();
        return output.toString(StandardCharsets.UTF_8);
    }
}
