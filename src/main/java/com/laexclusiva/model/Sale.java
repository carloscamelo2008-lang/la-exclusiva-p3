package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Venta con sus líneas y pagos. La entidad mantiene la coherencia entre el total,
 * lo pagado y el saldo pendiente.
 */
public final class Sale {

    private final String id;
    private final LocalDateTime createdAt;
    private final Seller seller;
    private final Customer customer;
    private final List<SaleLine> lines;
    private final List<SalePayment> payments = new ArrayList<>();

    /**
     * Crea una venta. El pago inicial puede ser cero; si queda saldo pendiente,
     * se requiere un cliente con identificador no vacío.
     */
    public Sale(LocalDateTime createdAt, Seller seller, Customer customer,
                List<SaleLine> lines, BigDecimal initialPayment) {
        if (createdAt == null) {
            throw new IllegalArgumentException("La fecha y hora de la venta son obligatorias.");
        }
        if (seller == null) {
            throw new IllegalArgumentException("El vendedor de la venta es obligatorio.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos una línea.");
        }
        for (SaleLine line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("La venta no puede contener líneas nulas.");
            }
        }
        if (initialPayment == null || initialPayment.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El pago inicial no puede ser nulo ni negativo.");
        }

        List<SaleLine> lineSnapshot = lines.stream().map(SaleLine::copy).toList();
        BigDecimal total = calculateTotal(lineSnapshot);
        if (initialPayment.compareTo(total) > 0) {
            throw new IllegalArgumentException("El pago inicial no puede superar el total de la venta.");
        }

        BigDecimal balance = total.subtract(initialPayment);
        if (balance.compareTo(BigDecimal.ZERO) > 0 && customer == null) {
            throw new IllegalArgumentException("Se requiere un cliente si la venta queda con saldo pendiente.");
        }
        if (customer != null && (customer.getId() == null || customer.getId().isBlank())) {
            throw new IllegalArgumentException("El cliente asociado debe tener un identificador válido.");
        }

        this.id = "SALE-" + UUID.randomUUID().toString().toUpperCase();
        this.createdAt = createdAt;
        this.seller = copySeller(seller);
        this.customer = customer == null ? null : customer.copy();
        this.lines = lineSnapshot;
        if (initialPayment.compareTo(BigDecimal.ZERO) > 0) {
            this.payments.add(new SalePayment(initialPayment, createdAt));
        }
    }

    private Sale(String id, LocalDateTime createdAt, Seller seller, Customer customer,
                 List<SaleLine> lines, List<SalePayment> payments) {
        this.id = id;
        this.createdAt = createdAt;
        this.seller = copySeller(seller);
        this.customer = customer == null ? null : customer.copy();
        this.lines = lines.stream().map(SaleLine::copy).toList();
        this.payments.addAll(payments);
    }

    private static Seller copySeller(Seller seller) {
        return new Seller(seller.getId(), seller.getName(), seller.getPhone(), seller.getAddress());
    }

    private static BigDecimal calculateTotal(List<SaleLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SaleLine line : lines) {
            total = total.add(line.getSubtotal());
        }
        return total;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Seller getSeller() {
        return copySeller(seller);
    }

    public Customer getCustomer() {
        return customer == null ? null : customer.copy();
    }

    /** Devuelve una copia inmutable de las líneas de la venta. */
    public List<SaleLine> getLines() {
        return lines;
    }

    /** Devuelve una vista inmutable de los pagos registrados. */
    public List<SalePayment> getPayments() {
        return List.copyOf(payments);
    }

    public BigDecimal calculateTotal() {
        return calculateTotal(lines);
    }

    public BigDecimal calculateTotalPaid() {
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (SalePayment payment : payments) {
            totalPaid = totalPaid.add(payment.getAmount());
        }
        return totalPaid;
    }

    public BigDecimal calculateBalance() {
        return calculateTotal().subtract(calculateTotalPaid());
    }

    /** Devuelve un snapshot independiente, conservando ID, fecha, líneas y pagos. */
    public Sale copy() {
        return new Sale(id, createdAt, seller, customer, lines, payments);
    }

    /**
     * Registra un abono positivo si no supera el saldo pendiente.
     * Una solicitud inválida no cambia los pagos ni los valores financieros.
     */
    public void registerPayment(SalePayment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("El pago es obligatorio.");
        }
        if (payment.getAmount().compareTo(calculateBalance()) > 0) {
            throw new IllegalArgumentException("El pago no puede superar el saldo pendiente de la venta.");
        }
        payments.add(payment);
    }
}
