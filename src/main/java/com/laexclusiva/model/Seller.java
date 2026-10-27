package com.laexclusiva.model;

import com.laexclusiva.validation.PersonValidator;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo que representa a un vendedor en el sistema La Exclusiva.
 */
public class Seller {

    private final String id;
    private String name;
    private String phone;
    private String address;

    public Seller(String name, String phone, String address) {
        PersonValidator.validatePersonData(name, phone, address);
        this.id = "SEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
    }

    public Seller(String id, String name, String phone, String address) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del vendedor no puede estar vacío.");
        }
        PersonValidator.validatePersonData(name, phone, address);
        this.id = id.trim();
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
    }

    public void updateData(String name, String phone, String address) {
        PersonValidator.validatePersonData(name, phone, address);
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return String.format("Vendedor [ID: %s, Nombre: %s, Teléfono: %s, Dirección: %s]", id, name, phone, address);
    }
}