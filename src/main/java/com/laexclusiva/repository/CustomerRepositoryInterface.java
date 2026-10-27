package com.laexclusiva.repository;

import com.laexclusiva.model.Customer;
import java.util.List;
import java.util.Optional;

public interface CustomerRepositoryInterface {
    void save(Customer customer);
    Optional<Customer> findById(String id);
    List<Customer> findAll();
    boolean delete(String id);
}