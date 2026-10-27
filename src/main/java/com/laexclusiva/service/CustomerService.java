package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.validation.PersonValidator;
import java.math.BigDecimal;
import java.util.List;

public class CustomerService {
    private final CustomerRepositoryInterface customerRepository;

    public CustomerService(CustomerRepositoryInterface customerRepository) {
        if (customerRepository == null) {
            throw new IllegalArgumentException("El repositorio de clientes no puede ser nulo.");
        }
        this.customerRepository = customerRepository;
    }

    public void registerCustomer(String id, String name, String phone, String address) {
        // Usamos las validaciones originales que ya tenías en tu clase PersonValidator
        PersonValidator.validatePerson(id, name, phone, address);

        if (customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el ID: " + id);
        }

        // Constructor original de Customer (ajustado a los parámetros que acepta tu modelo)
        Customer customer = new Customer(id, name, phone, address, BigDecimal.ZERO);
        customerRepository.save(customer);
    }

    public Customer getCustomerById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        Customer customer = customerRepository.findById(id);
        if (customer == null) {
            throw new IllegalArgumentException("No se encontró el cliente con ID: " + id);
        }
        return customer;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public void addDebtToCustomer(String customerId, BigDecimal amount) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }

        Customer customer = getCustomerById(customerId);
        customer.addDebt(amount);
        customerRepository.update(customer);
    }

    public void applyPaymentToCustomer(String customerId, BigDecimal amount) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }

        Customer customer = getCustomerById(customerId);
        customer.applyPayment(amount);
        customerRepository.update(customer);
    }
}