package com.laexclusiva.repository;

import com.laexclusiva.model.StockMovement;
import java.util.ArrayList;
import java.util.List;

/** Historial de movimientos en memoria, en orden de registro. */
public class InMemoryStockMovementRepository implements StockMovementRepository {

    private final List<StockMovement> storage = new ArrayList<>();

    @Override
    public void save(StockMovement movement) {
        storage.add(movement);
    }

    @Override
    public List<StockMovement> findAll() {
        return new ArrayList<>(storage);
    }

    @Override
    public List<StockMovement> findByProductId(String productId) {
        return storage.stream().filter(m -> m.productId().equals(productId)).toList();
    }
}
