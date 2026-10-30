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
 * Punto de entrada y composición de dependencias de La Exclusiva.
 *
 * Crea los repositorios en memoria, carga el catálogo inicial y conecta los servicios con
 * {@link ConsoleMenu}. La construcción centralizada permite que la interfaz dependa de los
 * servicios sin crear repositorios ni reglas de negocio durante la interacción.
 */
public final class Main {

    private Main() {
        // Evita instanciar la clase de inicio.
    }

    /**
     * Construye las dependencias de la aplicación y ejecuta el menú de consola.
     *
     * El catálogo inicial se carga en cada ejecución y los repositorios utilizados en este
     * punto de entrada mantienen los datos solo en memoria.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        // Los repositorios concretos se instancian aquí; los servicios reciben sus contratos.
        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        InMemoryInventoryRepository inventoryRepository = new InMemoryInventoryRepository();
        InMemoryStockMovementRepository movementRepository = new InMemoryStockMovementRepository();
        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        CustomerRepository customerRepository = new CustomerRepository();
        SellerRepository sellerRepository = new SellerRepository();

        // Se carga el catálogo antes de iniciar los flujos que consultan productos e inventario.
        ProductService productService = new ProductService(productRepository);
        productService.registerAll(InitialCatalog.create());
        InventoryService inventoryService = new InventoryService(
                productService, inventoryRepository, movementRepository);
        CustomerService customerService = new CustomerService(customerRepository);
        SellerService sellerService = new SellerService(sellerRepository);
        SaleService saleService = new SaleService(saleRepository, productService,
                inventoryService, customerService, sellerService);

        // La entrada y salida estándar pertenecen al proceso; ConsoleMenu no las cierra.
        ConsoleMenu menu = new ConsoleMenu(productService, inventoryService, customerService,
                sellerService, saleService, new Scanner(System.in), System.out);
        menu.start();
    }
}
