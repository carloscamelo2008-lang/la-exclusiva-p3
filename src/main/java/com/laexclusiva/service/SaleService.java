package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordina los casos de uso de ventas y sus abonos.
 *
 * Obtiene productos, clientes y vendedores mediante los servicios correspondientes, registra
 * las ventas en {@link SaleRepository} y coordina con {@link InventoryService} y
 * {@link CustomerService} los cambios de existencias y saldo asociados.
 */
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final SellerService sellerService;

    /**
     * Crea el servicio con las dependencias necesarias para validar y registrar una venta.
     *
     * @param saleRepository repositorio de ventas
     * @param productService servicio que consulta el catálogo y los precios
     * @param inventoryService servicio que valida y actualiza las existencias
     * @param customerService servicio que mantiene los saldos de clientes
     * @param sellerService servicio que consulta los vendedores
     * @throws IllegalArgumentException si alguna dependencia es nula
     */
    public SaleService(SaleRepository saleRepository, ProductService productService,
                       InventoryService inventoryService, CustomerService customerService,
                       SellerService sellerService) {
        if (saleRepository == null || productService == null || inventoryService == null
                || customerService == null || sellerService == null) {
            throw new IllegalArgumentException("Las dependencias del servicio de ventas son obligatorias.");
        }
        this.saleRepository = saleRepository;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.sellerService = sellerService;
    }

    /**
     * Valida y registra una venta con sus líneas y pago inicial.
     *
     * Antes de modificar datos comprueba los productos, el vendedor, el cliente requerido, el
     * importe y las existencias. Si queda saldo pendiente, lo asigna al cliente. Después registra
     * la salida de inventario y la venta. Ante un fallo de escritura intenta compensar los cambios
     * ya aplicados; este mecanismo corresponde a los repositorios actuales en memoria y no ofrece
     * una transacción durable.
     *
     * @param lines productos, presentaciones y cantidades de la venta; debe haber al menos una línea
     * @param sellerId identificador de un vendedor registrado
     * @param customerId identificador del cliente; es obligatorio si queda saldo pendiente y puede ser
     *                   nulo si el pago inicial cubre el total
     * @param initialPayment importe recibido al registrar la venta, entre cero y el total inclusive
     * @return copia de la venta registrada, con su identificador generado y pagos iniciales
     * @throws IllegalArgumentException si las líneas, identificadores, entidades o el pago son inválidos,
     *                                  o si falta un producto, vendedor o cliente requerido
     * @throws IllegalStateException si el stock no alcanza, hay una inconsistencia de saldos
     *                               o se detecta un identificador de venta duplicado
     * @throws com.laexclusiva.model.InsufficientStockException si las existencias cambian entre la
     *                                                          comprobación previa y el descuento
     */
    public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                            BigDecimal initialPayment) {
        List<StockRequest> requests = validateAndCopyRequests(lines);
        requireIdentifier(sellerId, "El identificador del vendedor es obligatorio.");

        Seller seller = sellerService.getSellerById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el vendedor con ID " + sellerId + "."));

        Customer customer = null;
        if (customerId != null) {
            requireIdentifier(customerId, "El identificador del cliente no puede estar vacío.");
            customer = customerService.getCustomerById(customerId);
            assertSalesDebtConsistency(customerId);
        }

        List<SaleLine> saleLines = new ArrayList<>(requests.size());
        for (StockRequest request : requests) {
            // Cada línea captura el precio vigente para conservarlo como parte del historial.
            saleLines.add(new SaleLine(productService.findById(request.productId()),
                    request.presentation(), request.quantity()));
        }

        Sale sale = new Sale(LocalDateTime.now(), seller, customer, saleLines, initialPayment);
        if (saleRepository.findById(sale.getId()).isPresent()) {
            throw new IllegalStateException("Ya existe una venta con el ID " + sale.getId() + ".");
        }
        // Esta comprobación ocurre antes de escribir; discountSale vuelve a validar al descontar.
        if (!inventoryService.canFulfill(requests)) {
            throw new IllegalStateException(
                    "No hay existencias suficientes para completar todas las líneas de la venta.");
        }

        BigDecimal pendingBalance = sale.calculateBalance();
        CustomerService.SaleDebtSnapshot customerSnapshot = pendingBalance.signum() > 0
                ? customerService.snapshotSaleDebtState(customer.getId()) : null;
        InventoryService.DiscountReceipt discount = null;
        boolean saleSaveAttempted = false;
        boolean customerDebtUpdateAttempted = false;
        try {
            // La secuencia de escrituras no es una transacción: se guardan snapshots para compensar.
            discount = inventoryService.discountSale(requests, "Venta " + sale.getId());
            if (pendingBalance.signum() > 0) {
                customerDebtUpdateAttempted = true;
                customerService.addSaleDebtToCustomer(customer.getId(), pendingBalance);
            }
            saleSaveAttempted = true;
            saleRepository.save(sale);
            return saleRepository.findById(sale.getId())
                    .orElseThrow(() -> new IllegalStateException("No se pudo recuperar la venta guardada."));
        } catch (RuntimeException | Error failure) {
            List<Runnable> compensations = new ArrayList<>();
            if (saleSaveAttempted) {
                compensations.add(() -> saleRepository.deleteById(sale.getId()));
            }
            if (customerDebtUpdateAttempted && customerSnapshot != null) {
                compensations.add(() -> customerService.restoreSaleDebtState(customerSnapshot));
            }
            if (discount != null) {
                InventoryService.DiscountReceipt receipt = discount;
                compensations.add(() -> inventoryService.rollbackDiscount(receipt));
            }
            compensate(failure, compensations);
            throw failure;
        }
    }

    /**
     * Registra un abono en la venta y reduce por el mismo importe la deuda de ventas del cliente.
     * El abono debe ser positivo, no superar el saldo de esta venta y corresponder a una venta
     * asociada a un cliente. Si una escritura falla, intenta restaurar los dos estados previos.
     *
     * @param saleId identificador de la venta pendiente
     * @param amount importe del abono
     * @return copia actualizada de la venta
     * @throws IllegalArgumentException si el identificador o importe es inválido, la venta no existe,
     *                                  no tiene cliente asociado o el abono supera el saldo disponible
     * @throws IllegalStateException si el saldo agregado del cliente no coincide con sus ventas
     */
    public Sale registerPayment(String saleId, BigDecimal amount) {
        requireIdentifier(saleId, "El identificador de la venta es obligatorio.");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El importe del abono debe ser mayor que cero.");
        }

        Sale before = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la venta con ID " + saleId + "."));
        if (amount.compareTo(before.calculateBalance()) > 0) {
            throw new IllegalArgumentException("El abono no puede superar el saldo pendiente de la venta.");
        }
        Customer saleCustomer = before.getCustomer();
        if (saleCustomer == null) {
            throw new IllegalArgumentException("La venta no tiene un cliente asociado para registrar un abono.");
        }
        String customerId = saleCustomer.getId();
        assertSalesDebtConsistency(customerId);
        Customer customer = customerService.getCustomerById(customerId);
        if (customer.getBalance().compareTo(amount) < 0
                || customerService.saleDebtBalance(customerId).compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "El abono supera la deuda pendiente asociada a ventas del cliente.");
        }

        CustomerService.SaleDebtSnapshot customerSnapshot =
                customerService.snapshotSaleDebtState(customerId);
        Sale updated = before.copy();
        updated.registerPayment(new SalePayment(amount, LocalDateTime.now()));
        boolean saleUpdateAttempted = false;
        boolean customerPaymentAttempted = false;
        try {
            // Se actualiza el saldo del cliente y luego el registro de la venta; ambos se compensan si falla uno.
            customerPaymentAttempted = true;
            customerService.applyPaymentToSaleDebt(customerId, amount);
            saleUpdateAttempted = true;
            saleRepository.update(updated);
            return saleRepository.findById(saleId)
                    .orElseThrow(() -> new IllegalStateException("No se pudo recuperar la venta actualizada."));
        } catch (RuntimeException | Error failure) {
            List<Runnable> compensations = new ArrayList<>();
            if (saleUpdateAttempted) {
                compensations.add(() -> saleRepository.update(before));
            }
            if (customerPaymentAttempted) {
                compensations.add(() -> customerService.restoreSaleDebtState(customerSnapshot));
            }
            compensate(failure, compensations);
            throw failure;
        }
    }

    /**
     * Calcula el total de una venta candidata con los precios actuales del catálogo, sin registrar
     * la venta ni modificar las existencias o los saldos.
     *
     * @param lines líneas propuestas para la venta
     * @return suma de subtotales con el precio configurado para cada presentación
     * @throws IllegalArgumentException si la lista está vacía, contiene una línea nula o un producto
     *                                  no existe
     * @throws IllegalStateException si falta el precio de una presentación para algún producto
     */
    public BigDecimal previewTotal(List<StockRequest> lines) {
        List<StockRequest> requests = validateAndCopyRequests(lines);
        List<SaleLine> saleLines = new ArrayList<>(requests.size());
        for (StockRequest request : requests) {
            saleLines.add(new SaleLine(productService.findById(request.productId()),
                    request.presentation(), request.quantity()));
        }
        return Sale.calculateTotal(saleLines);
    }

    /**
     * Busca una venta registrada por su identificador.
     *
     * @param saleId identificador de la venta
     * @return copia de la venta encontrada
     * @throws IllegalArgumentException si el identificador es nulo, vacío o no existe
     */
    public Sale findById(String saleId) {
        requireIdentifier(saleId, "El identificador de la venta es obligatorio.");
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la venta con ID " + saleId + "."));
    }

    /**
     * Lista copias independientes de las ventas registradas.
     *
     * @return ventas en el orden proporcionado por el repositorio
     */
    public List<Sale> listAll() {
        return saleRepository.findAll();
    }

    /**
     * Comprueba que la suma de saldos de las ventas coincida con la asignación de deuda de ventas
     * del cliente y que esa asignación no supere su saldo agregado.
     *
     * @param customerId identificador del cliente que se valida
     */
    private void assertSalesDebtConsistency(String customerId) {
        BigDecimal salesBalance = saleRepository.findAll().stream()
                .filter(sale -> sale.getCustomer() != null
                        && customerId.equals(sale.getCustomer().getId()))
                .map(Sale::calculateBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocatedDebt = customerService.saleDebtBalance(customerId);
        if (salesBalance.compareTo(allocatedDebt) != 0) {
            throw new IllegalStateException(
                    "El saldo de ventas del cliente no coincide con el saldo agregado asignado a ventas.");
        }
        if (customerService.getCustomerById(customerId).getBalance().compareTo(allocatedDebt) < 0) {
            throw new IllegalStateException("La deuda de ventas registrada supera el saldo del cliente.");
        }
    }

    private static void compensate(Throwable failure, List<Runnable> compensations) {
        // Se intentan todas las restauraciones; los fallos secundarios quedan asociados al original.
        for (Runnable compensation : compensations) {
            try {
                compensation.run();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static List<StockRequest> validateAndCopyRequests(List<StockRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos una línea.");
        }
        List<StockRequest> copy = new ArrayList<>(lines.size());
        for (StockRequest line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("La venta no puede contener líneas nulas.");
            }
            copy.add(line);
        }
        return List.copyOf(copy);
    }

    private static void requireIdentifier(String id, String message) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
