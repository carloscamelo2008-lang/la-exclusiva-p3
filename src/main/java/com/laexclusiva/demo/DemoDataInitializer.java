package com.laexclusiva.demo;

import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SaleService;
import com.laexclusiva.service.SellerService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga un estado de demostración conocido y repetible: existencias ficticias, un cliente,
 * dos perfiles de vendedor y una venta parcialmente pagada.
 *
 * Reglas de diseño:
 * - No registra el catálogo: lo carga InitialCatalog una sola vez en el arranque.
 * - Todo pasa por los servicios existentes (addStock, registerCustomer, registerSeller,
 *   registerSale). Nunca se guarda una venta ni se toca un saldo directamente en un repositorio.
 * - No usa referencias globales: recibe sus dependencias por constructor (DIP).
 * - Solo datos ficticios: sin teléfonos, direcciones ni contraseñas reales.
 * - Los datos viven en memoria; se pierden al cerrar la aplicación.
 */
public class DemoDataInitializer {

    /** Identificador estable del cliente ficticio. */
    public static final String DEMO_CUSTOMER_ID = "CLI-DEMO-001";

    private static final String DEMO_PHONE = "0000000000";
    private static final String DEMO_ADDRESS = "Dirección de demostración (ficticia)";
    private static final String STOCK_REASON = "Carga de demostración: existencias iniciales";
    private static final BigDecimal INITIAL_PAYMENT = BigDecimal.valueOf(100_000L);

    /** Existencias iniciales ficticias: id de producto -> canastas completas (0 sueltas). */
    private static final Map<String, Integer> DEMO_STOCK = new LinkedHashMap<>();

    static {
        DEMO_STOCK.put("COSTENITA_175", 2);        // 2 x 38 = 76 unidades
        DEMO_STOCK.put("POKER_250", 2);            // 2 x 38 = 76 unidades
        DEMO_STOCK.put("AGUILITA_250", 2);         // 2 x 38 = 76 unidades
        DEMO_STOCK.put("COSTENA_BACANA_330", 2);   // 2 x 30 = 60 unidades
        DEMO_STOCK.put("COLA_Y_POLA_LATA_330", 2); // 2 x 30 = 60 unidades
        DEMO_STOCK.put("POKER_750", 1);            // 1 x 16 = 16 unidades
    }

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final SellerService sellerService;
    private final SaleService saleService;
    private boolean initialized;

    /** Identificadores que necesitan los demás módulos para enlazar cuentas y sesiones. */
    public record DemoResult(String adminSellerId, String workerSellerId,
                             String customerId, String saleId) { }

    public DemoDataInitializer(ProductService productService, InventoryService inventoryService,
                               CustomerService customerService, SellerService sellerService,
                               SaleService saleService) {
        // Bloque 1: las cinco dependencias son obligatorias.
        if (productService == null || inventoryService == null || customerService == null
                || sellerService == null || saleService == null) {
            throw new IllegalArgumentException(
                    "Las dependencias del inicializador de demostración son obligatorias.");
        }
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.sellerService = sellerService;
        this.saleService = saleService;
    }

    /**
     * Las dos líneas de la venta demo: una canasta completa de Poker 250 ml ($85.000) y
     * una media canasta de Poker 750 ml ($45.000). Total: $130.000.
     */
    public static List<StockRequest> demoSaleLines() {
        return List.of(
                new StockRequest("POKER_250", SalePresentation.FULL_BASKET, 1),
                new StockRequest("POKER_750", SalePresentation.HALF_BASKET, 1));
    }

    /**
     * Ejecuta la carga completa. Solo puede ejecutarse una vez por instancia y falla con un
     * mensaje claro si los datos de demostración ya existen. Si falla a mitad de camino, los
     * datos están en memoria: lo más seguro es reiniciar la aplicación.
     */
    public DemoResult initialize() {
        // Bloque 1: una sola ejecución por instancia.
        if (initialized) {
            throw new IllegalStateException(
                    "La carga de datos de demostración ya se ejecutó en esta instancia.");
        }

        // Bloque 2: validar todas las precondiciones ANTES de modificar nada.
        requireStockIsEmpty();
        if (customerExists(DEMO_CUSTOMER_ID)) {
            throw new IllegalStateException("Ya existe el cliente de demostración "
                    + DEMO_CUSTOMER_ID + ". No se duplican los datos de demostración.");
        }

        // Bloque 3: existencias iniciales por addStock (deja un movimiento por referencia).
        DEMO_STOCK.forEach((productId, baskets) ->
                inventoryService.addStock(productId, baskets, 0, STOCK_REASON));

        // Bloque 4: cliente y perfiles de vendedor ficticios, por sus servicios.
        customerService.registerCustomer(DEMO_CUSTOMER_ID, "Cliente de demostración",
                DEMO_PHONE, DEMO_ADDRESS);
        Seller admin = sellerService.registerSeller("Administrador de demostración",
                DEMO_PHONE, DEMO_ADDRESS);
        Seller worker = sellerService.registerSeller("Trabajador de demostración",
                DEMO_PHONE, DEMO_ADDRESS);

        // Bloque 5: venta demo a nombre del trabajador, con pago inicial de $100.000.
        // SaleService descuenta el stock y deja el saldo de $30.000 en la venta y el cliente.
        Sale sale = saleService.registerSale(demoSaleLines(), worker.getId(),
                DEMO_CUSTOMER_ID, INITIAL_PAYMENT);

        // Bloque 6: recién aquí se marca como ejecutada y se devuelven los identificadores.
        initialized = true;
        return new DemoResult(admin.getId(), worker.getId(), DEMO_CUSTOMER_ID, sale.getId());
    }

    /** Rechaza la carga si alguna referencia demo ya tiene existencias (evita duplicarlas). */
    private void requireStockIsEmpty() {
        for (String productId : DEMO_STOCK.keySet()) {
            // availableUnits también falla si el producto no existe en el catálogo.
            if (inventoryService.availableUnits(productId) > 0) {
                throw new IllegalStateException("La referencia " + productId
                        + " ya tiene existencias. No se duplican los datos de demostración.");
            }
        }
    }

    private boolean customerExists(String customerId) {
        try {
            customerService.getCustomerById(customerId);
            return true;
        } catch (IllegalArgumentException notFound) {
            return false;
        }
    }
}
