package com.laexclusiva.repository;

import com.laexclusiva.model.Seller;
import java.util.List;
import java.util.Optional;

public interface SellerRepositoryInterface {
    void save(Seller seller);
    Optional<Seller> findById(String id);
    List<Seller> findAll();

    default void setActive(String id, boolean active) {
        throw new UnsupportedOperationException("El repositorio no admite cambiar el estado del vendedor.");
    }
}