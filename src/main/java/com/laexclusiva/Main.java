package com.laexclusiva;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.*;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.security.PasswordPolicy;
import com.laexclusiva.service.*;
import com.laexclusiva.ui.ConsoleMenu;
import com.laexclusiva.ui.IdentityServices;
import com.laexclusiva.ui.ApplicationServices;
import java.util.Scanner;

/** Punto de entrada y composición de dependencias de La Exclusiva. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        // 1. Validar el modo consola al puro inicio, antes de cualquier repositorio o servicio
        boolean consoleMode = java.util.Arrays.asList(args).contains("--console");
        
        if (!consoleMode) {
            // Si no se pasa --console, lanza inmediatamente JavaFX y sale del main
            com.laexclusiva.ui.App.launch(com.laexclusiva.ui.App.class, args);
            return;
        }

        String persistenceMode = System.getenv().get(ApplicationServices.MODE_ENV);
        if (!ApplicationServices.MEMORY_MODE.equalsIgnoreCase(persistenceMode)) {
            System.err.println("El modo --console solo admite LAEXCLUSIVA_PERSISTENCE_MODE=memory explícito; "
                    + "no se seleccionará almacenamiento volátil automáticamente.");
            return;
        }

        // 2. Flujo de consola clásico (solo se ejecuta si se pasa --console)
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        InMemoryInventoryRepository inventoryRepository = new InMemoryInventoryRepository();
        InMemoryStockMovementRepository movementRepository = new InMemoryStockMovementRepository();
        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        CustomerRepository customerRepository = new CustomerRepository();
        SellerRepository sellerRepository = new SellerRepository();
        
        ProductService productService = new ProductService(productRepository);
        
        InventoryService inventoryService = new InventoryService(productService, inventoryRepository, movementRepository);
        CustomerService customerService = new CustomerService(customerRepository);
        SellerService sellerService = new SellerService(sellerRepository);
        SaleService saleService = new SaleService(saleRepository, productService, inventoryService, customerService, sellerService);
        
        IdentityServices identity = IdentityServices.inMemory(sellerService);
        UserRepository userRepository = identity.users();
        AuthenticationService authenticationService = identity.authentication();
        AuthorizationService authorizationService = identity.authorization();
        Scanner scanner = new Scanner(System.in);
        
        if (userRepository.findAll().isEmpty()) {
            System.out.print("Usuario administrador inicial: "); 
            String username = scanner.nextLine().trim();
            System.out.print("Contraseña: "); 
            String password = scanner.nextLine();
            if (username.isEmpty()) {
                System.err.println("El usuario es obligatorio.");
                return; 
            }
            try {
                PasswordPolicy.validate(password);
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                System.err.println("No se pudo validar la contraseña inicial. Revise la política y su configuración.");
                return;
            }
            userRepository.save(new UserAccount(null, username, PasswordHasher.hash(password), UserRole.ADMINISTRATOR, null));
        }

        System.out.print("Usuario: "); 
        String username = scanner.nextLine();
        System.out.print("Contraseña: "); 
        String password = scanner.nextLine();
        UserSession session = authenticationService.createSession(username, password).orElse(null);
        if (session == null) { 
            System.err.println("Credenciales incorrectas."); 
            return; 
        }
        if (!authorizationService.hasPermission(session, Permission.VIEW_CATALOG)) return;
        ConsoleMenu menu = new ConsoleMenu(productService, inventoryService, customerService, sellerService,
                saleService.requireAuthenticatedSales(), scanner, System.out, session, authorizationService);
        menu.start();
    }
}
