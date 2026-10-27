package com.laexclusiva.repository;

import com.laexclusiva.model.Seller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación en memoria del repositorio de vendedores.
 */
public class SellerRepository implements SellerRepositoryInterface {

    private final Map<String, Seller> sellers = new HashMap<>();

    @Override
    public void save(Seller seller) {
        if (seller == null) {
            throw new IllegalArgumentException("El vendedor no puede ser nulo.");
        }
        if (sellers.containsKey(seller.getId())) {
            throw new IllegalArgumentException("Ya existe un vendedor registrado con el ID: " + seller.getId());
        }
        sellers.put(seller.getId(), seller);
    }

    public void update(Seller seller) {
        if (seller == null) {
            throw new IllegalArgumentException("El vendedor no puede ser nulo.");
        }
        if (!sellers.containsKey(seller.getId())) {
            throw new IllegalArgumentException("No existe el vendedor con ID: " + seller.getId() + " para actualizar.");
        }
        sellers.put(seller.getId(), seller);
    }

    @Override
    public Optional<Seller> findById(String id) {
        return Optional.ofNullable(sellers.get(id));
    }

    @Override
    public List<Seller> findAll() {
        return new ArrayList<>(sellers.values());
    }
}