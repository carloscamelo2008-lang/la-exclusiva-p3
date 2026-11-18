package com.laexclusiva.repository;

import com.laexclusiva.model.Customer;
import java.util.List;

public interface CustomerRepositoryInterface {
    void save(Customer customer);
    Customer findById(String id);
    List<Customer> findAll();
    boolean existsById(String id);

    /**
     * Actualiza un cliente existente.
     *
     * <p>Las implementaciones en memoria reemplazan el snapshot completo. La implementación JDBC
     * actualiza solo los datos personales y exige que el saldo persistido todavía coincida con el
     * saldo del snapshot; no modifica el saldo. Los ajustes financieros concurrentes deben usar
     * {@link CustomerBalanceAdjuster}.</p>
     *
     * @param customer snapshot del cliente con sus datos actualizados
     * @throws IllegalArgumentException si el cliente es nulo o el ID no existe
     * @throws java.util.ConcurrentModificationException si la implementación JDBC detecta que el
     *         saldo cambió desde que se obtuvo el snapshot
     */
    void update(Customer customer);
}
