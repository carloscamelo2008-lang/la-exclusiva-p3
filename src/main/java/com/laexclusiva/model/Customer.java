package com.laexclusiva.model;

import java.math.BigDecimal;

public class Customer {
    private String id;
    private String name;
    private String phone;
    private String address;
    private BigDecimal saldo;

    public Customer(String id, String name, String phone, String address) {
        this(id, name, phone, address, BigDecimal.ZERO);
    }

    public Customer(String id, String name, String phone, String address, BigDecimal saldo) {
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
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.saldo = saldo;
    }

    /** Devuelve una copia independiente del cliente. */
    public Customer copy() {
        return new Customer(id, name, phone, address, saldo);
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
