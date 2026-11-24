package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.model.PaymentMethod;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.CashMovementRepository;
import com.laexclusiva.repository.CustomerBalanceAdjuster;
import com.laexclusiva.repository.CustomerDebtQuery;
import com.laexclusiva.repository.CustomerGeneralDebtMovementRepository;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reglas de deuda general con adaptadores simulados; no verifica SQL ni bloqueos reales. */
class CustomerGeneralDebtWorkflowJdbcTest {
    private CustomerRepositoryInterface customers;
    private AtomicReference<BigDecimal> salesDebt;
    private FakeDebtMovementRepository ledger;
    private FakeCashRepository cash;
    private CustomerGeneralDebtWorkflowJdbc workflow;

    @BeforeEach
    void setUp() {
        CustomerRepository repository = new CustomerRepository();
        repository.save(new Customer("C-1", "Cliente ficticio", "3000000000", "Dirección ficticia",
                new BigDecimal("100.00")));
        customers = repository;
        salesDebt = new AtomicReference<>(new BigDecimal("70.00"));
        ledger = new FakeDebtMovementRepository();
        cash = new FakeCashRepository();
        CustomerBalanceAdjuster adjuster = balanceAdjuster(repository);
        CustomerDebtQuery query = id -> salesDebt.get();
        workflow = new CustomerGeneralDebtWorkflowJdbc(customers, query, adjuster, ledger, cash,
                new DirectTransactionBoundary(), Clock.fixed(Instant.parse("2026-11-18T16:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void paymentChangesOnlyGeneralDebtAndRetryDoesNotDuplicateHistoryOrCash() {
        assertEquals(new BigDecimal("30.00"), workflow.findBalance("C-1").generalDebt());
        GeneralDebtMovement payment = workflow.registerPayment("C-1", new BigDecimal("20.00"),
                "Pago general", "Pago de prueba", "general-pay-1", PaymentMethod.TRANSFERENCIA);
        GeneralDebtMovement replay = workflow.registerPayment("C-1", new BigDecimal("20.00"),
                "Pago general", "Pago de prueba", "general-pay-1", PaymentMethod.TRANSFERENCIA);
        assertThrows(IllegalArgumentException.class, () -> workflow.registerPayment("C-1", new BigDecimal("20.00"),
                "Pago general", "Pago de prueba", "general-pay-1", PaymentMethod.EFECTIVO));

        assertEquals(payment, replay);
        assertEquals(new BigDecimal("80.00"), customers.findById("C-1").getBalance());
        assertEquals(new BigDecimal("70.00"), workflow.findBalance("C-1").outstandingSalesDebt());
        assertEquals(new BigDecimal("10.00"), workflow.findBalance("C-1").generalDebt());
        assertEquals(1, ledger.rows.size());
        assertEquals(1, cash.rows.size());
        assertEquals(PaymentMethod.TRANSFERENCIA, cash.rows.get(0).paymentMethod());
        assertEquals(GeneralDebtMovementType.PAYMENT, ledger.rows.get(0).type());
        assertThrows(IllegalArgumentException.class, () -> workflow.registerPayment("C-1",
                new BigDecimal("21.00"), "Pago general", "Pago distinto", "general-pay-1"));
        assertThrows(IllegalArgumentException.class, () -> workflow.registerPayment("C-1",
                new BigDecimal("11.00"), "Pago general", "Excede el disponible", "general-pay-2"));

        GeneralDebtMovement debt = workflow.registerDebt("C-1", new BigDecimal("5.00"), "Ajuste",
                "Cargo general", "general-debt-1");
        assertEquals(GeneralDebtMovementType.DEBT_INCREASE, debt.type());
        assertEquals(new BigDecimal("15.00"), workflow.findBalance("C-1").generalDebt());
        assertEquals(2, workflow.listMovements("C-1", LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 18), null).size());
        assertEquals(1, workflow.listMovements("C-1", null, null, "Pago general").size());
        assertEquals(1, cash.rows.size(), "Crear deuda no es un ingreso de efectivo.");
    }

    @Test
    void refusesFinancialWritesWhenSalesDebtExceedsAggregateCustomerBalance() {
        salesDebt.set(new BigDecimal("101.00"));
        assertEquals(false, workflow.findBalance("C-1").isConsistent());
        assertThrows(IllegalStateException.class, () -> workflow.registerDebt("C-1", BigDecimal.ONE,
                "Ajuste", "No compensar ventas", "bad-state"));
        assertEquals(0, ledger.rows.size());
    }

    private static CustomerBalanceAdjuster balanceAdjuster(CustomerRepository repository) {
        return new CustomerBalanceAdjuster() {
            @Override public BigDecimal increaseBalance(String id, BigDecimal amount) {
                Customer old = repository.findById(id);
                Customer updated = new Customer(id, old.getName(), old.getPhone(), old.getAddress(),
                        old.getBalance().add(amount));
                repository.update(updated);
                return updated.getBalance();
            }
            @Override public BigDecimal decreaseBalance(String id, BigDecimal amount) {
                Customer old = repository.findById(id);
                if (old.getBalance().compareTo(amount) < 0) throw new IllegalArgumentException("Saldo insuficiente.");
                Customer updated = new Customer(id, old.getName(), old.getPhone(), old.getAddress(),
                        old.getBalance().subtract(amount));
                repository.update(updated);
                return updated.getBalance();
            }
        };
    }

    private static final class DirectTransactionBoundary implements TransactionBoundary {
        @Override public <T> T inTransaction(java.util.function.Supplier<T> operation) { return operation.get(); }
    }

    private static final class FakeDebtMovementRepository implements CustomerGeneralDebtMovementRepository {
        private final List<GeneralDebtMovement> rows = new ArrayList<>();
        private final Map<String, RequestRecord> byKey = new HashMap<>();
        private long nextId = 1;
        @Override public Optional<RequestRecord> findByIdempotencyKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }
        @Override public SaveResult saveOnce(GeneralDebtMovement movement, String fingerprint) {
            RequestRecord old = byKey.get(movement.idempotencyKey());
            if (old != null) {
                if (!old.fingerprint().equals(fingerprint)) throw new IllegalArgumentException("Clave distinta.");
                return new SaveResult(old.movement(), false);
            }
            GeneralDebtMovement saved = new GeneralDebtMovement(nextId++, movement.customerId(), movement.type(),
                    movement.amount(), movement.occurredAt(), movement.category(), movement.description(),
                    movement.idempotencyKey());
            rows.add(saved);
            byKey.put(saved.idempotencyKey(), new RequestRecord(saved, fingerprint));
            return new SaveResult(saved, true);
        }
        @Override public List<GeneralDebtMovement> find(String customerId, LocalDate from, LocalDate through,
                                                        String category) {
            return rows.stream().filter(row -> customerId == null || row.customerId().equals(customerId))
                    .filter(row -> from == null || !row.occurredAt().toLocalDate().isBefore(from))
                    .filter(row -> through == null || !row.occurredAt().toLocalDate().isAfter(through))
                    .filter(row -> category == null || row.category().equals(category)).toList();
        }
    }

    private static final class FakeCashRepository implements CashMovementRepository {
        private final List<CashMovement> rows = new ArrayList<>();
        private final Map<String, RequestRecord> byKey = new HashMap<>();
        @Override public Optional<RequestRecord> findByIdempotencyKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }
        @Override public SaveResult saveOnce(CashMovement movement, String fingerprint) {
            RequestRecord old = byKey.get(movement.idempotencyKey());
            if (old != null) {
                if (!old.fingerprint().equals(fingerprint)) throw new IllegalArgumentException("Clave de caja distinta.");
                return new SaveResult(old.movement(), false);
            }
            rows.add(movement);
            byKey.put(movement.idempotencyKey(), new RequestRecord(movement, fingerprint));
            return new SaveResult(movement, true);
        }
        @Override public List<CashMovement> find(LocalDate from, LocalDate through, String category) {
            return List.copyOf(rows);
        }
        @Override public BigDecimal findInvoicedSales(LocalDate from, LocalDate through) { return BigDecimal.ZERO; }
    }
}
