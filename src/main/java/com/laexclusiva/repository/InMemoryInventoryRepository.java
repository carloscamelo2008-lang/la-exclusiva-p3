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
        storage.put(item.getProduct().getId(), item.copy());
    }

    @Override
    public Optional<InventoryItem> findByProductId(String productId) {
        InventoryItem item = storage.get(productId);
        return item == null ? Optional.empty() : Optional.of(item.copy());
    }

    @Override
    public List<InventoryItem> findAll() {
        List<InventoryItem> copies = new ArrayList<>(storage.size());
        storage.values().forEach(item -> copies.add(item.copy()));
        return copies;
    }
}
