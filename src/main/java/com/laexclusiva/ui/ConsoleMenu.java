package com.laexclusiva.ui;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.StockSnapshot;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SaleService;
import com.laexclusiva.service.SellerService;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;

/** Menú de consola para ventas y las operaciones mínimas que las habilitan. */
public final class ConsoleMenu {

    private static final DateTimeFormatter SALE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final SellerService sellerService;
    private final SaleService saleService;
    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleMenu(ProductService productService, InventoryService inventoryService,
                       CustomerService customerService, SellerService sellerService,
                       SaleService saleService, Scanner scanner, PrintStream out) {
        this.productService = Objects.requireNonNull(productService, "El servicio de productos es obligatorio.");
        this.inventoryService = Objects.requireNonNull(inventoryService, "El servicio de inventario es obligatorio.");
        this.customerService = Objects.requireNonNull(customerService, "El servicio de clientes es obligatorio.");
        this.sellerService = Objects.requireNonNull(sellerService, "El servicio de vendedores es obligatorio.");
        this.saleService = Objects.requireNonNull(saleService, "El servicio de ventas es obligatorio.");
        this.scanner = Objects.requireNonNull(scanner, "La entrada de consola es obligatoria.");
        this.out = Objects.requireNonNull(out, "La salida de consola es obligatoria.");
    }

    /** Inicia el ciclo de consola. El Scanner y el PrintStream pertenecen al punto de entrada. */
    public void start() {
        boolean running = true;
        while (running) {
            showMainMenu();
            String option = readLine("");
            if (option == null) {
                return;
            }
            switch (option) {
                case "1" -> inventoryMenu();
                case "2" -> salesMenu();
                case "3" -> peopleMenu();
                case "4" -> showPendingModule("Ingresos y egresos");
                case "5" -> showPendingModule("Consultas y reportes");
                case "0" -> {
                    out.println("Gracias por utilizar La Exclusiva.");
                    running = false;
                }
                default -> out.println("Opción no válida. Intente nuevamente.");
            }
        }
    }

    private void showMainMenu() {
        out.println();
        out.println("======================================");
        out.println("              LA EXCLUSIVA");
        out.println("       Sistema de administración");
        out.println("======================================");
        out.println("1. Productos e inventario");
        out.println("2. Ventas");
        out.println("3. Clientes y vendedores");
        out.println("4. Ingresos y egresos");
        out.println("5. Consultas y reportes");
        out.println("0. Salir");
        out.print("Seleccione una opción: ");
    }

    private void inventoryMenu() {
        boolean back = false;
        while (!back) {
            out.println();
            out.println("--- Productos e inventario ---");
            out.println("1. Consultar catálogo e inventario");
            out.println("2. Registrar ingreso de existencias");
            out.println("0. Volver al menú principal");
            String option = readLine("Seleccione una opción: ");
            if (option == null) return;
            switch (option) {
                case "1" -> showCatalogAndInventory();
                case "2" -> registerStockEntry();
                case "0" -> back = true;
                default -> out.println("Opción no válida. Intente nuevamente.");
            }
        }
    }

    private void showCatalogAndInventory() {
        List<BeerProduct> products = productService.listAll();
        if (products.isEmpty()) {
            out.println("No hay productos registrados en el catálogo.");
            return;
        }
        out.println("Catálogo e inventario disponible:");
        for (BeerProduct product : products) {
            StockSnapshot stock = inventoryService.getStock(product.getId());
            out.printf("- %s (%s): %d canastas, %d unidades sueltas, %d unidades en total.%n",
                    product, product.getId(), stock.fullBaskets(), stock.looseUnits(), stock.totalUnits());
        }
    }

    private void registerStockEntry() {
        BeerProduct product = chooseProduct("Ingrese el ID del producto para el ingreso (vacío para volver): ");
        if (product == null) return;
        Integer baskets = readNonNegativeInt("Canastas completas recibidas: ");
        if (baskets == null) return;
        Integer looseUnits = readNonNegativeInt("Unidades sueltas recibidas: ");
        if (looseUnits == null) return;
        if (baskets == 0 && looseUnits == 0) {
            out.println("El ingreso debe incluir al menos una unidad.");
            return;
        }
        try {
            inventoryService.addStock(product.getId(), baskets, looseUnits,
                    "Ingreso de existencias registrado desde consola");
            StockSnapshot stock = inventoryService.getStock(product.getId());
            out.printf("Existencias registradas. Inventario actual: %d canastas, %d unidades sueltas (%d unidades).%n",
                    stock.fullBaskets(), stock.looseUnits(), stock.totalUnits());
        } catch (IllegalArgumentException exception) {
            out.println("No se pudo registrar el ingreso: " + exception.getMessage());
        }
    }

