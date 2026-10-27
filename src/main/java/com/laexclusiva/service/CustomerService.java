package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.repository.CustomerRepository;
import java.util.List;
import java.util.Optional;

/**
 * Servicio encargado de la lógica de negocio para clientes y cuentas por cobrar (fiados y abonos).
 */
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Registra un nuevo cliente validando sus datos y lo guarda en memoria.
     */
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
    public void addDebtToCustomer(String customerId, double amount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado con ID: " + customerId));
        customer.addDebt(amount);
        customerRepository.save(customer);
    }

    /**
     * Registra un abono o pago parcial a la deuda de un cliente.
     */
    public void applyPaymentToCustomer(String customerId, double amount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado con ID: " + customerId));
        customer.applyPayment(amount);
        customerRepository.save(customer);
    }
}