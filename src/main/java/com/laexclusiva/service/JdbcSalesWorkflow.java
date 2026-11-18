package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.CustomerBalanceAdjuster;
import com.laexclusiva.repository.CustomerDebtQuery;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.repository.IdempotentSalePaymentAppender;
import com.laexclusiva.repository.InventoryStockAdjuster;
import com.laexclusiva.repository.ProductRepository;
import com.laexclusiva.repository.SaleRepository;
import com.laexclusiva.repository.SellerRepositoryInterface;
import com.laexclusiva.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Coordina ventas y abonos JDBC dentro de una sola transacción compartida. */
public final class JdbcSalesWorkflow implements SalesWorkflow {
    private final ProductRepository products;
    private final CustomerRepositoryInterface customers;
    private final SellerRepositoryInterface sellers;
    private final SaleRepository sales;
    private final IdempotentSalePaymentAppender payments;
    private final CustomerDebtQuery debtQuery;
    private final CustomerBalanceAdjuster customerBalances;
    private final InventoryStockAdjuster inventory;
    private final StockMovementRepository movements;
    private final TransactionBoundary transactions;

    /**
     * Crea el caso de uso con adaptadores que comparten proveedor y coordinador JDBC.
     */
    public JdbcSalesWorkflow(ProductRepository products, CustomerRepositoryInterface customers,
                             SellerRepositoryInterface sellers, SaleRepository sales,
                             IdempotentSalePaymentAppender payments, CustomerDebtQuery debtQuery,
                             CustomerBalanceAdjuster customerBalances, InventoryStockAdjuster inventory,
                             StockMovementRepository movements, TransactionBoundary transactions) {
        if (products == null || customers == null || sellers == null || sales == null || payments == null
                || debtQuery == null || customerBalances == null || inventory == null || movements == null
                || transactions == null) {
            throw new IllegalArgumentException("Todas las dependencias del flujo JDBC son obligatorias.");
        }
        this.products = products;
        this.customers = customers;
        this.sellers = sellers;
        this.sales = sales;
        this.payments = payments;
        this.debtQuery = debtQuery;
        this.customerBalances = customerBalances;
        this.inventory = inventory;
        this.movements = movements;
        this.transactions = transactions;
    }

