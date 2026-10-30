package com.laexclusiva;

import com.laexclusiva.demo.DemoDataInitializer;
import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.*;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.service.*;
import com.laexclusiva.ui.ConsoleMenu;
import java.util.Scanner;

/** Punto de entrada y composición de dependencias de La Exclusiva. */
public final class Main {
    private Main() { }

    /** Construye dependencias, autentica al usuario y ejecuta el menú. @param args argumentos de línea de comandos */
    public static void main(String[] args) {
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        InMemoryInventoryRepository inventoryRepository = new InMemoryInventoryRepository();
        InMemoryStockMovementRepository movementRepository = new InMemoryStockMovementRepository();
        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        CustomerRepository customerRepository = new CustomerRepository();
        SellerRepository sellerRepository = new SellerRepository();
        ProductService productService = new ProductService(productRepository);
        productService.registerAll(InitialCatalog.create());
        InventoryService inventoryService = new InventoryService(productService, inventoryRepository, movementRepository);
        CustomerService customerService = new CustomerService(customerRepository);
        SellerService sellerService = new SellerService(sellerRepository);
        SaleService saleService = new SaleService(saleRepository, productService, inventoryService, customerService, sellerService);
        UserRepository userRepository = new InMemoryUserRepository();
        AuthenticationService authenticationService = new AuthenticationService(userRepository);
        AuthorizationService authorizationService = new AuthorizationService();
        Scanner scanner = new Scanner(System.in);
        boolean demo = java.util.Arrays.asList(args).contains("--demo");
        if (demo) {
            String adminPassword = System.getenv("LAEXCLUSIVA_DEMO_ADMIN_PASSWORD");
            String workerPassword = System.getenv("LAEXCLUSIVA_DEMO_WORKER_PASSWORD");
            if (adminPassword == null || adminPassword.isBlank() || workerPassword == null || workerPassword.isBlank()) {
                System.err.println("Defina LAEXCLUSIVA_DEMO_ADMIN_PASSWORD y LAEXCLUSIVA_DEMO_WORKER_PASSWORD."); return;
            }
            DemoDataInitializer initializer = new DemoDataInitializer(productService, inventoryService, customerService, sellerService, saleService);
            DemoDataInitializer.DemoResult result = initializer.initialize();
            userRepository.save(new UserAccount(null, "admin.demo", PasswordHasher.hash(adminPassword), UserRole.ADMINISTRATOR, result.adminSellerId()));
            userRepository.save(new UserAccount(null, "trabajador.demo", PasswordHasher.hash(workerPassword), UserRole.WORKER, result.workerSellerId()));
        } else if (userRepository.findAll().isEmpty()) {
            System.out.print("Usuario administrador inicial: "); String username = scanner.nextLine().trim();
            System.out.print("Contraseña: "); String password = scanner.nextLine();
            if (username.isEmpty() || password.isEmpty()) { System.err.println("Usuario y contraseña son obligatorios."); return; }
            userRepository.save(new UserAccount(null, username, PasswordHasher.hash(password), UserRole.ADMINISTRATOR, null));
        }
        System.out.print("Usuario: "); String username = scanner.nextLine();
        System.out.print("Contraseña: "); String password = scanner.nextLine();
        UserSession session = authenticationService.createSession(username, password).orElse(null);
        if (session == null) { System.err.println("Credenciales incorrectas."); return; }
        if (!authorizationService.hasRole(session, session.getUserAccount().getRole())) return;
        ConsoleMenu menu = new ConsoleMenu(productService, inventoryService, customerService, sellerService,
                saleService, scanner, System.out, session, authorizationService);
        menu.start();
    }
}
