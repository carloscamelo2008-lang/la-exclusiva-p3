package com.laexclusiva.repository;

import com.laexclusiva.model.Customer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerRepository implements CustomerRepositoryInterface {
    private final Map<String, Customer> customers = new HashMap<>();
    private final java.util.function.Predicate<String> hasReferences;

    public CustomerRepository() {
        this(id -> false);
    }

    /** Recibe la consulta de referencias de los repositorios que comparten el modo memoria. */
    public CustomerRepository(java.util.function.Predicate<String> hasReferences) {
        this.hasReferences = java.util.Objects.requireNonNull(hasReferences);
    }

    @Override
    public synchronized void save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        if (customers.containsKey(customer.getId())) {
            throw new IllegalArgumentException("Ya existe un cliente con el ID: " + customer.getId());
        }
        if (existsByPhone(customer.getPhone(), customer.getId())) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con ese teléfono.");
        }
        customers.put(customer.getId(), customer.copy());
    }

    @Override
    public synchronized Customer findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        Customer customer = customers.get(id);
        return customer == null ? null : customer.copy();
    }

    @Override
    public synchronized List<Customer> findAll() {
        List<Customer> copies = new ArrayList<>(customers.size());
        customers.values().forEach(customer -> copies.add(customer.copy()));
        return copies;
    }

    @Override
    public synchronized boolean existsById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        return customers.containsKey(id);
    }

    @Override
    public synchronized boolean existsByPhone(String phone, String excludedId) {
        return customers.values().stream().anyMatch(customer -> !customer.getId().equals(excludedId)
                && customer.getPhone().equals(phone));
    }

    @Override
    public synchronized void deleteById(String id) {
        Customer customer = customers.get(id);
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no existe.");
        }
        if (customer.getBalance().signum() != 0 || hasReferences.test(id)) {
            throw new com.laexclusiva.model.CustomerDeletionBlockedException();
        }
        customers.remove(id);
    }

    @Override
    public synchronized void update(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no puede ser nulo.");
        }
        if (!customers.containsKey(customer.getId())) {
            throw new IllegalArgumentException("No se puede actualizar: el cliente con ID " + customer.getId() + " no existe.");
        }
        if (existsByPhone(customer.getPhone(), customer.getId())) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con ese teléfono.");
        }
        customers.put(customer.getId(), customer.copy());
    }
}
