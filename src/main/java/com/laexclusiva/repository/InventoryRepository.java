package com.laexclusiva.repository;

import com.laexclusiva.model.InventoryItem;
import java.util.List;
import java.util.Optional;

/** Contrato de almacenamiento de existencias (DIP: el servicio depende de esta interfaz). */
public interface InventoryRepository {

    /**
     * Persiste el total de existencias. En JDBC requiere una transacción activa.
     * Para actualizar o descontar existencias, se debe leer primero el ítem con
     * {@link #findByProductIdForUpdate(String)}, modificar el resultado y guardar
     * dentro de la misma llamada a `JdbcTransactionManager.inTransaction(...)`.
     */
    void save(InventoryItem item);

    Optional<InventoryItem> findByProductId(String productId);

    /** Lee las existencias antes de modificarlas; en JDBC adquiere el bloqueo de fila. */
    default Optional<InventoryItem> findByProductIdForUpdate(String productId) {
        return findByProductId(productId);
    }


    List<InventoryItem> findAll();
}
