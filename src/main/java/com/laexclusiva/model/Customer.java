package com.laexclusiva.model;

import com.laexclusiva.validation.PersonValidator;
import java.util.UUID;

/**
 * Representa a un cliente dentro del sistema, incluyendo su control de saldos y fiados.
 */
public class Customer {

    private final String id;
    private String name;
    private String phone;
    private String address;
    private double currentDebt; // Saldo pendiente acumulado por fiados

    /**
     * Constructor para un nuevo cliente con ID autogenerado único y deuda inicial en 0.
     * 
     * @param name Nombre obligatorio del cliente.
     * @param phone Teléfono obligatorio.
     * @param address Dirección obligatoria.
     */
    public Customer(String name, String phone, String address) {
        PersonValidator.validatePersonData(name, phone, address);
        this.id = "CLI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
        this.currentDebt = 0.0;
    }

    /**
     * Constructor para reconstruir un cliente existente con saldo específico.
     */
    public Customer(String id, String name, String phone, String address, double currentDebt) {
        PersonValidator.validatePersonData(name, phone, address);
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del cliente no puede estar vacío.");
        }
        if (currentDebt < 0) {
            throw new IllegalArgumentException("La deuda no puede ser un valor negativo.");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
        this.currentDebt = currentDebt;
    }

    // Métodos de negocio para fiados y abonos

    /**
     * Registra un valor adeudado (fiado) al saldo del cliente.
     * 
     * @param amount Monto a fiar.
     */
    public void addDebt(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("El monto a fiar debe ser mayor a cero.");
        }
        this.currentDebt += amount;
    }

    /**
     * Registra un abono al saldo adeudado del cliente.
     * 
     * @param amount Monto abonado.
     */
    public void applyPayment(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("El monto del abono debe ser mayor a cero.");
        }
        if (amount > this.currentDebt) {
            throw new IllegalArgumentException("El abono no puede ser mayor que la deuda actual ($" + this.currentDebt + " COP).");
        }
        this.currentDebt -= amount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        PersonValidator.validatePersonData(name, this.phone, this.address);
        this.name = name.trim();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        PersonValidator.validatePersonData(this.name, phone, this.address);
        this.phone = phone.trim();
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        PersonValidator.validatePersonData(this.name, this.phone, address);
        this.address = address.trim();
    }

    public double getCurrentDebt() {
        return currentDebt;
    }

    @Override
    public String toString() {
        return String.format("Cliente [ID: %s | Nombre: %s | Teléfono: %s | Dirección: %s | Deuda Actual: $%.2f COP]",
                id, name, phone, address, currentDebt);
    }
}