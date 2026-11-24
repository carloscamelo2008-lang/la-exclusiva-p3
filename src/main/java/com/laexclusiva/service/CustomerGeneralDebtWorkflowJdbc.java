package com.laexclusiva.service;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.CashMovementRepository;
import com.laexclusiva.repository.CustomerBalanceAdjuster;
import com.laexclusiva.repository.CustomerDebtQuery;
import com.laexclusiva.repository.CustomerGeneralDebtMovementRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Coordina incrementos y pagos de deuda general, historial e ingreso de caja en transacciones compartidas. */
public final class CustomerGeneralDebtWorkflowJdbc implements CustomerGeneralDebtWorkflow {
    private final CustomerRepositoryInterface customers;
    private final CustomerDebtQuery salesDebt;
    private final CustomerBalanceAdjuster balances;
    private final CustomerGeneralDebtMovementRepository movements;
    private final CashMovementRepository cash;
    private final TransactionBoundary transactions;
    private final Clock clock;

    /** Construye el caso de uso con repositorios que comparten el mismo contexto transaccional JDBC. */
    public CustomerGeneralDebtWorkflowJdbc(CustomerRepositoryInterface customers, CustomerDebtQuery salesDebt,
                                           CustomerBalanceAdjuster balances,
                                           CustomerGeneralDebtMovementRepository movements,
                                           CashMovementRepository cash, TransactionBoundary transactions) {
        this(customers, salesDebt, balances, movements, cash, transactions, Clock.systemDefaultZone());
    }

