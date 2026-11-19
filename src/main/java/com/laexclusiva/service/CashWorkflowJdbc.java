package com.laexclusiva.service;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.CashSnapshot;
import com.laexclusiva.model.CashSummary;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.CashMovementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Casos de uso JDBC de caja; los recibos comerciales se escriben junto con la operación de origen. */
public final class CashWorkflowJdbc implements CashWorkflow {
    private final CashMovementRepository movements;
    private final TransactionBoundary transactions;

    /**
     * Crea los casos de uso de caja con el repositorio y el límite transaccional compartido.
     *
     * @param movements repositorio de movimientos
     * @param transactions coordinador utilizado también por los flujos financieros
     * @throws IllegalArgumentException si falta una dependencia
     */
    public CashWorkflowJdbc(CashMovementRepository movements, TransactionBoundary transactions) {
        if (movements == null || transactions == null) {
            throw new IllegalArgumentException("El repositorio y el límite transaccional son obligatorios.");
        }
        this.movements = movements;
        this.transactions = transactions;
    }

    @Override
    public CashMovement registerMovement(CashDirection direction, CashCategory category, BigDecimal amount,
                                         LocalDateTime occurredAt, String description, String idempotencyKey) {
        if (direction == null || category == null || category.direction() != direction || !category.isManualEntry()) {
            throw new IllegalArgumentException("Seleccione una categoría manual del tipo de movimiento indicado.");
        }
        BigDecimal normalized = requireMoney(amount);
        if (occurredAt == null || occurredAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("La fecha del movimiento no es válida.");
        }
        String detail = requireText(description, 2000);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("La clave de caja debe tener entre 1 y 64 caracteres.");
        }
        CashMovement movement = new CashMovement(0, direction, category, normalized, occurredAt,
                detail, idempotencyKey, "MANUAL", idempotencyKey);
        String fingerprint = cashFingerprint(movement);
        CashMovementRepository.SaveResult saved = movements.saveOnce(movement, fingerprint);
        return saved.movement();
    }

    @Override
    public List<CashMovement> listMovements(LocalDate from, LocalDate through, CashCategory category) {
        return movements.find(from, through, category == null ? null : category.name());
    }

    @Override
    public CashSummary summarize(LocalDate from, LocalDate through) {
        List<CashMovement> selected = movements.find(from, through, null);
        BigDecimal saleReceipts = sum(selected, CashCategory.SALE_INITIAL_PAYMENT)
                .add(sum(selected, CashCategory.SALE_INSTALLMENT));
        BigDecimal generalReceipts = sum(selected, CashCategory.GENERAL_DEBT_PAYMENT);
        BigDecimal otherIncome = sum(selected, CashCategory.OTHER_INCOME);
        BigDecimal expenses = selected.stream().filter(row -> row.direction() == CashDirection.EXPENSE)
                .map(CashMovement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CashSummary(movements.findInvoicedSales(from, through), saleReceipts,
                generalReceipts, otherIncome, expenses);
    }

    @Override
    public CashSnapshot loadSnapshot(LocalDate from, LocalDate through, CashCategory category) {
        return transactions.inReadOnlyRepeatableRead(
                () -> CashWorkflow.super.loadSnapshot(from, through, category));
    }

    @Override public boolean supportsDurableIdempotency() { return true; }

    static String cashFingerprint(CashMovement movement) {
        return FinancialRequestFingerprint.calculate(movement.direction().name(), movement.category().name(),
                movement.amount().toPlainString(), movement.occurredAt().toString(), movement.description(),
                movement.sourceType(), movement.sourceReference());
    }

    private static BigDecimal sum(List<CashMovement> movements, CashCategory category) {
        return movements.stream().filter(row -> row.category() == category).map(CashMovement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal requireMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("El importe debe ser mayor que cero.");
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) throw new IllegalArgumentException("El importe supera NUMERIC(19,2).");
            return normalized;
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("El importe admite máximo dos decimales.", failure);
        }
    }

    private static String requireText(String text, int max) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("La descripción es obligatoria.");
        String value = text.trim();
        if (value.length() > max) throw new IllegalArgumentException("La descripción supera " + max + " caracteres.");
        return value;
    }
}
