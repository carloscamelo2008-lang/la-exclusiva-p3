package com.laexclusiva.model;

import java.math.BigDecimal;

public class Customer {
    private String id;
    private String name;
    private String phone;
    private String address;
    private BigDecimal saldo;

    public Customer(String id, String name, String phone, String address) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.saldo = BigDecimal.ZERO;
    }

    public Customer(String id, String name, String phone, String address, BigDecimal saldo) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.saldo = saldo != null ? saldo : BigDecimal.ZERO;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }

    public BigDecimal getBalance() { 
        return saldo; 
    }

    public void addDebt(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.saldo = this.saldo.add(amount);
        }
    }

    public void applyPayment(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.saldo = this.saldo.subtract(amount);
        }
    }
}