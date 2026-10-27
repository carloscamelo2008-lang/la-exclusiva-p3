package com.laexclusiva.model;

import com.laexclusiva.validation.PersonValidator;
import java.util.UUID;

/**
 * Representa a un vendedor dentro del sistema La Exclusiva.
 */
public class Seller {

    private final String id;
    private String name;
    private String phone;
    private String address;

    /**
     * Constructor para registrar un nuevo vendedor con un ID autogenerado único.
     * 
     * @param name Nombre obligatorio del vendedor.
     * @param phone Teléfono obligatorio.
     * @param address Dirección obligatoria.
     */
    public Seller(String name, String phone, String address) {
        PersonValidator.validatePersonData(name, phone, address);
        this.id = "SEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.name = name.trim();
        this.phone = phone.trim();
        this.address = address.trim();
    }

    /**
     * Constructor para reconstruir un vendedor con un ID ya existente (persistencia/pruebas).
     */
    public Seller(String id, String name, String phone, String address) {
        PersonValidator.validatePersonData(name, phone, address);
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del vendedor no puede estar vacío.");
        }
        this.id = id.trim();
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

    @Override
    public String toString() {
        return String.format("Vendedor [ID: %s | Nombre: %s | Teléfono: %s | Dirección: %s]",
                id, name, phone, address);
    }
}