package com.laexclusiva.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemorySaleRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.SellerRepository;
import com.laexclusiva.service.AuthorizationService;
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
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsoleSalesWorkflowTest {

    private static final Pattern CUSTOMER_ID = Pattern.compile("Cliente registrado con ID (CLI-[A-F0-9]{8})\\.");
    private static final Pattern SELLER_ID = Pattern.compile("Vendedor registrado con ID (SEL-[A-F0-9]{8})\\.");
    private static final Pattern SALE_ID = Pattern.compile("Venta (SALE-[A-F0-9-]+) \\| Fecha:");

    private ProductService products;
    private InMemoryStockMovementRepository movementRepository;
    private InMemorySaleRepository saleRepository;
    private InventoryService inventory;
    private CustomerService customers;
    private SellerRepository sellerRepository;
    private SellerService sellers;
    private SaleService sales;

    @BeforeEach
    void setUp() {
        products = new ProductService(new InMemoryProductRepository());
        products.registerAll(InitialCatalog.create());
        movementRepository = new InMemoryStockMovementRepository();
        saleRepository = new InMemorySaleRepository();
        inventory = new InventoryService(products, new InMemoryInventoryRepository(), movementRepository);
        customers = new CustomerService(new CustomerRepository());
        sellerRepository = new SellerRepository();
        sellers = new SellerService(sellerRepository);
        sales = new SaleService(saleRepository, products, inventory, customers, sellers);
    }

    @Test
    void completesConsoleWorkflowFromStockSetupThroughSalePaymentAndInsufficientStock() {
        BeerProduct poker250 = products.findById("POKER_250");
        BeerProduct poker750 = products.findById("POKER_750");
        int initialPoker250Units = poker250.equivalentUnits(2, 5);
        int initialPoker750Units = poker750.equivalentUnits(1, 2);
        BigDecimal expectedTotal = products.priceFor("POKER_250", SalePresentation.FULL_BASKET)
                .add(products.priceFor("POKER_750", SalePresentation.HALF_BASKET));
        BigDecimal initialPayment = BigDecimal.valueOf(30_000L);
        BigDecimal laterPayment = BigDecimal.valueOf(25_000L);

        String setupOutput = runConsole(
                "opcion-invalida",
                "1", "2", "POKER_250", "cantidad-invalida", "2", "5",
                "2", "POKER_750", "1", "2", "1", "0",
                "3", "2", "Cliente Ficticio Integrado", "3000000001", "Direccion Ficticia 123",
                "4", "Vendedor Ficticio Integrado", "3000000002", "Direccion Ficticia 456", "0");

        assertTrue(setupOutput.contains("Intente nuevamente."));
        assertTrue(setupOutput.contains("igual o mayor que cero."));
        assertTrue(setupOutput.contains("Existencias registradas. Inventario actual: 2 canastas, 5 unidades sueltas ("
                + initialPoker250Units + " unidades)."));
        assertTrue(setupOutput.contains("Existencias registradas. Inventario actual: 1 canastas, 2 unidades sueltas ("
                + initialPoker750Units + " unidades)."));
        assertTrue(setupOutput.contains("(POKER_250): 2 canastas, 5 unidades sueltas, "
                + initialPoker250Units + " unidades en total."));
        assertTrue(setupOutput.contains("(POKER_750): 1 canastas, 2 unidades sueltas, "
                + initialPoker750Units + " unidades en total."));
        String customerId = captureId(CUSTOMER_ID, setupOutput, "ID del cliente generado desde el menú");
        String sellerId = captureId(SELLER_ID, setupOutput, "ID del vendedor generado desde el menú");

        String saleOutput = runConsole(
                "2", "1",
                "POKER_250", "9", "3", "cantidad-invalida", "1", "s",
                "POKER_750", "2", "1", "n",
                sellerId, initialPayment.toPlainString(), customerId, "s");

        assertTrue(saleOutput.contains("Presentación no válida. Intente nuevamente."));
        assertTrue(saleOutput.contains("número entero mayor que cero."));
        assertTrue(saleOutput.contains("Venta registrada correctamente."));
        assertTrue(saleOutput.contains("Total: " + formatMoney(expectedTotal)));
        assertTrue(saleOutput.contains("Pagado: " + formatMoney(initialPayment)));
        BigDecimal initialBalance = expectedTotal.subtract(initialPayment);
        assertTrue(saleOutput.contains("Saldo: " + formatMoney(initialBalance)));
        assertTrue(saleOutput.contains("Cliente: Cliente Ficticio Integrado"));
        assertTrue(saleOutput.contains("Vendedor: Vendedor Ficticio Integrado"));
        String saleId = captureId(SALE_ID, saleOutput, "ID de la venta generado desde el menú");

        Sale registeredSale = sales.findById(saleId);
        assertEquals(1, sales.listAll().size());
        assertEquals(expectedTotal, registeredSale.calculateTotal());
        assertEquals(initialPayment, registeredSale.calculateTotalPaid());
        assertEquals(initialBalance, registeredSale.calculateBalance());
        assertEquals(customerId, registeredSale.getCustomer().getId());
        assertEquals(sellerId, registeredSale.getSeller().getId());
        assertEquals(2, registeredSale.getLines().size());
        assertEquals(SalePresentation.FULL_BASKET,
                registeredSale.getLines().get(0).getPresentation());
        assertEquals(SalePresentation.HALF_BASKET,
                registeredSale.getLines().get(1).getPresentation());
        assertEquals(initialBalance, customers.getCustomerById(customerId).getBalance());
        assertEquals(poker250.unitsFor(SalePresentation.FULL_BASKET), exitUnitsFor("POKER_250"));
        assertEquals(poker750.unitsFor(SalePresentation.HALF_BASKET), exitUnitsFor("POKER_750"));

        int poker250Remaining = initialPoker250Units - poker250.unitsFor(SalePresentation.FULL_BASKET);
        int poker750Remaining = initialPoker750Units - poker750.unitsFor(SalePresentation.HALF_BASKET);
        String queryAndInventoryOutput = runConsole(
                "2", "2", "3", saleId, "0",
                "1", "1", "0", "0");

        assertTrue(queryAndInventoryOutput.contains("Pagos registrados: 1"));
        assertTrue(queryAndInventoryOutput.contains("Total: " + formatMoney(expectedTotal)));
        assertTrue(queryAndInventoryOutput.contains("Pagado: " + formatMoney(initialPayment)));
        assertTrue(queryAndInventoryOutput.contains("Saldo: " + formatMoney(initialBalance)));
        assertTrue(queryAndInventoryOutput.contains(inventoryLine(poker250, poker250Remaining)));
        assertTrue(queryAndInventoryOutput.contains(inventoryLine(poker750, poker750Remaining)));

        String paymentOutput = runConsole(
                "2", "4", saleId, laterPayment.toPlainString(), "2", "3", saleId, "0",
                "3", "1", "0");
        BigDecimal finalBalance = initialBalance.subtract(laterPayment);
        BigDecimal totalPaid = initialPayment.add(laterPayment);
        assertTrue(paymentOutput.contains("Abono registrado correctamente."));
        assertTrue(paymentOutput.contains("Total pagado: " + formatMoney(totalPaid)));
        assertTrue(paymentOutput.contains("Saldo pendiente: " + formatMoney(finalBalance)));
        assertTrue(paymentOutput.contains("Pagos registrados: 2"));
        assertTrue(paymentOutput.contains("Cliente Ficticio Integrado | Saldo: " + formatMoney(finalBalance)));
        assertEquals(1, sales.listAll().size());
        assertEquals(2, sales.findById(saleId).getPayments().size());
        assertEquals(totalPaid, sales.findById(saleId).calculateTotalPaid());
        assertEquals(finalBalance, sales.findById(saleId).calculateBalance());
        assertEquals(finalBalance, customers.getCustomerById(customerId).getBalance());

        long exitsBeforeRejectedSale = movementRepository.findAll().stream()
                .filter(movement -> movement.type() == MovementType.EXIT).count();
        String rejectedSaleOutput = runConsole(
                "2", "1", "POKER_750", "3", "1", "n",
                "opcion-invalida", "2", "0", "opcion-invalida",
                "1", "1", "0", "0");

        assertTrue(rejectedSaleOutput.contains("Stock insuficiente"));
        assertTrue(rejectedSaleOutput.contains("No se registró la venta."));
        assertTrue(rejectedSaleOutput.contains("Intente nuevamente."));
        assertTrue(rejectedSaleOutput.contains(inventoryLine(poker250, poker250Remaining)));
        assertTrue(rejectedSaleOutput.contains(inventoryLine(poker750, poker750Remaining)));
        assertTrue(rejectedSaleOutput.contains("Gracias por utilizar La Exclusiva."));
        assertEquals(1, sales.listAll().size());
        assertEquals(exitsBeforeRejectedSale, movementRepository.findAll().stream()
                .filter(movement -> movement.type() == MovementType.EXIT).count());
        assertEquals(2, exitsBeforeRejectedSale);
        assertEquals(poker250Remaining, inventory.availableUnits("POKER_250"));
        assertEquals(poker750Remaining, inventory.availableUnits("POKER_750"));
        assertEquals(finalBalance, customers.getCustomerById(customerId).getBalance());
    }

    @Test
    void workerSaleAlwaysUsesAuthenticatedSellerAndDoesNotAskToChooseSeller() {
        inventory.addStock("POKER_250", 0, 2, "Existencias recibidas");
        Seller associatedSeller = sellers.registerSeller("Vendedor asociado", "3001234567", "Dirección asociada");
        Seller otherSeller = sellers.registerSeller("Otro vendedor", "3001234568", "Otra dirección");
        UserSession worker = session(UserRole.WORKER, associatedSeller.getId());

        String output = runConsole(worker, "2", "1", "POKER_250", "1", "1", "n", "2500", "s", "0", "0");

        Sale sale = sales.listAll().get(0);
        assertEquals(worker.getUserAccount().getSellerId(), sale.getSeller().getId());
        assertTrue(!sale.getSeller().getId().equals(otherSeller.getId()));
        assertTrue(!output.contains("ID del vendedor"));
        assertTrue(output.contains("Vendedor: " + sale.getSeller().getName()));
    }

    @Test
    void workerCannotManageInventoryOrSellersButCanRegisterCustomerAndViewSales() {
        sellers.registerSeller("Vendedor asociado", "3001234567", "Dirección asociada");
        UserSession worker = session(UserRole.WORKER, sellers.getAllSellers().get(0).getId());
        String output = runConsole(worker, "1", "2", "0", "3", "3", "4", "2", "Cliente", "3000000000", "Dirección", "0", "2", "2", "0", "0");

        assertTrue(output.contains("Acceso denegado"));
        assertTrue(!output.contains("2. Registrar ingreso de existencias"));
        assertTrue(!output.contains("3. Listar vendedores"));
        assertTrue(output.contains("Cliente registrado con ID"));
        assertTrue(output.contains("No hay ventas registradas."));
        assertEquals(1, sellers.getAllSellers().size());
        assertEquals(0, inventory.availableUnits("POKER_250"));
    }

    private String runConsole(String... inputLines) {
        return runConsole(session(UserRole.ADMINISTRATOR, null), inputLines);
    }

    private String runConsole(UserSession userSession, String... inputLines) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] input = String.join("\n", inputLines).getBytes(StandardCharsets.UTF_8);
        try (Scanner scanner = new Scanner(new ByteArrayInputStream(input), StandardCharsets.UTF_8);
             PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            ConsoleMenu menu = new ConsoleMenu(products, inventory, customers,
                    sellers, sales, scanner, printStream, userSession, new AuthorizationService());
            menu.start();
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private UserSession session(UserRole role, String sellerId) {
        return new UserSession(new UserAccount(1L, role.name().toLowerCase(), "hash", role, sellerId));
    }

    private String inventoryLine(BeerProduct product, int units) {
        return "(" + product.getId() + "): " + units / product.getUnitsPerBasket()
                + " canastas, " + units % product.getUnitsPerBasket()
                + " unidades sueltas, " + units + " unidades en total.";
    }

    private int exitUnitsFor(String productId) {
        return movementRepository.findAll().stream()
                .filter(movement -> movement.type() == MovementType.EXIT)
                .filter(movement -> movement.productId().equals(productId))
                .mapToInt(movement -> movement.units())
                .sum();
    }

    private static String captureId(Pattern pattern, String output, String description) {
        Matcher matcher = pattern.matcher(output);
        assertTrue(matcher.find(), description + " debe aparecer en la salida de consola.\n" + output);
        return matcher.group(1);
    }

    private static String formatMoney(BigDecimal amount) {
        return "$ " + amount.stripTrailingZeros().toPlainString() + " COP";
    }
}
