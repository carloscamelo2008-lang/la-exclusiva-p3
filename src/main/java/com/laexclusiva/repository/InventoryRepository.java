package com.laexclusiva.repository;

import com.laexclusiva.model.InventoryItem;
import java.util.List;
import java.util.Optional;

/** Contrato de almacenamiento de existencias (DIP: el servicio depende de esta interfaz). */
public interface InventoryRepository {

    /**
     * Persiste una fila inicial de inventario.
     * Las implementaciones JDBC rechazan sobrescribir filas existentes: use
     * {@link InventoryStockAdjuster} para sumar o descontar unidades sin snapshots obsoletos.
     */
    void save(InventoryItem item);

    Optional<InventoryItem> findByProductId(String productId);

    /** Los adaptadores con bloqueo deben exigir una transacción activa. */
    default Optional<InventoryItem> findByProductIdForUpdate(String productId) {
        return findByProductId(productId);
    }


    List<InventoryItem> findAll();
}
