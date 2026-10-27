package com.laexclusiva.repository;

import com.laexclusiva.model.Customer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación en memoria del repositorio de clientes.
 */
public class CustomerRepository implements CustomerRepositoryInterface {

    private final Map<String, Customer> customers = new HashMap<>();

    @Override
    public void save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        if (customers.containsKey(customer.getId())) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el ID: " + customer.getId());
        }
        customers.put(customer.getId(), customer);
    }

    /**
     * Permite actualizar un cliente existente de forma explícita.
     */
    public void update(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        if (!customers.containsKey(customer.getId())) {
            throw new IllegalArgumentException("No existe el cliente con ID: " + customer.getId() + " para actualizar.");
        }
        customers.put(customer.getId(), customer);
    }

    @Override
    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public List<Customer> findAll() {
        return new ArrayList<>(customers.values());
    }

    @Override
    public boolean delete(String id) {
        return customers.remove(id) != null;
    }
}