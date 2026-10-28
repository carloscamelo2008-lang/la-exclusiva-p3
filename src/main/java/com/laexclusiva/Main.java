package com.laexclusiva;

import com.laexclusiva.model.InitialCatalog;
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
import com.laexclusiva.ui.ConsoleMenu;
import java.util.Scanner;

/**
 * Punto de entrada de la aplicación La Exclusiva.
 */
public final class Main {

    private Main() {
        // Evita instanciar la clase de inicio.
    }

    /**
     * Inicia el menú principal de la aplicación.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        InMemoryInventoryRepository inventoryRepository = new InMemoryInventoryRepository();
        InMemoryStockMovementRepository movementRepository = new InMemoryStockMovementRepository();
        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        CustomerRepository customerRepository = new CustomerRepository();
        SellerRepository sellerRepository = new SellerRepository();

        ProductService productService = new ProductService(productRepository);
        productService.registerAll(InitialCatalog.create());
        InventoryService inventoryService = new InventoryService(
                productService, inventoryRepository, movementRepository);
        CustomerService customerService = new CustomerService(customerRepository);
        SellerService sellerService = new SellerService(sellerRepository);
        SaleService saleService = new SaleService(saleRepository, productService,
                inventoryService, customerService, sellerService);

        ConsoleMenu menu = new ConsoleMenu(productService, inventoryService, customerService,
                sellerService, saleService, new Scanner(System.in), System.out);
        menu.start();
    }
}
