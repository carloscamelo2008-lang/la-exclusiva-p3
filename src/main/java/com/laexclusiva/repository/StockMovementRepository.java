package com.laexclusiva.repository;

import com.laexclusiva.model.StockMovement;
import java.util.List;

/**
 * Contrato del historial de movimientos. Va separado de InventoryRepository (ISP):
 * guardar existencias y guardar historial son necesidades distintas.
 */
public interface StockMovementRepository {

    void save(StockMovement movement);

    /**
     * Compensa una operación incompleta en memoria. En JDBC la compensación se
     * realiza revirtiendo la transacción.
     *
     * @throws UnsupportedOperationException si el historial no admite eliminación
     */
    default boolean remove(StockMovement movement) {
        throw new UnsupportedOperationException(
                "El historial de movimientos se revierte mediante la transacción.");
    }

    List<StockMovement> findAll();

    List<StockMovement> findByProductId(String productId);
}