    private void peopleMenu() {
        boolean back = false;
        while (!back) {
            out.println();
            out.println("--- Clientes y vendedores ---");
            out.println("1. Listar clientes");
            out.println("2. Registrar cliente");
            out.println("3. Listar vendedores");
            out.println("4. Registrar vendedor");
            out.println("0. Volver al menú principal");
            String option = readLine("Seleccione una opción: ");
            if (option == null) return;
            switch (option) {
                case "1" -> listCustomers();
                case "2" -> registerCustomer();
                case "3" -> listSellers();
                case "4" -> registerSeller();
                case "0" -> back = true;
                default -> out.println("Opción no válida. Intente nuevamente.");
            }
        }
    }

    private void listCustomers() {
        List<Customer> customers = customerService.getAllCustomers();
        if (customers.isEmpty()) {
            out.println("No hay clientes registrados.");
            return;
        }
        out.println("Clientes registrados:");
        for (Customer customer : customers) {
            out.printf("- %s | %s | Saldo: %s%n",
                    customer.getId(), customer.getName(), formatMoney(customer.getBalance()));
        }
    }

    private void registerCustomer() {
        String name = readLine("Nombre del cliente: ");
        if (name == null) return;
        String phone = readLine("Teléfono: ");
        if (phone == null) return;
        String address = readLine("Dirección: ");
        if (address == null) return;
        String customerId = generateCustomerId();
        try {
            customerService.registerCustomer(customerId, name, phone, address);
            out.println("Cliente registrado con ID " + customerId + ".");
        } catch (IllegalArgumentException exception) {
            out.println("No se pudo registrar el cliente: " + exception.getMessage());
        }
    }

    private String generateCustomerId() {
        while (true) {
            String id = "CLI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            boolean exists = customerService.getAllCustomers().stream()
                    .anyMatch(customer -> customer.getId().equals(id));
            if (!exists) return id;
        }
    }

    private void listSellers() {
        List<Seller> sellers = sellerService.getAllSellers();
        if (sellers.isEmpty()) {
            out.println("No hay vendedores registrados.");
            return;
        }
        out.println("Vendedores registrados:");
        for (Seller seller : sellers) {
            out.printf("- %s | %s%n", seller.getId(), seller.getName());
        }
    }

    private void registerSeller() {
        String name = readLine("Nombre del vendedor: ");
        if (name == null) return;
        String phone = readLine("Teléfono: ");
        if (phone == null) return;
        String address = readLine("Dirección: ");
        if (address == null) return;
        try {
            Seller seller = sellerService.registerSeller(name, phone, address);
            out.println("Vendedor registrado con ID " + seller.getId() + ".");
        } catch (IllegalArgumentException exception) {
            out.println("No se pudo registrar el vendedor: " + exception.getMessage());
        }
    }

    private void salesMenu() {
        boolean back = false;
        while (!back) {
            out.println();
            out.println("--- Ventas ---");
            out.println("1. Registrar venta");
            out.println("2. Listar ventas");
            out.println("3. Consultar venta por ID");
            out.println("4. Registrar abono");
            out.println("0. Volver al menú principal");
            String option = readLine("Seleccione una opción: ");
            if (option == null) return;
            switch (option) {
                case "1" -> registerSale();
                case "2" -> listSales();
                case "3" -> findSale();
                case "4" -> registerPayment();
                case "0" -> back = true;
                default -> out.println("Opción no válida. Intente nuevamente.");
            }
        }
    }

    private void registerSale() {
        if (productService.listAll().isEmpty()) {
            out.println("No hay productos disponibles en el catálogo.");
            return;
        }
        List<StockRequest> lines = new ArrayList<>();
        boolean addLine = true;
        while (addLine) {
            out.println("Productos disponibles:");
            showCatalogAndInventory();
            BeerProduct product = chooseProduct("Ingrese el ID del producto (vacío para cancelar): ");
            if (product == null) return;
            SalePresentation presentation = choosePresentation();
            if (presentation == null) return;
            Integer quantity = readPositiveInt("Cantidad de presentaciones: ");
            if (quantity == null) return;
            lines.add(new StockRequest(product.getId(), presentation, quantity));
            Boolean another = readYesNo("¿Desea agregar otra línea? (s/n): ");
            if (another == null) return;
            addLine = another;
        }

        if (!inventoryService.canFulfill(lines)) {
            out.println("Stock insuficiente para una o más líneas. No se registró la venta.");
            return;
        }
        BigDecimal total = saleService.previewTotal(lines);
        showSalePreview(lines, total);

        Seller seller = chooseSeller();
        if (seller == null) return;
        BigDecimal initialPayment = readMoney("Pago inicial (COP; use coma para decimales): ");
        if (initialPayment == null) return;
        while (initialPayment.compareTo(total) > 0) {
            out.println("El pago inicial no puede superar el total de la venta.");
            initialPayment = readMoney("Ingrese nuevamente el pago inicial (COP): ");
            if (initialPayment == null) return;
        }

        String customerId = null;
        if (initialPayment.compareTo(total) < 0) {
            Customer customer = chooseCustomer();
            if (customer == null) return;
            customerId = customer.getId();
        }

        out.println("Resumen final antes de registrar:");
        showSalePreview(lines, total);
        out.println("Vendedor: " + seller.getName() + " (" + seller.getId() + ")");
        out.println("Cliente: " + (customerId == null ? "No aplica; venta pagada" : customerId));
        out.println("Pago inicial: " + formatMoney(initialPayment));
        Boolean confirmed = readYesNo("¿Confirmar el registro de la venta? (s/n): ");
        if (confirmed == null || !confirmed) {
            out.println("Registro de venta cancelado.");
            return;
        }

        try {
            Sale sale = saleService.registerSale(lines, seller.getId(), customerId, initialPayment);
            out.println("Venta registrada correctamente.");
            printSaleSummary(sale);
        } catch (IllegalArgumentException | IllegalStateException | InsufficientStockException exception) {
            out.println("No se pudo registrar la venta: " + exception.getMessage());
        }
    }

