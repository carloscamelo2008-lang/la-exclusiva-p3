package com.laexclusiva.repository;

import com.laexclusiva.model.Customer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio en memoria para la gestión de clientes.
 */
public class CustomerRepository {

    private final Map<String, Customer> customers = new HashMap<>();

    /**
     * Guarda o actualiza un cliente en la memoria.
     */
    public void save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        customers.put(customer.getId(), customer);
    }

    /**
     * Busca un cliente por su identificador único.
     */
    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(customers.get(id));
    }

    /**
     * Retorna la lista completa de clientes registrados.
     */
    public List<Customer> findAll() {
        return new ArrayList<>(customers.values());
    }

    /**
     * Elimina un cliente por su ID.
     */
    public boolean delete(String id) {
        return customers.remove(id) != null;
    }
}