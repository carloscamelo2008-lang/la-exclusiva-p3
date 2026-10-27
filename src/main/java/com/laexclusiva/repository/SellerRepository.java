package com.laexclusiva.repository;

import com.laexclusiva.model.Seller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio en memoria para la gestión de vendedores.
 */
public class SellerRepository {

    private final Map<String, Seller> sellers = new HashMap<>();

    /**
     * Guarda o actualiza un vendedor en la memoria.
     */
    public void save(Seller seller) {
        if (seller == null) {
            throw new IllegalArgumentException("El vendedor no puede ser nulo.");
        }
        sellers.put(seller.getId(), seller);
    }

    /**
     * Busca un vendedor por su ID.
     */
    public Optional<Seller> findById(String id) {
        return Optional.ofNullable(sellers.get(id));
    }

    /**
     * Retorna todos los vendedores registrados.
     */
    public List<Seller> findAll() {
        return new ArrayList<>(sellers.values());
    }
}