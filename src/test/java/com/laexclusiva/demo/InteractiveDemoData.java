package com.laexclusiva.demo;

import com.laexclusiva.model.*;
import com.laexclusiva.service.GoodsReceiptService.ReceiptLineRequest;
import com.laexclusiva.service.PurchaseOrderService.OrderLineRequest;
import com.laexclusiva.service.UserManagementService.CreateUserRequest;
import com.laexclusiva.ui.ApplicationServices;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/** Herramienta de revisión fuera del JAR: solo servicios reales sobre una composición nueva en memoria. */
public final class InteractiveDemoData {
    private InteractiveDemoData() { }

    /** Resultado sin credenciales ni sesión administrativa de carga. */
    public record LoadedDemo(ApplicationServices services, Report report) { }
    public record Report(int customers, int sellers, int products, int suppliers, int orders,
                         int sales, int users, int inventoryMovements, int cashMovements) { }

    /** Lee una configuración UTF-8 editable; null utiliza el recurso empaquetado. */
    public static Properties configuration(Path override) throws IOException {
        Properties result = new Properties();
        if (override != null) {
            try (Reader reader = Files.newBufferedReader(override, StandardCharsets.UTF_8)) { result.load(reader); }
        } else {
            var stream = InteractiveDemoData.class.getResourceAsStream("/com/laexclusiva/demo/interactive-demo.properties");
            if (stream == null) throw new IOException("Falta la configuración de demostración.");
            try (Reader reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) { result.load(reader); }
        }
        return result;
    }

