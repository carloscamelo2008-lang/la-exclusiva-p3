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
        PersonValidator.validateId(id);
        PersonValidator.validateName(name);
        PersonValidator.validatePhone(phone);
        PersonValidator.validateAddress(address);

        if (customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el ID: " + id);
        }

        Customer customer = new Customer(id, name, phone, address);
        customerRepository.save(customer);
    }

    public Customer getCustomerById(String id) {
        PersonValidator.validateId(id);
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
        PersonValidator.validateId(customerId);
        PersonValidator.validateAmount(amount);

        Customer customer = getCustomerById(customerId);
        customer.addDebt(amount);
        customerRepository.update(customer);
    }

    public void applyPaymentToCustomer(String customerId, BigDecimal amount) {
        PersonValidator.validateId(customerId);
        PersonValidator.validateAmount(amount);

        Customer customer = getCustomerById(customerId);
        customer.applyPayment(amount);
        customerRepository.update(customer);
    }
}