    /**
     * Registra venta, salidas de inventario, movimientos, pago inicial y saldo del cliente
     * atómicamente. Primero bloquea la fila del cliente y después ajusta las filas de inventario
     * por ID ascendente; las demás operaciones financieras del cliente adquieren el mismo primer
     * bloqueo para evitar leer saldos de versiones distintas.
     */
    @Override
    public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                             BigDecimal initialPayment) {
        List<StockRequest> requests = validateRequests(lines);
        requireId(sellerId, "El vendedor es obligatorio.");
        if (customerId != null && customerId.isBlank()) {
            throw new IllegalArgumentException("El ID del cliente no puede estar vacío.");
        }
        return transactions.inTransaction(() -> registerSaleInTransaction(
                requests, sellerId, customerId, initialPayment));
    }

    private Sale registerSaleInTransaction(List<StockRequest> requests, String sellerId,
                                           String customerId, BigDecimal initialPayment) {
        Customer customer = customerId == null ? null : customers.findByIdForUpdate(customerId);
        if (customerId != null && customer == null) {
            throw new IllegalArgumentException("No existe el cliente " + customerId + ".");
        }

        Seller seller = sellers.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el vendedor " + sellerId + "."));

        Map<String, com.laexclusiva.model.BeerProduct> productById = new LinkedHashMap<>();
        List<SaleLine> saleLines = new ArrayList<>(requests.size());
        for (StockRequest request : requests) {
            com.laexclusiva.model.BeerProduct product = products.findById(request.productId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el producto " + request.productId() + "."));
            productById.put(product.getId(), product);
            saleLines.add(new SaleLine(product, request.presentation(), request.quantity()));
        }

        Sale sale = new Sale(LocalDateTime.now(), seller, customer, saleLines, initialPayment);
        BigDecimal outstanding = sale.calculateBalance();
        if (customer != null) {
            requireConsistentCustomerBalance(customer);
        }
        if (outstanding.signum() > 0 && customer == null) {
            throw new IllegalArgumentException("Se requiere cliente para una venta con saldo pendiente.");
        }

        Map<String, Integer> unitsByProduct = requiredUnits(requests, productById);
        for (Map.Entry<String, Integer> entry : unitsByProduct.entrySet()) {
            inventory.decreaseStock(entry.getKey(), entry.getValue());
            movements.save(new StockMovement(entry.getKey(), MovementType.EXIT, entry.getValue(),
                    "Venta " + sale.getId(), LocalDateTime.now()));
        }
        if (outstanding.signum() > 0) {
            customerBalances.increaseBalance(customerId, outstanding);
        }
        sales.save(sale);
        return sales.findById(sale.getId())
                .orElseThrow(() -> new IllegalStateException("PostgreSQL no devolvió la venta recién guardada."));
    }

    /**
     * Inserta un abono identificado y reduce el saldo agregado solo si PostgreSQL confirma que
     * la clave es nueva. La transacción bloquea primero la fila del cliente y después la venta,
     * el mismo orden que aplica una venta a crédito; así las lecturas de saldo y cartera pertenecen
     * a una única secuencia financiera hasta el commit.
     */
    @Override
    public Sale registerPayment(String saleId, BigDecimal amount, String idempotencyKey) {
        requireId(saleId, "La venta es obligatoria.");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El importe del abono debe ser mayor que cero.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("La clave de idempotencia debe tener entre 1 y 64 caracteres.");
        }
        return transactions.inTransaction(() -> {
            String customerId = sales.findCustomerIdBySaleId(saleId);
            if (customerId == null) {
                throw new IllegalArgumentException("La venta no tiene un cliente asociado.");
            }
            Customer persistedCustomer = customers.findByIdForUpdate(customerId);
            if (persistedCustomer == null) {
                throw new IllegalStateException("No existe el cliente asociado a la venta.");
            }
            IdempotentSalePaymentAppender.AppendResult result =
                    payments.appendPaymentOnce(saleId, amount, idempotencyKey);
            if (result.inserted()) {
                BigDecimal salesDebtAfterPayment = debtQuery.findOutstandingSalesDebt(customerId);
                if (salesDebtAfterPayment.add(result.payment().getAmount())
                        .compareTo(persistedCustomer.getBalance()) > 0) {
                    throw new IllegalStateException(
                            "La cartera de ventas supera el saldo agregado del cliente; revise la inconsistencia.");
                }
                customerBalances.decreaseBalance(customerId, result.payment().getAmount());
            }
            return findById(saleId);
        });
    }

    @Override
    public Sale findById(String saleId) {
        requireId(saleId, "La venta es obligatoria.");
        return sales.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la venta " + saleId + "."));
    }

    @Override
    public List<Sale> listAll() {
        return sales.findAll();
    }

    @Override
    public BigDecimal outstandingSalesDebt(String customerId) {
        requireId(customerId, "El cliente es obligatorio.");
        return debtQuery.findOutstandingSalesDebt(customerId);
    }

    @Override
    public boolean supportsDurableIdempotency() {
        return true;
    }

    private void requireConsistentCustomerBalance(Customer customer) {
        BigDecimal salesDebt = debtQuery.findOutstandingSalesDebt(customer.getId());
        if (salesDebt.compareTo(customer.getBalance()) > 0) {
            throw new IllegalStateException(
                    "La cartera de ventas supera el saldo agregado del cliente; revise la inconsistencia.");
        }
    }

    private static Map<String, Integer> requiredUnits(List<StockRequest> requests,
                                                       Map<String, com.laexclusiva.model.BeerProduct> products) {
        Map<String, Integer> totals = new TreeMap<>(Comparator.naturalOrder());
        for (StockRequest request : requests) {
            int perPresentation = products.get(request.productId()).unitsFor(request.presentation());
            try {
                int units = Math.multiplyExact(perPresentation, request.quantity());
                totals.merge(request.productId(), units, Math::addExact);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("La cantidad supera el máximo de unidades permitido.", overflow);
            }
        }
        return totals;
    }

    private static List<StockRequest> validateRequests(List<StockRequest> lines) {
        if (lines == null || lines.isEmpty() || lines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("La venta debe contener líneas válidas.");
        }
        return List.copyOf(lines);
    }

    private static void requireId(String id, String message) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