    /**
     * Publica la composición únicamente cuando toda la carga y sus verificaciones terminan.
     * Cada invocación empieza vacía: nunca duplica ni borra los datos de una sesión previa.
     * Las ventas mantienen la fecha real: sus servicios no admiten fecha histórica.
     */
    public static LoadedDemo create(Properties config, String adminPassword, String workerPassword) {
        Objects.requireNonNull(config, "Configuración obligatoria");
        com.laexclusiva.security.PasswordPolicy.validate(adminPassword);
        com.laexclusiva.security.PasswordPolicy.validate(workerPassword);
        ApplicationServices s = ApplicationServices.create("memory", Map.of());
        s.identity().bootstrapInitialAdministrator("admin", adminPassword);
        UserSession admin = s.identity().authentication().createSession("admin", adminPassword).orElseThrow();
        try {
            List<String> customers = new ArrayList<>();
            for (int i = 1; i <= count(config, "customer.count"); i++) {
                String[] row = row(config, "customer." + i, 3);
                String id = "CLI-%03d".formatted(i);
                s.customers().registerCustomer(id, row[0], phone(row[1]), row[2]); customers.add(id);
            }
            // Observación real previa a compras, ventas y abonos; no se retocan sus fechas.
            s.collectionAssistant().saveProfile(admin, "CLI-007", "camila.duarte@example.test",
                    CollectionChannel.EMAIL, true, true, LocalDate.now().minusDays(3),
                    "Contacto autorizado y seguimiento de la cuenta comercial.");
            List<Seller> sellers = new ArrayList<>();
            for (int i = 1; i <= count(config, "seller.count"); i++) {
                String[] row = row(config, "seller." + i, 3);
                sellers.add(s.sellers().registerSeller(row[0], phone(row[1]), row[2]));
            }
            if (customers.size() < 15 || sellers.size() != 5) throw new IllegalArgumentException("La demo requiere 15 clientes y cinco vendedores.");
            for (int i = 1; i <= count(config, "product.count"); i++) {
                String[] row = row(config, "product." + i, 9);
                BeerProduct product = new BeerProduct(row[0], row[1], Integer.parseInt(row[2]),
                        Integer.parseInt(row[3]), ContainerType.valueOf(row[4]), row[5].isBlank() ? null : row[5]);
                product.definePrice(SalePresentation.UNIT, new BigDecimal(row[6]));
                product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal(row[7]));
                product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal(row[8]));
                s.products().register(product);
            }
            List<String> stockIds = new ArrayList<>();
            for (int i = 1; i <= count(config, "stock.count"); i++) {
                String[] row = row(config, "stock." + i, 3); stockIds.add(row[0]);
                int baskets = Integer.parseInt(row[1]), loose = Integer.parseInt(row[2]);
                int units = s.products().findById(row[0]).equivalentUnits(baskets, loose);
                if (units > 0) s.inventory().addStock(row[0], baskets, loose, "Ingreso de mercancía de apertura");
            }
            if (stockIds.size() < 8) throw new IllegalArgumentException("Faltan productos de la demo.");
            List<Supplier> suppliers = new ArrayList<>();
            for (int i = 1; i <= count(config, "supplier.count"); i++) {
                String[] row = row(config, "supplier." + i, 3);
                suppliers.add(s.purchases().suppliers().create(admin, row[0], row[1], row[2]));
            }
            for (int i = 0; i < count(config, "purchase.count"); i++) {
                var order = s.purchases().orders().create(admin, suppliers.get(i % suppliers.size()).id(),
                        "Abastecimiento de bebidas · orden " + (i + 1), List.of(
                        new OrderLineRequest(stockIds.get(i % 8), count(config, "purchase.baskets"), count(config, "purchase.loose")),
                        new OrderLineRequest(stockIds.get((i + 1) % 8), 1, 2)));
                if (i < 4) {
                    List<ReceiptLineRequest> receipt = order.lines().stream().map(line -> new ReceiptLineRequest(
                            line.id(), line.orderedBaskets(), line.orderedLooseUnits())).toList();
                    receiveAndCheck(s, admin, order.order().id(), "REC-%03d".formatted(i + 1), receipt);
                } else if (i < 7) {
                    receiveAndCheck(s, admin, order.order().id(), "REC-%03d".formatted(i + 1),
                            List.of(new ReceiptLineRequest(order.lines().get(0).id(), 1, 0)));
                } else if (i == 9) s.purchases().orders().cancel(admin, order.order().id());
            }
            int[] sellerSales = Arrays.stream(required(config, "sales.bySeller").split(","))
                    .map(String::trim).mapToInt(Integer::parseInt).toArray();
            if (sellerSales.length != sellers.size() || Arrays.stream(sellerSales).sum() < 30
                    || Arrays.stream(sellerSales).anyMatch(n -> n < 0 || n > 1000)) {
                throw new IllegalArgumentException("Distribuya al menos 30 ventas entre los cinco vendedores.");
            }
            int index = 0;
            for (int seller = 0; seller < sellers.size(); seller++) {
                for (int n = 0; n < sellerSales[seller]; n++, index++) {
                    List<StockRequest> lines = new ArrayList<>();
                    SalePresentation presentation = List.of(SalePresentation.UNIT, SalePresentation.HALF_BASKET,
                            SalePresentation.FULL_BASKET).get(index % 3);
                    lines.add(new StockRequest(stockIds.get(index % 8), presentation, presentation == SalePresentation.UNIT ? 2 : 1));
                    if (index % 3 == 0) lines.add(new StockRequest(stockIds.get((index + 1) % 8), SalePresentation.UNIT, 3));
                    BigDecimal total = lines.stream().map(line -> s.products().priceFor(line.productId(), line.presentation())
                            .multiply(BigDecimal.valueOf(line.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
                    int pattern = index % 4;
                    BigDecimal initial = switch (pattern) {
                        case 0 -> total;
                        case 1 -> BigDecimal.ZERO;
                        case 2 -> total.divide(BigDecimal.valueOf(4));
                        default -> total.divide(BigDecimal.valueOf(2));
                    };
                    Map<String, Integer> before = inventory(s);
                    Sale sale = s.sales().registerSale(lines, sellers.get(seller).getId(), customers.get(index % 10),
                            initial, "VTA-%03d".formatted(index + 1));
                    for (String productId : before.keySet()) {
                        int sold = lines.stream().filter(line -> line.productId().equals(productId))
                                .mapToInt(line -> Math.multiplyExact(s.products().unitsFor(productId, line.presentation()), line.quantity())).sum();
                        require(s.inventory().availableUnits(productId) == before.get(productId) - sold, "Descuento de venta incoherente");
                    }
                    if (pattern == 2) s.sales().registerPayment(sale.getId(), initial, "ABO-%03d".formatted(index + 1));
                    if (pattern == 3) s.sales().registerPayment(sale.getId(), sale.calculateBalance(), "ABO-%03d".formatted(index + 1));
                }
            }
            int[] debtCustomers = {6, 8, 10, 11};
            for (int i = 0; i < debtCustomers.length; i++) {
                BigDecimal amount = money(config, "generalDebt.base").add(money(config, "generalDebt.step").multiply(BigDecimal.valueOf(i)));
                String customer = customers.get(debtCustomers[i]);
                s.generalDebt().registerDebt(customer, amount, i % 2 == 0 ? "Envases" : "Servicio de entrega",
                        i % 2 == 0 ? "Cargo por envases pendientes de devolución" : "Cargo por servicio de entrega",
                        "CAR-%03d".formatted(i + 1));

                if (i < 3) s.generalDebt().registerPayment(customer, i == 2 ? amount : money(config, "generalDebt.partialPayment"),
                        "Abonos", "Abono a saldo de cuenta", "PAG-CAR-%03d".formatted(i + 1));
            }
            int manualCount = count(config, "cash.manual.count");
            CashCategory[] expenses = {CashCategory.OTHER_EXPENSE, CashCategory.SERVICES, CashCategory.ICE, CashCategory.OTHER_EXPENSE};
            String[] descriptions = {"Flete de abastecimiento", "Pago de energía y acueducto", "Compra de hielo", "Insumos de limpieza"};
            for (int i = 0; i < manualCount; i++) {
                boolean income = i % 4 == 0;
                int daysAgo = manualCount <= 1 ? 0 : 29 - (i * 29 / (manualCount - 1));
                int expenseIndex = Math.floorMod(i - 1 - i / 4, expenses.length);
                CashCategory category = income ? CashCategory.OTHER_INCOME : expenses[expenseIndex];
                s.cash().registerMovement(category.direction(), category, money(config, income ? "cash.income" : "cash.expense"),
                        LocalDate.now().minusDays(daysAgo).atTime(10 + i % 6, 0),
                        income ? "Aporte a fondo de caja" : descriptions[expenseIndex],
                        "CAJ-%03d".formatted(i + 1));
            }
            String[] usernames = {"elena.torres", "andres.molina", "operador", "paula.serrano"};
            for (int i = 0; i < usernames.length; i++) {
                var created = s.identity().userManagement().createUser(admin,
                        new CreateUserRequest(usernames[i], workerPassword, UserRole.WORKER, i != 3));
                if (i != 2) s.identity().userManagement().assignSeller(admin, created.id().toString(),
                        created.securityVersion(), sellers.get(i == 3 ? 2 : i).getId());
            }
            s.collectionAssistant().saveProfile(admin, "CLI-002", "julian.rivera@example.test",
                    CollectionChannel.EMAIL, true, true, LocalDate.now().minusDays(2), "Seguimiento de compromiso de pago vencido.");
            s.collectionAssistant().saveProfile(admin, "CLI-009", "lucia.beltran@example.test",
                    CollectionChannel.EMAIL, true, true, LocalDate.now().plusDays(2), "Confirmar abono en la próxima visita.");
            s.collectionAssistant().saveProfile(admin, "CLI-012", null,
                    CollectionChannel.PHONE, true, false, LocalDate.now().plusDays(3), "Contacto telefónico autorizado, sin avisos automáticos.");
            s.collectionAssistant().prepareWeeklyReminders(admin);
            verify(s);
            return new LoadedDemo(s, new Report(customers.size(), sellers.size(), s.products().listAll().size(),
                    suppliers.size(), s.purchases().orders().findAll(admin).size(), s.sales().listAll().size(),
                    s.identity().users().findAll().size(), s.inventory().allMovements().size(), s.cash().listMovements(null, null, null).size()));
        } finally { s.identity().authentication().revoke(admin); }
    }

    private static void receiveAndCheck(ApplicationServices s, UserSession admin, long order,
                                        String key, List<ReceiptLineRequest> requests) {
        Map<String, Integer> before = inventory(s);
        var receipt = s.purchases().receipts().receive(admin, order, key, "Recepción de mercancía en bodega", requests);
        for (String id : before.keySet()) {
            int received = receipt.lines().stream().filter(line -> line.orderLineBeforeReceipt().productId().equals(id))
                    .mapToInt(GoodsReceiptLine::receivedEquivalentUnits).sum();
            require(s.inventory().availableUnits(id) == before.get(id) + received, "Recepción no coincide con inventario");
        }
    }

    /** Reconciliación independiente de stock, cartera y efectivo; no corrige saldos artificialmente. */
    public static void verify(ApplicationServices s) {
        if (s.postgresMode()) throw new IllegalArgumentException("La demo solo verifica memoria.");
        for (BeerProduct product : s.products().listAll()) {
            int net = s.inventory().movementsOf(product.getId()).stream().mapToInt(movement ->
                    movement.type() == MovementType.ENTRY ? movement.units() : -movement.units()).sum();
            require(net == s.inventory().availableUnits(product.getId()) && net >= 0, "Historial de inventario incoherente");
        }
        for (Customer customer : s.customers().getAllCustomers()) {
            BigDecimal sales = s.sales().listAll().stream().filter(sale -> sale.getCustomer() != null
                    && sale.getCustomer().getId().equals(customer.getId())).map(Sale::calculateBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal general = s.generalDebt().listMovements(customer.getId(), null, null, null).stream()
                    .map(m -> m.type() == GeneralDebtMovementType.PAYMENT ? m.amount().negate() : m.amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            require(customer.getBalance().compareTo(sales.add(general)) == 0, "Saldo agregado incoherente");
            var balance = s.generalDebt().findBalance(customer.getId());
            require(balance.outstandingSalesDebt().compareTo(sales) == 0 && balance.generalDebt().compareTo(general) == 0,
                    "Deudas de ventas y general deben permanecer separadas");
        }
        BigDecimal receipts = s.sales().listAll().stream().flatMap(sale -> sale.getPayments().stream())
                .map(SalePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashReceipts = s.cash().listMovements(null, null, null).stream()
                .filter(m -> m.category() == CashCategory.SALE_INITIAL_PAYMENT || m.category() == CashCategory.SALE_INSTALLMENT)
                .map(CashMovement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        require(receipts.compareTo(cashReceipts) == 0, "Caja duplicó u omitió pagos de ventas");
        BigDecimal generalPayments = s.generalDebt().listMovements(null, null, null, null).stream()
                .filter(m -> m.type() == GeneralDebtMovementType.PAYMENT).map(GeneralDebtMovement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        require(s.cash().summarize(null, null).generalDebtReceipts().compareTo(generalPayments) == 0,
                "Caja no coincide con pagos de deuda general");
        var movements = s.cash().listMovements(null, null, null);
        require(movements.stream().map(m -> m.sourceType() + ":" + m.sourceReference()).distinct().count() == movements.size(),
                "Origen de caja duplicado");
    }

    private static Map<String, Integer> inventory(ApplicationServices s) {
        Map<String, Integer> result = new LinkedHashMap<>();
        s.products().listAll().forEach(product -> result.put(product.getId(), s.inventory().availableUnits(product.getId())));
        return result;
    }
    private static String required(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta la opción demo " + key);
        return value.trim();
    }
    private static int count(Properties config, String key) {
        int value = Integer.parseInt(required(config, key));
        if (value < 1 || value > 1000) throw new IllegalArgumentException("Cantidad demo fuera de rango: " + key);
        return value;
    }
    private static BigDecimal money(Properties config, String key) { return new BigDecimal(required(config, key)); }
    private static String[] row(Properties config, String key, int size) {
        String[] result = required(config, key).split("\\|", -1);
        if (result.length != size) throw new IllegalArgumentException("Campos incorrectos en " + key);
        return Arrays.stream(result).map(String::trim).toArray(String[]::new);
    }
    private static String phone(String value) {
        if (!value.matches("3[0-9]{9}")) throw new IllegalArgumentException("Teléfono demo colombiano inválido en formato.");
        return value;
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
}
