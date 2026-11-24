package com.laexclusiva.service;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.CashSummary;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.repository.CashMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Implementación de demostración que deriva cobros de las ventas y conserva los otros movimientos en memoria. */
public final class InMemoryCashWorkflow implements CashWorkflow {
    private final SalesWorkflow sales;
    private final CustomerGeneralDebtWorkflow generalDebt;
    private final List<CashMovement> manualMovements = new ArrayList<>();
    private final Map<String, ManualRecord> manualByKey = new HashMap<>();
    private long nextId = 1;

    /** Compone recibos de ventas/cartera con egresos e ingresos manuales del modo memoria. */
    public InMemoryCashWorkflow(SalesWorkflow sales, CustomerGeneralDebtWorkflow generalDebt) {
        if (sales == null || generalDebt == null) throw new IllegalArgumentException("Los servicios son obligatorios.");
        this.sales = sales;
        this.generalDebt = generalDebt;
    }

    @Override
    public synchronized CashMovement registerMovement(CashDirection direction, CashCategory category,
                                                       BigDecimal amount, LocalDateTime occurredAt,
                                                       String description, String idempotencyKey) {
        return registerMovement(direction, category, amount, occurredAt, description, idempotencyKey, null);
    }

    @Override
    public synchronized CashMovement registerMovement(CashDirection direction, CashCategory category,
            BigDecimal amount, LocalDateTime occurredAt, String description, String idempotencyKey,
            com.laexclusiva.model.PaymentMethod paymentMethod) {
        if (direction == null || category == null || category.direction() != direction || !category.isManualEntry()
                || amount == null || amount.signum() <= 0 || occurredAt == null
                || description == null || description.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("El movimiento manual requiere datos válidos y una categoría manual.");
        }
        CashMovement proposed = new CashMovement(0, direction, category, amount, occurredAt,
                description.trim(), idempotencyKey, "MANUAL", idempotencyKey, AuthorizationService.operationUserId(), paymentMethod);
        String fingerprint = CashWorkflowJdbc.cashFingerprint(proposed);
        ManualRecord old = manualByKey.get(idempotencyKey);
        if (old != null) {
            if (!old.fingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("La clave de caja ya se usó con datos diferentes.");
            }
            return old.movement();
        }
        CashMovement saved = withId(proposed, nextId++);
        manualMovements.add(saved);
        manualByKey.put(idempotencyKey, new ManualRecord(fingerprint, saved));
        return saved;
    }

    @Override
    public synchronized List<CashMovement> listMovements(LocalDate from, LocalDate through, CashCategory category) {
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        }
        List<CashMovement> all = new ArrayList<>(manualMovements);
        for (Sale sale : sales.listAll()) {
            List<SalePayment> payments = sale.getPayments();
            for (int index = 0; index < payments.size(); index++) {
                SalePayment payment = payments.get(index);
                boolean initial = index == 0 && payment.getTimestamp().equals(sale.getCreatedAt());
                CashCategory paymentCategory = initial ? CashCategory.SALE_INITIAL_PAYMENT : CashCategory.SALE_INSTALLMENT;
                String source = sale.getId() + ":" + index + ":" + payment.getTimestamp();
                all.add(new CashMovement(0, CashDirection.INCOME, paymentCategory, payment.getAmount(),
                        payment.getTimestamp(), (initial ? "Pago inicial de venta " : "Abono a venta ") + sale.getId(),
                        "MEMORY-SALE:" + source, initial ? "SALE_INITIAL" : "SALE_INSTALLMENT", source, payment.getReceivedByUserId(), null));
            }
        }
        for (GeneralDebtMovement movement : generalDebt.listMovements(null, from, through, null)) {
            if (movement.type() == GeneralDebtMovementType.PAYMENT) {
                all.add(new CashMovement(0, CashDirection.INCOME, CashCategory.GENERAL_DEBT_PAYMENT,
                        movement.amount(), movement.occurredAt(), movement.description(),
                        "MEMORY-GENERAL:" + movement.idempotencyKey(), "GENERAL_DEBT_PAYMENT",
                        movement.idempotencyKey(), movement.receivedByUserId(), null));
            }
        }
        return all.stream()
                .filter(row -> from == null || !row.occurredAt().toLocalDate().isBefore(from))
                .filter(row -> through == null || !row.occurredAt().toLocalDate().isAfter(through))
                .filter(row -> category == null || row.category() == category)
                .sorted(java.util.Comparator.comparing(CashMovement::occurredAt).thenComparing(CashMovement::sourceReference))
                .toList();
    }

    @Override
    public CashSummary summarize(LocalDate from, LocalDate through) {
        List<CashMovement> selected = listMovements(from, through, null);
        BigDecimal invoices = sales.listAll().stream()
                .filter(sale -> from == null || !sale.getCreatedAt().toLocalDate().isBefore(from))
                .filter(sale -> through == null || !sale.getCreatedAt().toLocalDate().isAfter(through))
                .map(Sale::calculateTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saleReceipts = sum(selected, CashCategory.SALE_INITIAL_PAYMENT)
                .add(sum(selected, CashCategory.SALE_INSTALLMENT));
        BigDecimal generalReceipts = sum(selected, CashCategory.GENERAL_DEBT_PAYMENT);
        BigDecimal otherIncome = sum(selected, CashCategory.OTHER_INCOME);
        BigDecimal expenses = selected.stream().filter(row -> row.direction() == CashDirection.EXPENSE)
                .map(CashMovement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CashSummary(invoices, saleReceipts, generalReceipts, otherIncome, expenses);
    }

    @Override public boolean supportsDurableIdempotency() { return false; }

    private static CashMovement withId(CashMovement movement, long id) {
        return new CashMovement(id, movement.direction(), movement.category(), movement.amount(),
                movement.occurredAt(), movement.description(), movement.idempotencyKey(),
                movement.sourceType(), movement.sourceReference(), movement.userId(), movement.paymentMethod());
    }

    private static BigDecimal sum(List<CashMovement> rows, CashCategory category) {
        return rows.stream().filter(row -> row.category() == category).map(CashMovement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record ManualRecord(String fingerprint, CashMovement movement) { }
}
