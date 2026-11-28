package com.laexclusiva.ui;

import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.persistence.jdbc.DatabaseConfig;
import com.laexclusiva.persistence.jdbc.InventoryRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.InventoryStockAdjusterJdbc;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcTransactionManager;
import com.laexclusiva.repository.CashMovementRepository;
import com.laexclusiva.repository.AdministrativeAuditRepository;
import com.laexclusiva.repository.CustomerCollectionRepository;
import com.laexclusiva.persistence.jdbc.ProductRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.StockMovementRepositoryJdbc;
import com.laexclusiva.repository.CustomerDebtQuery;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemorySaleRepository;
import com.laexclusiva.repository.InMemoryAdministrativeAuditRepository;
import com.laexclusiva.repository.InMemoryCustomerCollectionRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.InventoryRepository;
import com.laexclusiva.repository.ProductRepository;
import com.laexclusiva.repository.SaleRepository;
import com.laexclusiva.repository.SellerRepository;
import com.laexclusiva.repository.SellerRepositoryInterface;
import com.laexclusiva.repository.StockMovementRepository;
import com.laexclusiva.repository.jdbc.CustomerBalanceAdjusterJdbc;
import com.laexclusiva.repository.jdbc.CustomerDebtQueryJdbc;
import com.laexclusiva.repository.jdbc.CustomerGeneralDebtMovementRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CashMovementRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerCollectionRepositoryJdbc;
import com.laexclusiva.repository.jdbc.AdministrativeAuditRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SaleRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.CollectionAssistantService;
import com.laexclusiva.service.AccountStatementPdfGenerator;
import com.laexclusiva.service.ColombiaContactWindow;
import com.laexclusiva.service.EmailDeliveryProvider;
import com.laexclusiva.service.SimulatedEmailDeliveryProvider;
import com.laexclusiva.service.SmtpEmailDeliveryProvider;
import com.laexclusiva.service.AdministrativeAuditService;
import com.laexclusiva.service.CustomerGeneralDebtWorkflow;
import com.laexclusiva.service.CustomerGeneralDebtWorkflowJdbc;
import com.laexclusiva.service.InMemoryCustomerGeneralDebtWorkflow;
import com.laexclusiva.service.CashWorkflow;
import com.laexclusiva.service.CashWorkflowJdbc;
import com.laexclusiva.service.InMemoryCashWorkflow;
import com.laexclusiva.service.InMemorySalesWorkflow;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.JdbcSalesWorkflow;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.PurchasesModuleFactory;
import com.laexclusiva.service.PurchasesModuleFactory.Services;
import com.laexclusiva.service.SaleService;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.service.SellerService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Ensambla una sola configuración coherente de repositorios para la aplicación JavaFX. */
public final class ApplicationServices {
    public static final String MODE_ENV = "LAEXCLUSIVA_PERSISTENCE_MODE";
    public static final String MEMORY_MODE = "memory";
    public static final String POSTGRES_MODE = "postgres";

    private final CustomerService customers;
    private final SellerService sellers;
    private final ProductService products;
    private final InventoryService inventory;
    private final SalesWorkflow sales;
    private final CustomerDebtQuery debtQuery;
    private final boolean postgresMode;
    private final CustomerGeneralDebtWorkflow generalDebt;
    private final CashWorkflow cash;
    private final IdentityServices identity;
    private final Services purchases;
    private final CollectionAssistantService collections;
    private final AdministrativeAuditService administrativeAudit;

    private ApplicationServices(CustomerService customers, SellerService sellers, ProductService products,
                                InventoryService inventory, SalesWorkflow sales,
                                CustomerDebtQuery debtQuery, boolean postgresMode,
                                CustomerGeneralDebtWorkflow generalDebt, CashWorkflow cash,
                                IdentityServices identity, Services purchases,
                                CollectionAssistantService collections,
                                AdministrativeAuditService administrativeAudit) {
        this.customers = customers;
        this.sellers = sellers;
        this.products = products;
        this.inventory = inventory;
        this.sales = sales;
        this.debtQuery = debtQuery;
        this.postgresMode = postgresMode;
        this.generalDebt = generalDebt;
        this.cash = cash;
        this.identity = identity;
        this.purchases = purchases;
        this.collections = collections;
        this.administrativeAudit = administrativeAudit;
    }

