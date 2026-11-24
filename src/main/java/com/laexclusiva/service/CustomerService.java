package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerService {
    private final CustomerRepositoryInterface customerRepository;
    private final Map<String, BigDecimal> saleDebts = new HashMap<>();

    public CustomerService(CustomerRepositoryInterface customerRepository) {
        if (customerRepository == null) {
            throw new IllegalArgumentException("El repositorio de clientes no puede ser nulo.");
        }
        this.customerRepository = customerRepository;
    }

    public void registerCustomer(String id, String name, String phone, String address) {
        registerCustomer(id, name, phone, address, BigDecimal.ZERO);
    }

    public void registerCustomer(String id, String name, String phone, String address, BigDecimal creditLimit) {
        id = Customer.normalizeText(id);
        name = Customer.normalizeText(name);
        address = Customer.normalizeText(address);
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede ser nulo ni vacío.");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("El teléfono no puede ser nulo ni vacío.");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("La dirección no puede ser nula ni vacía.");
        }
        if (customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con el ID: " + id);
        }

        Customer customer = new Customer(id, name, phone, address, BigDecimal.ZERO, creditLimit);
        requireAvailablePhone(customer.getPhone(), null);
        customerRepository.save(customer);
    }

    /** Actualiza los datos de contacto conservando la identidad y el saldo del cliente. */
    public void updateCustomer(String id, String name, String phone, String address) {
        synchronized (customerRepository) {
            Customer current = loadCustomer(id);
            updateContact(current, name, phone, address, current.getCreditLimit());
        }
    }

    /** Actualiza los datos personales y el cupo sin modificar el saldo. */
    public void updateCustomer(String id, String name, String phone, String address, BigDecimal creditLimit) {
        synchronized (customerRepository) {
            updateContact(loadCustomer(id), name, phone, address, creditLimit);
        }
    }

    private void updateContact(Customer current, String name, String phone, String address, BigDecimal creditLimit) {
        Customer updated = Customer.reconstitute(current.getId(), Customer.normalizeText(name), phone,
                Customer.normalizeText(address), current.getBalance(), creditLimit);
        requireAvailablePhone(updated.getPhone(), current.getId());
        customerRepository.update(updated);
    }

    /** Elimina un cliente existente cuando el repositorio confirma que no tiene referencias ni saldo. */
    public void deleteCustomer(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El ID del cliente es obligatorio.");
        }
        customerRepository.deleteById(loadCustomer(id).getId());
    }

    /** Devuelve coincidencias consultivas de nombre para confirmar un alta. */
    public List<Customer> findSimilarNames(String name) {
        return customerRepository.findSimilarNames(name);
    }

    private void requireAvailablePhone(String phone, String excludedId) {
        if (customerRepository.existsByPhone(phone, excludedId)) {
            throw new IllegalArgumentException("Ya existe un cliente registrado con ese teléfono.");
        }
    }

    /** Devuelve un snapshot: modificarlo no cambia el cliente almacenado. */
    public Customer getCustomerById(String id) {
        return loadCustomer(id);
    }

    /** Devuelve snapshots independientes de todos los clientes. */
    public List<Customer> getAllCustomers() {
        List<Customer> copies = new ArrayList<>();
        customerRepository.findAll().forEach(customer -> copies.add(customer.copy()));
        return copies;
    }

    /** Agrega una deuda general, no asignada a una venta específica. */
    public void addDebtToCustomer(String customerId, BigDecimal amount) {
        requirePositiveAmount(customerId, amount, "El monto");
        Customer current = loadCustomer(customerId);
        requireAllocatedSaleDebtIsCovered(customerId, current);
        Customer updated = current.copy();
        updated.addDebt(amount);
        persistWithRollback(current, updated);
    }

    /**
     * Aplica un abono a deuda general del cliente. Si ya no queda deuda general,
     * se rechaza el abono para no reducir silenciosamente el saldo de ventas.
     */
    public void applyPaymentToCustomer(String customerId, BigDecimal amount) {
        requirePositiveAmount(customerId, amount, "El monto del abono");
        Customer current = loadCustomer(customerId);
        BigDecimal saleDebt = saleDebts.getOrDefault(customerId, BigDecimal.ZERO);
        BigDecimal generalDebt = current.getBalance().subtract(saleDebt);
        if (generalDebt.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("La deuda de ventas registrada supera el saldo del cliente.");
        }
        if (amount.compareTo(generalDebt) > 0) {
            throw new IllegalArgumentException(
                    "El abono general no puede superar la deuda no asociada a ventas del cliente.");
        }
        Customer updated = current.copy();
        updated.applyPayment(amount);
        persistWithRollback(current, updated);
    }

    /** Agrega al saldo la deuda pendiente de una venta y la asigna a ventas. */
    public void addSaleDebtToCustomer(String customerId, BigDecimal amount) {
        requirePositiveAmount(customerId, amount, "El monto de la deuda");
        Customer current = loadCustomer(customerId);
        BigDecimal currentSaleDebt = saleDebts.getOrDefault(customerId, BigDecimal.ZERO);
        requireAllocatedSaleDebtIsCovered(customerId, current);
        BigDecimal newSaleDebt = currentSaleDebt.add(amount);
        Customer updated = current.copy();
        updated.addDebt(amount);
        persistWithRollback(current, updated);
        saleDebts.put(customerId, newSaleDebt);
    }

    /** Reduce por igual el saldo agregado y la deuda asignada a ventas. */
    public void applyPaymentToSaleDebt(String customerId, BigDecimal amount) {
        requirePositiveAmount(customerId, amount, "El monto del abono");
        Customer current = loadCustomer(customerId);
        BigDecimal saleDebt = saleDebts.getOrDefault(customerId, BigDecimal.ZERO);
        if (saleDebt.compareTo(current.getBalance()) > 0) {
            throw new IllegalStateException("La deuda de ventas registrada supera el saldo del cliente.");
        }
        if (amount.compareTo(saleDebt) > 0) {
            throw new IllegalArgumentException("El abono supera la deuda pendiente asociada a ventas.");
        }
        Customer updated = current.copy();
        updated.applyPayment(amount);
        persistWithRollback(current, updated);
        saleDebts.put(customerId, saleDebt.subtract(amount));
    }

    SaleDebtSnapshot snapshotSaleDebtState(String customerId) {
        Customer customer = loadCustomer(customerId);
        requireAllocatedSaleDebtIsCovered(customerId, customer);
        return new SaleDebtSnapshot(customerId, customer.getBalance(),
                saleDebts.getOrDefault(customerId, BigDecimal.ZERO));
    }

    /** Restores the exact pre-operation aggregate and sales allocation during compensation. */
    void restoreSaleDebtState(SaleDebtSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("El snapshot de deuda es obligatorio.");
        }
        Customer current = loadCustomer(snapshot.customerId());
        Customer restored = withBalance(current, snapshot.balance());
        customerRepository.update(restored);
        saleDebts.put(snapshot.customerId(), snapshot.saleDebt());
    }

    BigDecimal saleDebtBalance(String customerId) {
        loadCustomer(customerId);
        return saleDebts.getOrDefault(customerId, BigDecimal.ZERO);
    }

    private Customer loadCustomer(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        Customer customer = customerRepository.findById(id);
        if (customer == null && !id.equals(Customer.normalizeText(id))) {
            customer = customerRepository.findById(Customer.normalizeText(id));
        }
        if (customer == null) {
            throw new IllegalArgumentException("No se encontró el cliente con ID: " + id);
        }
        return customer.copy();
    }

    private void persistWithRollback(Customer before, Customer after) {
        try {
            customerRepository.update(after.copy());
        } catch (RuntimeException | Error failure) {
            try {
                customerRepository.update(before.copy());
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static Customer withBalance(Customer customer, BigDecimal balance) {
        return Customer.reconstitute(customer.getId(), customer.getName(), customer.getPhone(),
                customer.getAddress(), balance, customer.getCreditLimit());
    }

    private void requireAllocatedSaleDebtIsCovered(String customerId, Customer customer) {
        if (saleDebts.getOrDefault(customerId, BigDecimal.ZERO)
                .compareTo(customer.getBalance()) > 0) {
            throw new IllegalStateException("La deuda de ventas registrada supera el saldo del cliente.");
        }
    }

    private static void requirePositiveAmount(String customerId, BigDecimal amount, String label) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID no puede ser nulo ni vacío.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + " debe ser mayor a cero.");
        }
    }

    record SaleDebtSnapshot(String customerId, BigDecimal balance, BigDecimal saleDebt) { }
}
