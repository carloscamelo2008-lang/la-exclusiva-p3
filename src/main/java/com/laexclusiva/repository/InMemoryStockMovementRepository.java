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
    public boolean remove(StockMovement movement) {
        for (int index = storage.size() - 1; index >= 0; index--) {
            if (storage.get(index).equals(movement)) {
                storage.remove(index);
                return true;
            }
        }
        return false;
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
