package com.laexclusiva.ui;

import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.persistence.jdbc.DatabaseConfig;
import com.laexclusiva.persistence.jdbc.InventoryRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.InventoryStockAdjusterJdbc;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcTransactionManager;
import com.laexclusiva.repository.CashMovementRepository;
import com.laexclusiva.persistence.jdbc.ProductRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.StockMovementRepositoryJdbc;
import com.laexclusiva.repository.CustomerDebtQuery;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemorySaleRepository;
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
import com.laexclusiva.repository.jdbc.SaleRepositoryJdbc;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
import com.laexclusiva.service.CustomerService;
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

    private ApplicationServices(CustomerService customers, SellerService sellers, ProductService products,
                                InventoryService inventory, SalesWorkflow sales,
                                CustomerDebtQuery debtQuery, boolean postgresMode,
                                CustomerGeneralDebtWorkflow generalDebt, CashWorkflow cash,
                                IdentityServices identity, Services purchases) {
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
    }

    /**
     * Selecciona la composición mediante {@value #MODE_ENV}; memoria es el modo predeterminado.
     * Las credenciales JDBC se leen solo desde el entorno del proceso.
     *
     * @return servicios compuestos para el modo configurado
     */
    public static ApplicationServices fromEnvironment() {
        return create(System.getenv().getOrDefault(MODE_ENV, MEMORY_MODE), System.getenv());
    }

    /** Variante inyectable para probar la selección de modo sin leer ni registrar secretos. */
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
        return switch (mode.trim().toLowerCase()) {
            case MEMORY_MODE -> inMemory();
            case POSTGRES_MODE -> postgres(DatabaseConfig.fromEnvironment(environment));
            default -> throw new IllegalArgumentException(MODE_ENV + " solo admite memory o postgres.");
        };
    }

    private static ApplicationServices inMemory() {
        CustomerRepositoryInterface customerRepository = new CustomerRepository();
        SellerRepositoryInterface sellerRepository = new SellerRepository();
        SellerService sellers = new SellerService(sellerRepository);
        IdentityServices identity = IdentityServices.inMemory(sellers);
        ProductRepository productRepository = new InMemoryProductRepository();
        InMemoryInventoryRepository inventoryRepository = new InMemoryInventoryRepository();
        InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
        SaleRepository saleRepository = new InMemorySaleRepository();
        CustomerService customers = new CustomerService(customerRepository);
        ProductService products = new ProductService(productRepository);
        products.registerAll(InitialCatalog.create());
        InventoryService inventory = new InventoryService(products, inventoryRepository, movements);
        SaleService saleService = new SaleService(saleRepository, products, inventory, customers, sellers);
        SalesWorkflow sales = new InMemorySalesWorkflow(saleService, customers);
        CustomerGeneralDebtWorkflow generalDebt = new InMemoryCustomerGeneralDebtWorkflow(customers, sales);
        CashWorkflow cash = new InMemoryCashWorkflow(sales, generalDebt);
        Services purchases = PurchasesModuleFactory.inMemory(products, inventoryRepository, movements,
                identity.authorization(), identity.users());
        return new ApplicationServices(customers, sellers, products, inventory, sales, null, false,
                generalDebt, cash, identity, purchases);
    }

    private static ApplicationServices postgres(DatabaseConfig config) {
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        CustomerRepositoryJdbc customerRepository = new CustomerRepositoryJdbc(provider);
        SellerRepositoryJdbc sellerRepository = new SellerRepositoryJdbc(provider);
        SellerService sellers = new SellerService(sellerRepository);
        IdentityServices identity = IdentityServices.postgres(provider, sellers);
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
                movementRepository, transactions, cashRepository);
        CustomerGeneralDebtWorkflow generalDebt = new CustomerGeneralDebtWorkflowJdbc(customerRepository,
                debtQuery, customerBalances, generalDebtRepository, cashRepository, transactions);
        CashWorkflow cash = new CashWorkflowJdbc(cashRepository, transactions);
        Services purchases = PurchasesModuleFactory.postgres(provider, identity.authorization(), identity.users(),
                products, transactions, inventoryAdjuster, movementRepository);
        return new ApplicationServices(customers, sellers, products, inventory, sales, debtQuery, true,
                generalDebt, cash, identity, purchases);
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
}
