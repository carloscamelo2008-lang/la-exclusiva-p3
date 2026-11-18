package com.laexclusiva.repository;

import com.laexclusiva.model.InventoryItem;
import java.util.List;
import java.util.Optional;

/** Contrato de almacenamiento de existencias (DIP: el servicio depende de esta interfaz). */
public interface InventoryRepository {

    /**
     * Persiste la fila inicial de inventario. En JDBC requiere una transacción activa
     * y rechaza sobrescribir filas existentes; para cambios posteriores use
     * {@link InventoryStockAdjuster}, que aplica deltas sin reutilizar snapshots.
     */
    void save(InventoryItem item);

    Optional<InventoryItem> findByProductId(String productId);

    /** Lee las existencias antes de modificarlas; en JDBC adquiere el bloqueo de fila. */
    default Optional<InventoryItem> findByProductIdForUpdate(String productId) {
        return findByProductId(productId);
    }


    List<InventoryItem> findAll();
}
