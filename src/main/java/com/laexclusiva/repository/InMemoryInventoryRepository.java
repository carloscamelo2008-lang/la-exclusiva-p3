package com.laexclusiva.repository;

import com.laexclusiva.model.InventoryItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Existencias en memoria, indexadas por id de producto (se pierden al cerrar el programa). */
public class InMemoryInventoryRepository implements InventoryRepository {

    private final Map<String, InventoryItem> storage = new LinkedHashMap<>();

    @Override
    public void save(InventoryItem item) {
        storage.put(item.getProduct().getId(), item);
    }

    @Override
    public Optional<InventoryItem> findByProductId(String productId) {
        return Optional.ofNullable(storage.get(productId));
    }

    @Override
    public List<InventoryItem> findAll() {
        return new ArrayList<>(storage.values());
    }
}
