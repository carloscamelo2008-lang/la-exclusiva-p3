package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Venta registrada con un vendedor, un cliente opcional, sus líneas y sus pagos.
 * Mantiene el total como suma de los subtotales y el saldo como total menos pagos; conserva
 * copias de las entidades asociadas para que cambios posteriores no alteren el registro.
 */
public final class Sale {

    private final String id;
    private final LocalDateTime createdAt;
    private final Seller seller;
    private final Customer customer;
    private final List<SaleLine> lines;
    private final List<SalePayment> payments = new ArrayList<>();

    /**
     * Crea una venta con ID generado y captura las líneas, el vendedor y el cliente como copias.
     * El pago inicial puede ser cero, pero no puede superar el total. Si queda saldo pendiente,
     * debe asociarse un cliente; para una venta pagada el cliente es opcional.
     *
     * @param createdAt fecha y hora de registro
     * @param seller vendedor responsable de la venta
     * @param customer cliente asociado; puede ser {@code null} si el pago cubre el total
     * @param lines líneas de productos y presentaciones; deben incluir al menos una
     * @param initialPayment importe recibido al registrar la venta
     * @throws IllegalArgumentException si falta la fecha o el vendedor, las líneas son inválidas,
     *                                  el pago es nulo, negativo o supera el total, o queda saldo
     *                                  pendiente sin un cliente válido
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

    /**
     * Suma los subtotales de las líneas para calcular el total de una venta.
     *
     * @param lines líneas cuyos subtotales se suman
     * @return total monetario de las líneas
     * @throws NullPointerException si la lista o alguna de sus líneas es nula
     */
    public static BigDecimal calculateTotal(List<SaleLine> lines) {
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

    /** Devuelve la lista inmutable de líneas capturada al crear la venta. */
    public List<SaleLine> getLines() {
        return lines;
    }

    /** Devuelve una copia inmutable de los pagos registrados hasta el momento. */
    public List<SalePayment> getPayments() {
        return List.copyOf(payments);
    }

    /**
     * Calcula el total de la venta a partir de sus líneas.
     *
     * @return suma de los subtotales de las líneas
     */
    public BigDecimal calculateTotal() {
        return calculateTotal(lines);
    }

    /**
     * Suma los pagos y abonos asociados a la venta.
     *
     * @return importe total recibido para esta venta
     */
    public BigDecimal calculateTotalPaid() {
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (SalePayment payment : payments) {
            totalPaid = totalPaid.add(payment.getAmount());
        }
        return totalPaid;
    }

    /**
     * Calcula el valor que aún falta por pagar.
     *
     * @return total de la venta menos la suma de sus pagos
     */
    public BigDecimal calculateBalance() {
        return calculateTotal().subtract(calculateTotalPaid());
    }

    /**
     * Devuelve una copia independiente que conserva el ID, la fecha, las líneas y los pagos.
     *
     * @return copia de esta venta
     */
    public Sale copy() {
        return new Sale(id, createdAt, seller, customer, lines, payments);
    }

    /**
     * Registra un abono positivo si no supera el saldo pendiente.
     * Una solicitud inválida no cambia los pagos ni los valores financieros.
     *
     * @param payment pago o abono que se asocia a esta venta
     * @throws IllegalArgumentException si el pago es nulo o supera el saldo pendiente
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
