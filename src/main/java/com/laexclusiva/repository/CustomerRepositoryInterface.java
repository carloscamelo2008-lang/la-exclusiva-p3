package com.laexclusiva.repository;

import com.laexclusiva.model.Customer;
import java.util.List;

public interface CustomerRepositoryInterface {
    void save(Customer customer);
    Customer findById(String id);
    List<Customer> findAll();
    boolean existsById(String id);

    /**
     * Actualiza la información de un cliente existente en el repositorio.
     * 
     * @param customer El cliente con los datos actualizados.
     * @throws IllegalArgumentException si el cliente es nulo o si el ID no existe en el repositorio.
     */
    void update(Customer customer);
}