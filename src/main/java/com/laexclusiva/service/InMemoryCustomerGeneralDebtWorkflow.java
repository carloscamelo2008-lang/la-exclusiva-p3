package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Implementación demostrativa de deuda general cuya bitácora dura mientras se ejecuta la aplicación. */
public final class InMemoryCustomerGeneralDebtWorkflow implements CustomerGeneralDebtWorkflow {
    private final CustomerService customers;
    private final SalesWorkflow sales;
    private final Map<String, StoredRequest> requests = new HashMap<>();
    private final List<GeneralDebtMovement> movements = new ArrayList<>();
    private long nextId = 1;

    /** Compone la lógica de deuda general existente con la consulta de cartera en memoria. */
    public InMemoryCustomerGeneralDebtWorkflow(CustomerService customers, SalesWorkflow sales) {
        if (customers == null || sales == null) throw new IllegalArgumentException("Los servicios son obligatorios.");
        this.customers = customers;
        this.sales = sales;
    }

    @Override
    public synchronized CustomerDebtBreakdown findBalance(String customerId) {
        Customer customer = customers.getCustomerById(requireCustomerId(customerId));
        BigDecimal salesBalance = sales.outstandingSalesDebt(customerId);
        return new CustomerDebtBreakdown(customer.getBalance(), salesBalance,
                customer.getBalance().subtract(salesBalance));
    }

    @Override
    public GeneralDebtMovement registerDebt(String customerId, BigDecimal amount, String category,
                                            String description, String key) {
        return register(GeneralDebtMovementType.DEBT_INCREASE, customerId, amount, category, description, key);
    }

    @Override
    public GeneralDebtMovement registerPayment(String customerId, BigDecimal amount, String category,
                                               String description, String key) {
        return register(GeneralDebtMovementType.PAYMENT, customerId, amount, category, description, key);
    }

    private synchronized GeneralDebtMovement register(GeneralDebtMovementType type, String customerId,
                                                       BigDecimal amount, String category,
                                                       String description, String key) {
        String id = requireCustomerId(customerId);
        if (amount == null || amount.signum() <= 0 || category == null || category.isBlank()
                || description == null || description.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Importe, categoría, descripción y clave son obligatorios.");
        }
        String fingerprint = FinancialRequestFingerprint.calculate(type.name(), id,
                amount.stripTrailingZeros().toPlainString(), category.trim(), description.trim());
        StoredRequest old = requests.get(key);
        if (old != null) {
            if (!old.fingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("La clave financiera ya se usó con datos diferentes.");
            }
            return old.movement();
        }
        CustomerDebtBreakdown current = findBalance(id);
        if (!current.isConsistent()) {
            throw new IllegalStateException("La cartera de ventas supera el saldo agregado del cliente.");
        }
        if (type == GeneralDebtMovementType.PAYMENT && amount.compareTo(current.generalDebt()) > 0) {
            throw new IllegalArgumentException("El abono supera la deuda general disponible.");
        }
        if (type == GeneralDebtMovementType.DEBT_INCREASE) customers.addDebtToCustomer(id, amount);
        else customers.applyPaymentToCustomer(id, amount);
        GeneralDebtMovement movement = new GeneralDebtMovement(nextId++, id, type, amount,
                LocalDateTime.now(), category.trim(), description.trim(), key,
                type == GeneralDebtMovementType.PAYMENT ? AuthorizationService.operationUserId() : null);
        movements.add(movement);
        requests.put(key, new StoredRequest(fingerprint, movement));
        return movement;
    }

    @Override
    public synchronized List<GeneralDebtMovement> listMovements(String customerId, LocalDate from,
                                                                LocalDate through, String category) {
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        }
        return movements.stream()
                .filter(movement -> customerId == null || movement.customerId().equals(customerId))
                .filter(movement -> from == null || !movement.occurredAt().toLocalDate().isBefore(from))
                .filter(movement -> through == null || !movement.occurredAt().toLocalDate().isAfter(through))
                .filter(movement -> category == null || category.isBlank()
                        || movement.category().equals(category.trim()))
                .toList();
    }

    @Override public boolean supportsDurableIdempotency() { return false; }

    private static String requireCustomerId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("El cliente es obligatorio.");
        return id;
    }

    private record StoredRequest(String fingerprint, GeneralDebtMovement movement) { }
}