    /** Variante con reloj inyectable para validar fechas sin dependencia de la hora local. */
    public CustomerGeneralDebtWorkflowJdbc(CustomerRepositoryInterface customers, CustomerDebtQuery salesDebt,
                                           CustomerBalanceAdjuster balances,
                                           CustomerGeneralDebtMovementRepository movements,
                                           CashMovementRepository cash, TransactionBoundary transactions,
                                           Clock clock) {
        if (customers == null || salesDebt == null || balances == null || movements == null
                || cash == null || transactions == null || clock == null) {
            throw new IllegalArgumentException("Las dependencias financieras JDBC son obligatorias.");
        }
        this.customers = customers;
        this.salesDebt = salesDebt;
        this.balances = balances;
        this.movements = movements;
        this.cash = cash;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public CustomerDebtBreakdown findBalance(String customerId) {
        String id = requireCustomerId(customerId);
        return transactions.inTransaction(() -> {
            Customer customer = lockCustomer(id);
            return breakdown(customer);
        });
    }

    @Override
    public GeneralDebtMovement registerDebt(String customerId, BigDecimal amount, String category,
                                            String description, String idempotencyKey) {
        return register(GeneralDebtMovementType.DEBT_INCREASE, customerId, amount,
                category, description, idempotencyKey);
    }

    @Override
    public GeneralDebtMovement registerPayment(String customerId, BigDecimal amount, String category,
                                               String description, String idempotencyKey) {
        return register(GeneralDebtMovementType.PAYMENT, customerId, amount,
                category, description, idempotencyKey);
    }

    private GeneralDebtMovement register(GeneralDebtMovementType type, String customerId, BigDecimal amount,
                                         String category, String description, String key) {
        String id = requireCustomerId(customerId);
        BigDecimal normalizedAmount = requireMoney(amount);
        String normalizedCategory = requireText(category, "La categoría es obligatoria.", 80);
        String normalizedDescription = requireText(description, "La descripción es obligatoria.", 2000);
        requireKey(key);
        String fingerprint = FinancialRequestFingerprint.calculate(type.name(), id,
                normalizedAmount.toPlainString(), normalizedCategory, normalizedDescription);
        return transactions.inTransaction(() -> {
            Customer lockedCustomer = lockCustomer(id);
            CustomerGeneralDebtMovementRepository.RequestRecord existing =
                    movements.findByIdempotencyKey(key).orElse(null);
            if (existing != null) return replay(existing, fingerprint);

            CustomerDebtBreakdown current = breakdown(lockedCustomer);
            requireConsistent(current);
            if (type == GeneralDebtMovementType.PAYMENT
                    && normalizedAmount.compareTo(current.generalDebt()) > 0) {
                throw new IllegalArgumentException(
                        "El abono no puede superar la deuda general disponible del cliente.");
            }

            GeneralDebtMovement proposed = new GeneralDebtMovement(0, id, type, normalizedAmount,
                    LocalDateTime.now(clock), normalizedCategory, normalizedDescription, key,
                    type == GeneralDebtMovementType.PAYMENT ? AuthorizationService.operationUserId() : null);
            CustomerGeneralDebtMovementRepository.SaveResult saved = movements.saveOnce(proposed, fingerprint);
            if (!saved.inserted()) return saved.movement();

            if (type == GeneralDebtMovementType.DEBT_INCREASE) {
                balances.increaseBalance(id, normalizedAmount);
            } else {
                balances.decreaseBalance(id, normalizedAmount);
                recordCashReceipt(saved.movement());
            }
            return saved.movement();
        });
    }

    private void recordCashReceipt(GeneralDebtMovement movement) {
        String key = "GENERAL-DEBT-PAYMENT:" + movement.idempotencyKey();
        CashMovement entry = new CashMovement(0, CashDirection.INCOME, CashCategory.GENERAL_DEBT_PAYMENT,
                movement.amount(), movement.occurredAt(), movement.description(), key,
                "GENERAL_DEBT_PAYMENT", movement.idempotencyKey(), movement.receivedByUserId(), null);
        String fingerprint = FinancialRequestFingerprint.calculate(entry.direction().name(),
                entry.category().name(), entry.amount().toPlainString(), entry.occurredAt().toString(),
                entry.description(), entry.sourceType(), entry.sourceReference());
        if (!cash.saveOnce(entry, fingerprint).inserted()) {
            throw new IllegalStateException("El pago general ya tiene un movimiento de caja; revise la integridad.");
        }
    }

    @Override
    public List<GeneralDebtMovement> listMovements(String customerId, LocalDate from,
                                                  LocalDate through, String category) {
        if (customerId != null) requireCustomerId(customerId);
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        return movements.find(customerId, from, through,
                category == null || category.isBlank() ? null : category.trim());
    }

    @Override public boolean supportsDurableIdempotency() { return true; }

    private Customer lockCustomer(String id) {
        Customer customer = customers.findByIdForUpdate(id);
        if (customer == null) throw new IllegalArgumentException("No existe el cliente " + id + ".");
        return customer;
    }

    private CustomerDebtBreakdown breakdown(Customer customer) {
        BigDecimal sales = salesDebt.findOutstandingSalesDebt(customer.getId());
        return new CustomerDebtBreakdown(customer.getBalance(), sales, customer.getBalance().subtract(sales));
    }

    private static GeneralDebtMovement replay(CustomerGeneralDebtMovementRepository.RequestRecord record,
                                              String fingerprint) {
        if (!record.fingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("La clave de deuda general ya se usó con datos diferentes.");
        }
        return record.movement();
    }

    private static void requireConsistent(CustomerDebtBreakdown breakdown) {
        if (!breakdown.isConsistent()) {
            throw new IllegalStateException("La deuda de ventas supera el saldo agregado; no se puede modificar "
                    + "la deuda general hasta corregir la inconsistencia.");
        }
    }

    private static String requireCustomerId(String id) {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new IllegalArgumentException("El ID del cliente debe tener entre 1 y 64 caracteres.");
        }
        return id;
    }

    private static BigDecimal requireMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El importe debe ser mayor que cero.");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) throw new IllegalArgumentException("El importe supera NUMERIC(19,2).");
            return normalized;
        } catch (ArithmeticException invalidScale) {
            throw new IllegalArgumentException("El importe admite máximo dos decimales.", invalidScale);
        }
    }

    private static String requireText(String value, String message, int maximumLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("El texto supera el límite de " + maximumLength + " caracteres.");
        }
        return normalized;
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 64) {
            throw new IllegalArgumentException("La clave idempotente debe tener entre 1 y 64 caracteres.");
        }
    }
}
