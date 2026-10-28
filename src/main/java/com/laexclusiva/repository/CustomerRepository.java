package com.laexclusiva.repository;

import com.laexclusiva.model.Customer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerRepository implements CustomerRepositoryInterface {
    private final Map<String, Customer> customers = new HashMap<>();

    @Override
    public void save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        if (customers.containsKey(customer.getId())) {
            throw new IllegalArgumentException("Ya existe un cliente con el ID: " + customer.getId());
        }
        customers.put(customer.getId(), customer.copy());
    }

    @Override
    public Customer findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        Customer customer = customers.get(id);
        return customer == null ? null : customer.copy();
    }

    @Override
    public List<Customer> findAll() {
        List<Customer> copies = new ArrayList<>(customers.size());
        customers.values().forEach(customer -> copies.add(customer.copy()));
        return copies;
    }

    @Override
    public boolean existsById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        return customers.containsKey(id);
    }

    @Override
    public void update(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        if (!customers.containsKey(customer.getId())) {
            throw new IllegalArgumentException("No se puede actualizar: el cliente con ID " + customer.getId() + " no existe.");
        }
        customers.put(customer.getId(), customer.copy());
    }
}
