package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Servicio encargado de la lógica de negocio para clientes y cuentas por cobrar.
 */
public class CustomerService {

    private final CustomerRepositoryInterface customerRepository;

    public CustomerService(CustomerRepositoryInterface customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer registerCustomer(String name, String phone, String address) {
        Customer customer = new Customer(name, phone, address);
        customerRepository.save(customer);
        return customer;
    }

    public Optional<Customer> getCustomerById(String id) {
        return customerRepository.findById(id);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    /**
     * Registra un fiado (deuda) a un cliente específico.
     */
    public void addDebtToCustomer(String customerId, BigDecimal amount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado con ID: " + customerId));
        customer.addDebt(amount);
        if (customerRepository instanceof CustomerRepository) {
            ((CustomerRepository) customerRepository).update(customer);
        }
    }

    /**
     * Registra un abono a la deuda de un cliente.
     */
    public void applyPaymentToCustomer(String customerId, BigDecimal amount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado con ID: " + customerId));
        customer.applyPayment(amount);
        if (customerRepository instanceof CustomerRepository) {
            ((CustomerRepository) customerRepository).update(customer);
        }
    }
}