    /**
     * Selecciona la composición mediante {@value #MODE_ENV}; PostgreSQL es el modo predeterminado
     * para impedir que una ejecución comercial use almacenamiento volátil por omisión.
     * Las credenciales JDBC se leen solo desde el entorno del proceso.
     *
     * @return servicios compuestos para el modo configurado
     */
    public static ApplicationServices fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /**
     * Selecciona PostgreSQL cuando el entorno no declara un modo; memoria exige selección explícita.
     * Esta variante permite verificar el arranque sin alterar el entorno del proceso.
     *
     * @param environment variables de proceso disponibles
     * @return composición coherente con el modo seleccionado
     * @throws IllegalArgumentException si falta la configuración PostgreSQL o el modo no es válido
     */
    public static ApplicationServices fromEnvironment(Map<String, String> environment) {
        if (environment == null) throw new IllegalArgumentException("El entorno de configuración es obligatorio.");
        return create(environment.getOrDefault(MODE_ENV, POSTGRES_MODE), environment);
    }

    /**
     * Construye la composición sin registrar credenciales ni abrir conexiones durante el arranque.
     *
     * @param mode modo explícito: {@code memory} o {@code postgres}
     * @param environment variables de configuración JDBC
     * @return servicios coherentes con el modo indicado
     */
    public static ApplicationServices create(String mode, Map<String, String> environment) {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException(MODE_ENV + " debe ser memory o postgres.");
        }
        Map<String, String> configured = environment == null ? Map.of() : Map.copyOf(environment);
        return switch (mode.trim().toLowerCase()) {
            case MEMORY_MODE -> inMemory(configured);
            case POSTGRES_MODE -> postgres(DatabaseConfig.fromEnvironment(configured), configured);
            default -> throw new IllegalArgumentException(MODE_ENV + " solo admite memory o postgres.");
        };
    }

    private static ApplicationServices inMemory(Map<String, String> environment) {
        if (enabled(environment, "LAEXCLUSIVA_SMTP_ENABLED")) {
            throw new IllegalArgumentException("El envío SMTP real requiere persistencia PostgreSQL.");
        }
        SellerRepositoryInterface sellerRepository = new SellerRepository();
        SellerService sellers = new SellerService(sellerRepository);
        InMemoryAdministrativeAuditRepository auditRepository = new InMemoryAdministrativeAuditRepository();
        IdentityServices identity = IdentityServices.inMemory(sellers, auditRepository);
        ProductRepository productRepository = new InMemoryProductRepository();
        InMemoryInventoryRepository inventoryRepository = new InMemoryInventoryRepository();
        InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
        SaleRepository saleRepository = new InMemorySaleRepository();
        CustomerCollectionRepository collectionRepository = new InMemoryCustomerCollectionRepository();
        var generalDebtReference = new AtomicReference<InMemoryCustomerGeneralDebtWorkflow>();
        CustomerRepository customerRepository = new CustomerRepository(id ->
                saleRepository.findAll().stream().anyMatch(sale ->
                        sale.getCustomer() != null && id.equals(sale.getCustomer().getId()))
                || !generalDebtReference.get().listMovements(id, null, null, null).isEmpty()
                || collectionRepository.findProfile(id).isPresent()
                || !collectionRepository.findFollowUps(id).isEmpty()
                || java.util.Arrays.stream(CustomerCollectionReminder.Status.values())
                        .anyMatch(status -> collectionRepository.findReminders(status).stream()
                                .anyMatch(reminder -> id.equals(reminder.customerId()))));
        CustomerService customers = new CustomerService(customerRepository);
        ProductService products = new ProductService(productRepository);
        products.registerAll(InitialCatalog.create());
        InventoryService inventory = new InventoryService(products, inventoryRepository, movements);
        SaleService saleService = new SaleService(saleRepository, products, inventory, customers, sellers).requireAuthenticatedSales();
        SalesWorkflow sales = new InMemorySalesWorkflow(saleService, customers);
        var generalDebt = new InMemoryCustomerGeneralDebtWorkflow(customers, sales);
        generalDebtReference.set(generalDebt);
        CashWorkflow cash = new InMemoryCashWorkflow(sales, generalDebt);
        Services purchases = PurchasesModuleFactory.inMemory(products, inventoryRepository, movements,
                identity.authorization(), identity.users());
        AdministrativeAuditService auditService = new AdministrativeAuditService(auditRepository, identity.authorization());
        CollectionAssistantService collections = new CollectionAssistantService(customers, sales, generalDebt,
                collectionRepository, auditRepository, identity.authorization(), new SimulatedEmailDeliveryProvider(),
                new AccountStatementPdfGenerator(), contactWindow(environment), directTransactions(),
                Clock.system(ZoneId.of("America/Bogota")), environment.get("LAEXCLUSIVA_PAYMENT_KEY"),
                enabled(environment, "LAEXCLUSIVA_PAYMENT_KEY_OWNER_CONFIRMED"),
                enabled(environment, "LAEXCLUSIVA_PAYMENT_KEY_VERIFIED_BY_ADMIN"));
        return new ApplicationServices(customers, sellers, products, inventory, sales, null, false,
                generalDebt, cash, identity, purchases, collections, auditService);
    }

    private static ApplicationServices postgres(DatabaseConfig config, Map<String, String> environment) {
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        CustomerRepositoryJdbc customerRepository = new CustomerRepositoryJdbc(provider);
        SellerRepositoryJdbc sellerRepository = new SellerRepositoryJdbc(provider);
        SellerService sellers = new SellerService(sellerRepository);
        AdministrativeAuditRepository auditRepository = new AdministrativeAuditRepositoryJdbc(provider);
        IdentityServices identity = IdentityServices.postgres(provider, sellers, auditRepository);
        ProductRepositoryJdbc productRepository = new ProductRepositoryJdbc(provider, transactions);
        InventoryRepositoryJdbc inventoryRepository = new InventoryRepositoryJdbc(provider, transactions);
        StockMovementRepositoryJdbc movementRepository = new StockMovementRepositoryJdbc(provider);
        SaleRepositoryJdbc saleRepository = new SaleRepositoryJdbc(provider, transactions);
        CustomerDebtQuery debtQuery = new CustomerDebtQueryJdbc(provider);
        CustomerBalanceAdjusterJdbc customerBalances = new CustomerBalanceAdjusterJdbc(provider);
        InventoryStockAdjusterJdbc inventoryAdjuster = new InventoryStockAdjusterJdbc(provider);
        CashMovementRepository cashRepository = new CashMovementRepositoryJdbc(provider, transactions);
        CustomerGeneralDebtMovementRepositoryJdbc generalDebtRepository =
                new CustomerGeneralDebtMovementRepositoryJdbc(provider, transactions);

        CustomerService customers = new CustomerService(customerRepository);
        ProductService products = new ProductService(productRepository);
        InventoryService inventory = new InventoryService(products, inventoryRepository,
                movementRepository, inventoryAdjuster, transactions);
        SalesWorkflow sales = new JdbcSalesWorkflow(productRepository, customerRepository, sellerRepository,
                saleRepository, saleRepository, debtQuery, customerBalances, inventoryAdjuster,
                movementRepository, transactions, cashRepository).requireAuthenticatedSales();
        CustomerGeneralDebtWorkflow generalDebt = new CustomerGeneralDebtWorkflowJdbc(customerRepository,
                debtQuery, customerBalances, generalDebtRepository, cashRepository, transactions);
        CashWorkflow cash = new CashWorkflowJdbc(cashRepository, transactions);
        CustomerCollectionRepository collectionRepository = new CustomerCollectionRepositoryJdbc(provider, transactions);
        AdministrativeAuditService auditService = new AdministrativeAuditService(auditRepository,
                identity.authorization());
        CollectionAssistantService collections = new CollectionAssistantService(customers, sales, generalDebt,
                collectionRepository, auditRepository, identity.authorization(), emailProvider(environment),
                new AccountStatementPdfGenerator(), contactWindow(environment), transactions,
                Clock.system(ZoneId.of("America/Bogota")), environment.get("LAEXCLUSIVA_PAYMENT_KEY"),
                enabled(environment, "LAEXCLUSIVA_PAYMENT_KEY_OWNER_CONFIRMED"),
                enabled(environment, "LAEXCLUSIVA_PAYMENT_KEY_VERIFIED_BY_ADMIN"));
        Services purchases = PurchasesModuleFactory.postgres(provider, identity.authorization(), identity.users(),
                products, transactions, inventoryAdjuster, movementRepository);
        return new ApplicationServices(customers, sellers, products, inventory, sales, debtQuery, true,
                generalDebt, cash, identity, purchases, collections, auditService);
    }

    private static EmailDeliveryProvider emailProvider(Map<String, String> environment) {
        if (!enabled(environment, "LAEXCLUSIVA_SMTP_ENABLED")) return new SimulatedEmailDeliveryProvider();
        try {
            return new SmtpEmailDeliveryProvider(required(environment, "LAEXCLUSIVA_SMTP_HOST"),
                    Integer.parseInt(required(environment, "LAEXCLUSIVA_SMTP_PORT")),
                    required(environment, "LAEXCLUSIVA_SMTP_USERNAME"),
                    required(environment, "LAEXCLUSIVA_SMTP_PASSWORD"),
                    required(environment, "LAEXCLUSIVA_SMTP_FROM"));
        } catch (RuntimeException invalidConfiguration) {
            throw new IllegalArgumentException("La configuración SMTP habilitada está incompleta o no es válida.");
        }
    }

    private static ColombiaContactWindow contactWindow(Map<String, String> environment) {
        String yearValue = environment.get("LAEXCLUSIVA_COLOMBIA_HOLIDAYS_YEAR");
        String holidayValues = environment.get("LAEXCLUSIVA_COLOMBIA_HOLIDAYS");
        int currentYear = LocalDate.now(ZoneId.of("America/Bogota")).getYear();
        if (yearValue == null || holidayValues == null || holidayValues.isBlank()) {
            return new ColombiaContactWindow(Set.of(), false);
        }
        try {
            if (Integer.parseInt(yearValue) != currentYear) return new ColombiaContactWindow(Set.of(), false);
            Set<LocalDate> holidays = Arrays.stream(holidayValues.split(","))
                    .map(String::trim).map(LocalDate::parse).collect(Collectors.toUnmodifiableSet());
            if (holidays.isEmpty() || holidays.stream().anyMatch(date -> date.getYear() != currentYear)) {
                return new ColombiaContactWindow(Set.of(), false);
            }
            return new ColombiaContactWindow(holidays, true);
        } catch (RuntimeException invalidCalendar) {
            return new ColombiaContactWindow(Set.of(), false);
        }
    }

    private static boolean enabled(Map<String, String> environment, String key) {
        return "true".equalsIgnoreCase(environment.getOrDefault(key, "false").trim());
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }

    private static com.laexclusiva.persistence.jdbc.TransactionBoundary directTransactions() {
        return new com.laexclusiva.persistence.jdbc.TransactionBoundary() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> operation) { return operation.get(); }
        };
    }

    /** Servicio de clientes conectado al repositorio seleccionado. */
    public CustomerService customers() { return customers; }
    /** Servicio de vendedores conectado al repositorio seleccionado. */
    public SellerService sellers() { return sellers; }
    /** Servicio de catálogo conectado al repositorio seleccionado. */
    public ProductService products() { return products; }
    /** Servicio de inventario conectado a los ajustes por delta si se usa PostgreSQL. */
    public InventoryService inventory() { return inventory; }
    /** Flujo de ventas y abonos adaptado al almacenamiento seleccionado. */
    public SalesWorkflow sales() { return sales; }
    /** Consulta de deuda de ventas; es nula en el modo en memoria. */
    public CustomerDebtQuery debtQuery() { return debtQuery; }
    /** Indica si la composición utiliza PostgreSQL. */
    public boolean postgresMode() { return postgresMode; }
    /** Flujos de deuda general y auditoría separados de los abonos a ventas. */
    public CustomerGeneralDebtWorkflow generalDebt() { return generalDebt; }
    /** Registro de efectivo recibido y egresado, sin sumar facturación como efectivo. */
    public CashWorkflow cash() { return cash; }
    /** Identidad, autenticación, autorización y administración compartidas por la aplicación. */
    public IdentityServices identity() { return identity; }
    /** Autorizador vinculado al emisor de sesiones de esta composición. */
    public com.laexclusiva.service.AuthorizationService authorization() { return identity.authorization(); }
    /** Proveedores, pedidos y recepción conectados al mismo catálogo, inventario y autoridad. */
    public Services purchases() { return purchases; }
    /** Asistente de cartera, preferencias de contacto, estados comerciales y avisos revisables. */
    public CollectionAssistantService collectionAssistant() { return collections; }
    /** Lectura de auditoría administrativa revalidada contra la sesión actual. */
    public AdministrativeAuditService administrativeAudit() { return administrativeAudit; }
}
