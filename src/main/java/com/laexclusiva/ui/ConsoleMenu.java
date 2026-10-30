package com.laexclusiva.ui;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.StockSnapshot;
import com.laexclusiva.service.AuthorizationService;
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

/**
 * Interfaz de consola para consultar el catálogo y el inventario, gestionar personas y operar
 * ventas y abonos. Delega las reglas de negocio a los servicios recibidos por constructor;
 * conserva aquí la navegación, la lectura de entradas y la presentación de resultados.
 */
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
    private final UserSession session;
    private final AuthorizationService authorizationService;

    /**
     * Crea el menú con los servicios de cada módulo y los flujos de entrada y salida.
     * El menú usa el {@link Scanner} y el {@link PrintStream}, pero no los cierra porque su
     * ciclo de vida pertenece al punto de entrada de la aplicación.
     *
     * @param productService servicio de consulta del catálogo y precios
     * @param inventoryService servicio de consulta y actualización de existencias
     * @param customerService servicio de registro y consulta de clientes
     * @param sellerService servicio de registro y consulta de vendedores
     * @param saleService servicio de cálculo y registro de ventas y abonos
     * @param scanner fuente de líneas ingresadas por el usuario
     * @param out destino de los mensajes del menú
     * @throws NullPointerException si alguna dependencia o flujo de consola es nulo
     */
    public ConsoleMenu(ProductService productService, InventoryService inventoryService,
                       CustomerService customerService, SellerService sellerService,
                       SaleService saleService, Scanner scanner, PrintStream out, UserSession session, AuthorizationService authorizationService) {
        this.productService = Objects.requireNonNull(productService, "El servicio de productos es obligatorio.");
        this.inventoryService = Objects.requireNonNull(inventoryService, "El servicio de inventario es obligatorio.");
        this.customerService = Objects.requireNonNull(customerService, "El servicio de clientes es obligatorio.");
        this.sellerService = Objects.requireNonNull(sellerService, "El servicio de vendedores es obligatorio.");
        this.saleService = Objects.requireNonNull(saleService, "El servicio de ventas es obligatorio.");
        this.scanner = Objects.requireNonNull(scanner, "La entrada de consola es obligatoria.");
        this.out = Objects.requireNonNull(out, "La salida de consola es obligatoria.");
        this.session = session;
        this.authorizationService = Objects.requireNonNull(authorizationService, "El servicio de autorización es obligatorio.");
        if (session == null || !session.getUserAccount().isActive()) {
            throw new IllegalArgumentException("Se requiere una sesión activa.");
        }
    }

    /**
     * Ejecuta el ciclo principal y dirige cada opción al submenú correspondiente.
     * Una entrada finalizada termina el ciclo; la opción de salida muestra el mensaje de cierre.
     */
    public void start() {
        if (!session.getUserAccount().isActive()) {
            out.println("Acceso denegado: la cuenta está inactiva.");
            return;
        }
        boolean running = true;
        while (running) {
            showMainMenu();
            String option = readLine("");
            if (option == null) {
                return;
            }
            // Cada módulo conserva su navegación; las opciones aún no implementadas informan su estado.
            switch (option) {
                case "1" -> inventoryMenu();
                case "2" -> salesMenu();
                case "3" -> peopleMenu();
                case "5" -> portfolioMenu();
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
        if ((can(Permission.VIEW_CATALOG) && can(Permission.VIEW_INVENTORY)) || can(Permission.MANAGE_INVENTORY_ENTRIES)) out.println("1. Productos e inventario");
        if (can(Permission.REGISTER_SALE) || can(Permission.VIEW_SALES) || can(Permission.REGISTER_PAYMENT)) out.println("2. Ventas");
        if (can(Permission.REGISTER_CUSTOMER) || can(Permission.VIEW_CUSTOMER) || can(Permission.VIEW_SELLERS) || can(Permission.REGISTER_SELLERS)) out.println("3. Clientes y vendedores");
        if (can(Permission.VIEW_PORTFOLIO)) out.println("5. Consultar cartera");
        out.println("0. Salir");
        out.print("Seleccione una opción: ");
    }

    /** Mantiene el submenú de inventario abierto hasta volver al principal o terminar la entrada. */
    private void inventoryMenu() {
        if (!requireAny(Permission.VIEW_CATALOG, Permission.VIEW_INVENTORY, Permission.MANAGE_INVENTORY_ENTRIES)) return;
        boolean back = false;
        while (!back) {
            out.println();
            out.println("--- Productos e inventario ---");
            if (can(Permission.VIEW_CATALOG) && can(Permission.VIEW_INVENTORY)) out.println("1. Consultar catálogo e inventario");
            if (can(Permission.MANAGE_INVENTORY_ENTRIES)) out.println("2. Registrar ingreso de existencias");
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
        if (!requireAll(Permission.VIEW_CATALOG, Permission.VIEW_INVENTORY)) return;
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
        if (!require(Permission.MANAGE_INVENTORY_ENTRIES)) return;
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

    /** Agrupa la navegación de consulta y registro de clientes y vendedores. */
    private void peopleMenu() {
        if (!requireAny(Permission.REGISTER_CUSTOMER, Permission.VIEW_CUSTOMER, Permission.VIEW_SELLERS, Permission.REGISTER_SELLERS)) return;
        boolean back = false;
        while (!back) {
            out.println();
            out.println("--- Clientes y vendedores ---");
            if (can(Permission.VIEW_CUSTOMER)) out.println("1. Listar clientes");
            if (can(Permission.REGISTER_CUSTOMER)) out.println("2. Registrar cliente");
            if (can(Permission.VIEW_SELLERS)) out.println("3. Listar vendedores");
            if (can(Permission.REGISTER_SELLERS)) out.println("4. Registrar vendedor");
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
        if (!require(Permission.VIEW_CUSTOMER)) return;
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
        if (!require(Permission.REGISTER_CUSTOMER)) return;
        String name = readLine("Nombre del cliente: ");
        if (name == null) return;
        String phone = readLine("Teléfono: ");
        if (phone == null) return;
        String address = readLine("Dirección: ");
        if (address == null) return;
        // El servicio recibe el identificador ya generado; la consola evita reutilizar uno existente.
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
        if (!require(Permission.VIEW_SELLERS)) return;
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
        if (!require(Permission.REGISTER_SELLERS)) return;
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

    /** Mantiene las opciones de venta y abonos disponibles hasta volver al menú principal. */
    private void salesMenu() {
        if (!requireAny(Permission.REGISTER_SALE, Permission.VIEW_SALES, Permission.REGISTER_PAYMENT)) return;
        boolean back = false;
        while (!back) {
            out.println();
            out.println("--- Ventas ---");
            if (can(Permission.REGISTER_SALE)) out.println("1. Registrar venta");
            if (can(Permission.VIEW_SALES)) {
                out.println("2. Listar ventas");
                out.println("3. Consultar venta por ID");
            }
            if (can(Permission.REGISTER_PAYMENT)) out.println("4. Registrar abono");
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
        if (!require(Permission.REGISTER_SALE)) return;
        if (productService.listAll().isEmpty()) {
            out.println("No hay productos disponibles en el catálogo.");
            return;
        }
        List<StockRequest> lines = new ArrayList<>();
        boolean addLine = true;
        // Se reúne y cotiza el detalle primero; hasta la confirmación no se solicita registrar cambios.
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

        // La prevalidación evita continuar con una venta imposible; el servicio vuelve a validar al registrarla.
        if (!inventoryService.canFulfill(lines)) {
            out.println("Stock insuficiente para una o más líneas. No se registró la venta.");
            return;
        }
        BigDecimal total = saleService.previewTotal(lines);
        showSalePreview(lines, total);

        Seller seller;
        if (authorizationService.isAdministrator(session)) {
            seller = chooseSeller();
        } else {
            String associatedSellerId = session.getUserAccount().getSellerId();
            seller = associatedSellerId == null ? null : sellerService.getSellerById(associatedSellerId).orElse(null);
            if (seller == null) {
                out.println("No se puede registrar la venta: la cuenta no tiene un vendedor válido asociado.");
                return;
            }
        }
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
            // Solo se asocia un cliente cuando la venta queda con saldo pendiente.
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

        // SaleService coordina el descuento, la deuda del cliente y el guardado de la venta.
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
        if (!require(Permission.VIEW_SALES)) return;
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
        if (!require(Permission.VIEW_SALES)) return;
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
        if (!require(Permission.REGISTER_PAYMENT)) return;
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
            // El servicio actualiza el pago de la venta y el saldo asignado al cliente.
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

    /** Lee un importe no negativo, admite coma o punto decimal y devuelve {@code null} al finalizar la entrada. */
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

    /** Lee y recorta una línea; devuelve {@code null} cuando no quedan datos de entrada. */
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

    private void portfolioMenu() {
        if (!require(Permission.VIEW_PORTFOLIO)) return;
        List<Customer> debtors = customerService.getAllCustomers().stream()
                .filter(customer -> customer.getBalance().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (debtors.isEmpty()) {
            out.println("No hay clientes con saldo pendiente.");
            return;
        }
        out.println("Cartera pendiente:");
        debtors.forEach(customer -> out.printf("- %s | %s | Saldo: %s%n",
                customer.getId(), customer.getName(), formatMoney(customer.getBalance())));
    }

    private boolean can(Permission permission) {
        return authorizationService.hasPermission(session, permission);
    }

    private boolean require(Permission permission) {
        if (session.getUserAccount().isActive() && can(permission)) return true;
        out.println("Acceso denegado: permiso " + permission + " requerido.");
        return false;
    }

    private boolean requireAny(Permission... permissions) {
        for (Permission permission : permissions) if (can(permission)) return true;
        out.println("Acceso denegado: no tiene permisos para esta opción.");
        return false;
    }

    private boolean requireAll(Permission... permissions) {
        for (Permission permission : permissions) {
            if (!can(permission)) {
                out.println("Acceso denegado: permiso " + permission + " requerido.");
                return false;
            }
        }
        return true;
    }
}
