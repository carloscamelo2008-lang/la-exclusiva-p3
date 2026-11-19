package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.CashSnapshot;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.CashMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pruebas de la capa de caja con un repositorio simulado, sin afirmar uso de PostgreSQL. */
class CashWorkflowJdbcTest {
    @Test
    void summaryCountsCashReceiptsSeparatelyFromInvoicesAndManualWritesAreIdempotent() {
        FakeCashRepository repository = new FakeCashRepository();
        CashWorkflowJdbc cash = new CashWorkflowJdbc(repository, new DirectTransactionBoundary());
        LocalDate date = LocalDate.of(2026, 11, 18);
        seed(repository, CashCategory.SALE_INITIAL_PAYMENT, new BigDecimal("100.00"), date, "SALE_INITIAL", "S1");
        seed(repository, CashCategory.SALE_INSTALLMENT, new BigDecimal("50.00"), date, "SALE_INSTALLMENT", "S1:P1");
        seed(repository, CashCategory.GENERAL_DEBT_PAYMENT, new BigDecimal("20.00"), date,
                "GENERAL_DEBT_PAYMENT", "D1");

        CashMovement income = cash.registerMovement(CashDirection.INCOME, CashCategory.OTHER_INCOME,
                new BigDecimal("50.00"), date.atTime(10, 0), "Ingreso de prueba", "manual-income-1");
        CashMovement replay = cash.registerMovement(CashDirection.INCOME, CashCategory.OTHER_INCOME,
                new BigDecimal("50.00"), date.atTime(10, 0), "Ingreso de prueba", "manual-income-1");
        cash.registerMovement(CashDirection.EXPENSE, CashCategory.ICE,
                new BigDecimal("30.00"), date.atTime(11, 0), "Compra de hielo", "manual-expense-1");

        assertEquals(income, replay);
        assertThrows(IllegalArgumentException.class, () -> cash.registerMovement(CashDirection.INCOME,
                CashCategory.OTHER_INCOME, new BigDecimal("51.00"), date.atTime(10, 0),
                "Carga distinta", "manual-income-1"));
        assertThrows(IllegalArgumentException.class, () -> cash.registerMovement(CashDirection.INCOME,
                CashCategory.SALE_INSTALLMENT, BigDecimal.ONE, date.atTime(12, 0),
                "Ingreso duplicado manual", "manual-forbidden"));

        var summary = cash.summarize(date, date);
        assertEquals(new BigDecimal("1000.00"), summary.invoicedSales());
        assertEquals(new BigDecimal("150.00"), summary.saleReceipts());
        assertEquals(new BigDecimal("20.00"), summary.generalDebtReceipts());
        assertEquals(new BigDecimal("50.00"), summary.otherIncome());
        assertEquals(new BigDecimal("30.00"), summary.expenses());
        assertEquals(new BigDecimal("220.00"), summary.cashIn());
        assertEquals(new BigDecimal("190.00"), summary.netCash());
        assertEquals(1, cash.listMovements(date, date, CashCategory.OTHER_INCOME).size());
        assertEquals(5, cash.listMovements(date, date, null).size());
        assertNotEquals(summary.invoicedSales(), summary.cashIn());
    }

    @Test
    void snapshotKeepsCategoryFilterOnRowsAndRetainsUnfilteredSummary() {
        FakeCashRepository repository = new FakeCashRepository();
        CashWorkflowJdbc cash = new CashWorkflowJdbc(repository, new DirectTransactionBoundary());
        LocalDate date = LocalDate.of(2026, 11, 18);
        seed(repository, CashCategory.SALE_INITIAL_PAYMENT, new BigDecimal("100.00"), date,
                "SALE_INITIAL", "S1");
        seed(repository, CashCategory.OTHER_INCOME, new BigDecimal("25.00"), date, "MANUAL", "M1");

        CashSnapshot snapshot = cash.loadSnapshot(date, date, CashCategory.OTHER_INCOME);

        assertEquals(1, snapshot.movements().size());
        assertEquals(CashCategory.OTHER_INCOME, snapshot.movements().get(0).category());
        assertEquals(new BigDecimal("100.00"), snapshot.summary().saleReceipts());
        assertEquals(new BigDecimal("25.00"), snapshot.summary().otherIncome());
        assertEquals(new BigDecimal("1000.00"), snapshot.summary().invoicedSales());
    }

    private static void seed(FakeCashRepository repository, CashCategory category, BigDecimal amount,
                             LocalDate date, String sourceType, String sourceReference) {
        CashDirection direction = category.direction();
        String key = "seed:" + sourceType + ":" + sourceReference;
        CashMovement row = new CashMovement(0, direction, category, amount, date.atTime(9, 0),
                "Movimiento semilla", key, sourceType, sourceReference);
        repository.saveOnce(row, CashWorkflowJdbc.cashFingerprint(row));
    }

    private static final class FakeCashRepository implements CashMovementRepository {
        private final Map<String, RequestRecord> byKey = new HashMap<>();
        private final List<CashMovement> rows = new ArrayList<>();
        private long nextId = 1;

        @Override public Optional<RequestRecord> findByIdempotencyKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }
        @Override public SaveResult saveOnce(CashMovement movement, String fingerprint) {
            RequestRecord existing = byKey.get(movement.idempotencyKey());
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    throw new IllegalArgumentException("La clave ya se usó con otros datos.");
                }
                return new SaveResult(existing.movement(), false);
            }
            CashMovement saved = new CashMovement(nextId++, movement.direction(), movement.category(),
                    movement.amount(), movement.occurredAt(), movement.description(), movement.idempotencyKey(),
                    movement.sourceType(), movement.sourceReference());
            byKey.put(saved.idempotencyKey(), new RequestRecord(saved, fingerprint));
            rows.add(saved);
            return new SaveResult(saved, true);
        }
        @Override public List<CashMovement> find(LocalDate from, LocalDate through, String category) {
            return rows.stream()
                    .filter(row -> from == null || !row.occurredAt().toLocalDate().isBefore(from))
                    .filter(row -> through == null || !row.occurredAt().toLocalDate().isAfter(through))
                    .filter(row -> category == null || row.category().name().equals(category)).toList();
        }
        @Override public BigDecimal findInvoicedSales(LocalDate from, LocalDate through) {
            return new BigDecimal("1000.00");
        }
    }

    private static final class DirectTransactionBoundary implements TransactionBoundary {
        @Override public <T> T inTransaction(java.util.function.Supplier<T> operation) {
            return operation.get();
        }
    }
}