    private void showSalePreview(List<StockRequest> lines, BigDecimal total) {
        out.println("Detalle de la venta:");
        for (StockRequest line : lines) {
            BeerProduct product = productService.findById(line.productId());
            BigDecimal unitPrice = productService.priceFor(line.productId(), line.presentation());
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(line.quantity()));
            out.printf("- %s | %s x %d | %s c/u | Subtotal: %s%n",
                    product.getName(), presentationName(line.presentation()), line.quantity(),
                    formatMoney(unitPrice), formatMoney(subtotal));
        }
        out.println("Total: " + formatMoney(total));
    }

    private void listSales() {
        List<Sale> sales = saleService.listAll();
        if (sales.isEmpty()) {
            out.println("No hay ventas registradas.");
            return;
        }
        for (Sale sale : sales) {
            printSaleSummary(sale);
        }
    }

    private void findSale() {
        String saleId = readLine("ID de la venta (vacío para volver): ");
        if (saleId == null || saleId.isBlank()) return;
        try {
            Sale sale = saleService.findById(saleId);
            printSaleSummary(sale);
            out.println("Detalle:");
            for (var line : sale.getLines()) {
                out.printf("- %s | %s x %d | Precio: %s | Subtotal: %s%n",
                        line.getProduct().getName(), presentationName(line.getPresentation()), line.getQuantity(),
                        formatMoney(line.getUnitPrice()), formatMoney(line.getSubtotal()));
            }
            out.println("Pagos registrados: " + sale.getPayments().size());
        } catch (IllegalArgumentException exception) {
            out.println("No se encontró la venta: " + exception.getMessage());
        }
    }

    private void registerPayment() {
        String saleId = readLine("ID de la venta pendiente (vacío para volver): ");
        if (saleId == null || saleId.isBlank()) return;
        Sale sale;
        try {
            sale = saleService.findById(saleId);
        } catch (IllegalArgumentException exception) {
            out.println("No se encontró la venta: " + exception.getMessage());
            return;
        }
        if (sale.calculateBalance().compareTo(BigDecimal.ZERO) <= 0) {
            out.println("La venta no tiene saldo pendiente.");
            return;
        }
        out.println("Saldo pendiente: " + formatMoney(sale.calculateBalance()));
        BigDecimal amount = readPositiveMoney("Valor del abono (COP; use coma para decimales): ");
        if (amount == null) return;
        try {
            Sale updatedSale = saleService.registerPayment(saleId, amount);
            out.println("Abono registrado correctamente.");
            out.println("Total pagado: " + formatMoney(updatedSale.calculateTotalPaid()));
            out.println("Saldo pendiente: " + formatMoney(updatedSale.calculateBalance()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            out.println("No se pudo registrar el abono: " + exception.getMessage());
        }
    }

    private void printSaleSummary(Sale sale) {
        Customer customer = sale.getCustomer();
        out.println("Venta " + sale.getId()
                + " | Fecha: " + SALE_DATE_FORMAT.format(sale.getCreatedAt())
                + " | Vendedor: " + sale.getSeller().getName()
                + " | Cliente: " + (customer == null ? "No aplica" : customer.getName())
                + " | Total: " + formatMoney(sale.calculateTotal())
                + " | Pagado: " + formatMoney(sale.calculateTotalPaid())
                + " | Saldo: " + formatMoney(sale.calculateBalance()));
    }

    private BeerProduct chooseProduct(String prompt) {
        while (true) {
            String productId = readLine(prompt);
            if (productId == null || productId.isBlank()) return null;
            try {
                return productService.findById(productId);
            } catch (IllegalArgumentException exception) {
                out.println("Producto inexistente: " + exception.getMessage());
                out.println("Consulte el catálogo e ingrese un ID listado.");
            }
        }
    }

    private SalePresentation choosePresentation() {
        out.println("Presentaciones:");
        out.println("1. Unidad");
        out.println("2. Media canasta");
        out.println("3. Canasta completa");
        out.println("0. Cancelar venta");
        while (true) {
            String option = readLine("Seleccione una presentación: ");
            if (option == null) return null;
            switch (option) {
                case "1" -> { return SalePresentation.UNIT; }
                case "2" -> { return SalePresentation.HALF_BASKET; }
                case "3" -> { return SalePresentation.FULL_BASKET; }
                case "0" -> { return null; }
                default -> out.println("Presentación no válida. Intente nuevamente.");
            }
        }
    }

    private Seller chooseSeller() {
        List<Seller> sellers = sellerService.getAllSellers();
        if (sellers.isEmpty()) {
            out.println("No hay vendedores registrados. Registre uno en la opción 3 del menú principal.");
            return null;
        }
        out.println("Vendedores disponibles:");
        sellers.forEach(seller -> out.printf("- %s | %s%n", seller.getId(), seller.getName()));
        while (true) {
            String sellerId = readLine("ID del vendedor (vacío para cancelar): ");
            if (sellerId == null || sellerId.isBlank()) return null;
            var seller = sellerService.getSellerById(sellerId);
            if (seller.isPresent()) return seller.get();
            out.println("No existe un vendedor con ese ID. Ingrese uno de la lista.");
        }
    }

    private Customer chooseCustomer() {
        List<Customer> customers = customerService.getAllCustomers();
        if (customers.isEmpty()) {
            out.println("La venta queda pendiente y requiere un cliente. Registre uno en la opción 3 del menú principal.");
            return null;
        }
        out.println("Clientes disponibles:");
        customers.forEach(customer -> out.printf("- %s | %s%n", customer.getId(), customer.getName()));
        while (true) {
            String customerId = readLine("ID del cliente (vacío para cancelar): ");
            if (customerId == null || customerId.isBlank()) return null;
            try {
                return customerService.getCustomerById(customerId);
            } catch (IllegalArgumentException exception) {
                out.println("No existe un cliente con ese ID. Ingrese uno de la lista.");
            }
        }
    }

    private Integer readPositiveInt(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (value == null) return null;
            try {
                int number = Integer.parseInt(value);
                if (number > 0) return number;
            } catch (NumberFormatException ignored) {
                // Se vuelve a pedir el dato sin interrumpir el menú.
            }
            out.println("Ingrese un número entero mayor que cero.");
        }
    }

    private Integer readNonNegativeInt(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (value == null) return null;
            try {
                int number = Integer.parseInt(value);
                if (number >= 0) return number;
            } catch (NumberFormatException ignored) {
                // Se vuelve a pedir el dato sin interrumpir el menú.
            }
            out.println("Ingrese un número entero igual o mayor que cero.");
        }
    }

    private BigDecimal readMoney(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (value == null) return null;
            try {
                BigDecimal amount = new BigDecimal(value.replace(',', '.'));
                if (amount.compareTo(BigDecimal.ZERO) >= 0) return amount;
            } catch (NumberFormatException ignored) {
                // Se vuelve a pedir el dato sin interrumpir el menú.
            }
            out.println("Ingrese un importe válido no negativo, sin separador de miles.");
        }
    }

    private BigDecimal readPositiveMoney(String prompt) {
        while (true) {
            BigDecimal amount = readMoney(prompt);
            if (amount == null) return null;
            if (amount.compareTo(BigDecimal.ZERO) > 0) return amount;
            out.println("El importe debe ser mayor que cero.");
        }
    }

    private Boolean readYesNo(String prompt) {
        while (true) {
            String answer = readLine(prompt);
            if (answer == null) return null;
            if (answer.equalsIgnoreCase("s") || answer.equalsIgnoreCase("sí")
                    || answer.equalsIgnoreCase("si")) return true;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) return false;
            out.println("Responda s o n.");
        }
    }

    private String readLine(String prompt) {
        out.print(prompt);
        if (!scanner.hasNextLine()) return null;
        return scanner.nextLine().trim();
    }

    private static String presentationName(SalePresentation presentation) {
        if (presentation == SalePresentation.UNIT) return "Unidad";
        if (presentation == SalePresentation.HALF_BASKET) return "Media canasta";
        if (presentation == SalePresentation.FULL_BASKET) return "Canasta completa";
        return presentation.code();
    }

    private static String formatMoney(BigDecimal amount) {
        return "$ " + amount.stripTrailingZeros().toPlainString() + " COP";
    }

    private void showPendingModule(String moduleName) {
        out.println(moduleName + ": funcionalidad pendiente de implementación.");
    }
}
