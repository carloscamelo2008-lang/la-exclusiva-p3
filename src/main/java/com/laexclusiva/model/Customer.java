package com.laexclusiva.model;

import java.math.BigDecimal;

public class Customer {
    private String id;
    private String name;
    private String phone;
    private String address;
    private BigDecimal saldo;
    private final BigDecimal creditLimit;

    public Customer(String id, String name, String phone, String address) {
        this(id, name, phone, address, BigDecimal.ZERO);
    }

    public Customer(String id, String name, String phone, String address, BigDecimal saldo) {
        this(id, name, phone, address, saldo, BigDecimal.ZERO);
    }

    public Customer(String id, String name, String phone, String address, BigDecimal saldo, BigDecimal creditLimit) {
        this(normalizeText(id), name, phone, address, saldo, creditLimit, true);
    }

    /** Reconstruye un cliente sin cambiar la clave primaria de filas históricas. */
    public static Customer reconstitute(String id, String name, String phone, String address,
                                        BigDecimal balance, BigDecimal creditLimit) {
        return new Customer(id, name, phone, address, balance, creditLimit, false);
    }

    private Customer(String id, String name, String phone, String address, BigDecimal saldo,
                     BigDecimal creditLimit, boolean normalizeContact) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El ID del cliente es obligatorio y no puede estar vacío.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio y no puede estar vacío.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("El teléfono del cliente es obligatorio y no puede estar vacío.");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("La dirección del cliente es obligatoria y no puede estar vacía.");
        }
        if (saldo == null || saldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser nulo ni negativo.");
        }
        this.id = id;
        this.name = normalizeContact ? normalizeText(name) : name;
        this.phone = normalizePhone(phone);
        this.address = normalizeContact ? normalizeText(address) : address;
        this.saldo = saldo;
        if (creditLimit == null || creditLimit.signum() < 0 || creditLimit.stripTrailingZeros().scale() > 2
                || creditLimit.compareTo(new BigDecimal("99999999999999999.99")) > 0) {
            throw new IllegalArgumentException("El cupo de crédito debe ser un importe no negativo de hasta dos decimales.");
        }
        this.creditLimit = creditLimit;
    }

    /** Elimina espacios exteriores y reduce los grupos interiores a un espacio. */
    public static String normalizeText(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }

    /** Clave de comparación para advertir coincidencias de nombre. */
    public static String comparableName(String name) {
        if (name == null) return "";
        return java.text.Normalizer.normalize(normalizeText(name), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }

    public BigDecimal getCreditLimit() { return creditLimit; }

    /** Valida la exposición total del cliente antes de registrar una venta a crédito. */
    public void requireAvailableCredit(BigDecimal newDebt) {
        if (newDebt.signum() > 0 && saldo.add(newDebt).compareTo(creditLimit) > 0) {
            throw new IllegalArgumentException("La venta supera el cupo de crédito disponible del cliente.");
        }
    }

    /** Normaliza un teléfono a dígitos ASCII, conservando ceros iniciales. */
    public static String normalizePhone(String phone) {
        String compact = phone == null ? "" : phone.replaceAll("\\s", "");
        if (!compact.matches("\\+?[0-9]{1,50}")) {
            throw new IllegalArgumentException("El teléfono debe contener únicamente dígitos, con un + inicial opcional.");
        }
        return compact.startsWith("+") ? compact.substring(1) : compact;
    }

    /** Devuelve una copia independiente del cliente. */
    public Customer copy() {
        return reconstitute(id, name, phone, address, saldo, creditLimit);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }

    public BigDecimal getBalance() { 
        return saldo; 
    }

    public void addDebt(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto de la deuda debe ser mayor a cero.");
        }
        this.saldo = this.saldo.add(amount);
    }

    public void applyPayment(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del abono debe ser mayor a cero.");
        }
        if (amount.compareTo(this.saldo) > 0) {
            throw new IllegalArgumentException("El abono no puede superar el saldo actual de la deuda.");
        }
        this.saldo = this.saldo.subtract(amount);
    }
}
