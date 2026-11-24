package com.laexclusiva.service;

import com.laexclusiva.model.Sale;
import com.laexclusiva.model.StockRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adaptador del flujo existente en memoria para las pantallas de venta y cartera. */
public final class InMemorySalesWorkflow implements SalesWorkflow {
    private final SaleService saleService;
    private final CustomerService customerService;
    private final Map<PaymentKey, BigDecimal> paymentsByKey = new HashMap<>();
    private final Map<String, SaleRequestRecord> salesByKey = new HashMap<>();

    /** Crea el adaptador con los servicios en memoria ya existentes. */
    public InMemorySalesWorkflow(SaleService saleService, CustomerService customerService) {
        if (saleService == null || customerService == null) {
            throw new IllegalArgumentException("Los servicios en memoria son obligatorios.");
        }
        this.saleService = saleService;
        this.customerService = customerService;
    }

    @Override
    public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                            BigDecimal initialPayment) {
        return saleService.registerSale(lines, sellerId, customerId, initialPayment);
    }

    /**
     * Deduplica reintentos de creación durante la vida del proceso en modo memoria.
     *
     * @param lines líneas solicitadas
     * @param sellerId vendedor
     * @param customerId cliente o {@code null}
     * @param initialPayment pago inicial
     * @param idempotencyKey clave estable de la pantalla
     * @return venta creada o recuperada de esta sesión del proceso
     */
    @Override
    public synchronized Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                          BigDecimal initialPayment, String idempotencyKey) {
        saleService.authorizeSeller(sellerId);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("La clave de la venta debe tener entre 1 y 64 caracteres.");
        }
        String fingerprint = SaleRequestFingerprint.calculate(lines, sellerId, customerId, initialPayment);
        SaleRequestRecord existing = salesByKey.get(idempotencyKey);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("La clave de venta ya se usó con datos diferentes.");
            }
            return saleService.findById(existing.saleId());
        }
        Sale created = saleService.registerSale(lines, sellerId, customerId, initialPayment);
        salesByKey.put(idempotencyKey, new SaleRequestRecord(fingerprint, created.getId()));
        return created;
    }

    /**
     * Deduplica reintentos durante la vida del proceso; la clave no es durable en modo demo.
     */
    @Override
    public synchronized Sale registerPayment(String saleId, BigDecimal amount, String idempotencyKey) {
        requireKey(saleId, idempotencyKey);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El importe del abono debe ser mayor que cero.");
        }
        PaymentKey key = new PaymentKey(saleId, idempotencyKey);
        BigDecimal existing = paymentsByKey.get(key);
        if (existing != null) {
            if (existing.compareTo(amount) != 0) {
                throw new IllegalArgumentException("La clave de idempotencia ya se usó con otro importe.");
            }
            return saleService.findById(saleId);
        }
        Sale updated = saleService.registerPayment(saleId, amount);
        paymentsByKey.put(key, amount);
        return updated;
    }

    @Override
    public Sale findById(String saleId) {
        return saleService.findById(saleId);
    }

    @Override
    public List<Sale> listAll() {
        return saleService.listAll();
    }

    @Override
    public BigDecimal outstandingSalesDebt(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("El ID del cliente es obligatorio.");
        }
        return saleService.listAll().stream()
                .filter(sale -> sale.getCustomer() != null
                        && customerId.equals(sale.getCustomer().getId()))
                .map(Sale::calculateBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean supportsDurableIdempotency() {
        return false;
    }

    private static void requireKey(String saleId, String idempotencyKey) {
        if (saleId == null || saleId.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("La venta y la clave de idempotencia son obligatorias.");
        }
    }

    private record PaymentKey(String saleId, String idempotencyKey) { }
    private record SaleRequestRecord(String fingerprint, String saleId) { }

}
