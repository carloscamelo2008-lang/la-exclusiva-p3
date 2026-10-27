package com.laexclusiva.repository;

import com.laexclusiva.model.InventoryItem;
import java.util.List;
import java.util.Optional;

/** Contrato de almacenamiento de existencias (DIP: el servicio depende de esta interfaz). */
public interface InventoryRepository {

    void save(InventoryItem item);

    Optional<InventoryItem> findByProductId(String productId);

    List<InventoryItem> findAll();
}
