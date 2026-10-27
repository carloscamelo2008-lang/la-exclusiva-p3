package com.laexclusiva.repository;

import com.laexclusiva.model.BeerProduct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Implementación en memoria (los datos se pierden al cerrar el programa). */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, BeerProduct> storage = new LinkedHashMap<>();

    @Override
    public void save(BeerProduct product) {
        storage.put(product.getId(), product);
    }

    @Override
    public Optional<BeerProduct> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<BeerProduct> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }
}
