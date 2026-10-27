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

/** Coordina el registro de ventas y los pagos asociados a una venta existente. */
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final SellerService sellerService;

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
     * Registra una venta. Primero valida las líneas, las entidades, el pago y el stock; después
     * aplica la salida, deuda y venta con compensaciones para el almacenamiento actual en memoria.
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
            saleLines.add(new SaleLine(productService.findById(request.productId()),
                    request.presentation(), request.quantity()));
        }

        Sale sale = new Sale(LocalDateTime.now(), seller, customer, saleLines, initialPayment);
        if (saleRepository.findById(sale.getId()).isPresent()) {
            throw new IllegalStateException("Ya existe una venta con el ID " + sale.getId() + ".");
        }
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

    /** Registra el mismo importe en la venta y en la deuda asociada a ventas del cliente. */
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

    /** Busca una venta por ID o falla con un mensaje de dominio. */
    public Sale findById(String saleId) {
        requireIdentifier(saleId, "El identificador de la venta es obligatorio.");
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la venta con ID " + saleId + "."));
    }

    /** Lista snapshots independientes de las ventas registradas. */
    public List<Sale> listAll() {
        return saleRepository.findAll();
    }

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
