package com.laexclusiva.repository;

import com.laexclusiva.model.StockMovement;
import java.util.List;

/**
 * Contrato del historial de movimientos. Va separado de InventoryRepository (ISP):
 * guardar existencias y guardar historial son necesidades distintas.
 */
public interface StockMovementRepository {

    void save(StockMovement movement);

    List<StockMovement> findAll();

    List<StockMovement> findByProductId(String productId);
}
