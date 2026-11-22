package com.laexclusiva.repository;

import com.laexclusiva.model.GoodsReceipt;
import java.util.List;
import java.util.Optional;

/** Persiste recepciones con clave de idempotencia por pedido. */
public interface GoodsReceiptRepository {
    /**
     * Inserta una recepción nueva y todos sus detalles antes de actualizar el pedido.
     * El caso de uso coordina esta escritura con los acumulados y el inventario.
     * @param receipt recepción sin ID
     * @return recepción y detalles con IDs asignados
     * @throws IllegalStateException si la clave ya está registrada en ese pedido
     */
    GoodsReceipt save(GoodsReceipt receipt);

    /** @param id ID de la recepción
     * @return recepción encontrada, si existe
     */
    Optional<GoodsReceipt> findById(long id);

    /** @param purchaseOrderId ID del pedido
     * @return recepciones del pedido ordenadas por ID
     */
    List<GoodsReceipt> findByPurchaseOrderId(long purchaseOrderId);

    /** @param purchaseOrderId ID del pedido
     * @param idempotencyKey clave de la solicitud
     * @return recepción previa, si existe
     */
    Optional<GoodsReceipt> findByOrderAndIdempotencyKey(long purchaseOrderId, String idempotencyKey);
}
