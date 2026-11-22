package com.laexclusiva.repository;

import com.laexclusiva.model.PurchaseOrder;
import com.laexclusiva.model.PurchaseOrderLine;
import com.laexclusiva.model.PurchaseOrderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persiste pedidos y aplica cambios de estado y cantidades como una sola operación. */
public interface PurchaseOrderRepository {
    /**
     * Inserta la cabecera y sus líneas con IDs generados.
     * @param order cabecera nueva, en estado pendiente y sin ID
     * @param lines líneas solicitadas
     * @return pedido persistido con sus líneas
     */
    OrderWithLines save(PurchaseOrder order, List<NewLine> lines);

    /** @param id ID del pedido
     * @return pedido y líneas, si existe
     */
    Optional<OrderWithLines> findById(long id);

    /**
     * Lee el pedido para una escritura. JDBC requiere la transacción exterior y bloquea
     * cabecera y líneas hasta que termine. En memoria devuelve una instantánea cuya
     * vigencia se comprueba al actualizar.
     * @param id ID del pedido
     * @return pedido y líneas, si existe
     */
    Optional<OrderWithLines> findByIdForUpdate(long id);

    /** @return cabeceras ordenadas por ID */
    List<PurchaseOrder> findAll();

    /** @param status estado a consultar
     * @return cabeceras del estado solicitado, ordenadas por ID
     */
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    /**
     * Actualiza el estado y suma deltas de recepción, exigiendo que la instantánea siga vigente.
     * @param expected pedido y líneas leídos antes de la operación
     * @param status estado resultante
     * @param receivedDeltas unidades nuevas por ID de línea; vacío para cancelar
     * @return pedido actualizado
     * @throws java.util.ConcurrentModificationException si cambió el pedido esperado
     * @throws IllegalArgumentException si el cambio no respeta el saldo o el ciclo de vida
     */
    OrderWithLines updateStatusAndReceivedUnits(OrderWithLines expected, PurchaseOrderStatus status,
                                               Map<Long, Integer> receivedDeltas);

    /**
     * Cantidades a pedir antes de asignar los IDs.
     * @param productId ID del producto
     * @param orderedBaskets canastas completas
     * @param orderedLooseUnits unidades sueltas
     * @param unitsPerBasketSnapshot capacidad histórica de la canasta
     */
    record NewLine(String productId, int orderedBaskets, int orderedLooseUnits, int unitsPerBasketSnapshot) {
        /** Valida el producto y las cantidades solicitadas. */
        public NewLine {
            if (productId == null || productId.isBlank() || productId.length() > 64) {
                throw new IllegalArgumentException("El ID del producto debe tener entre 1 y 64 caracteres.");
            }
            PurchaseOrderLine.equivalentUnits(orderedBaskets, orderedLooseUnits, unitsPerBasketSnapshot);
        }

        /** @return total solicitado en unidades equivalentes */
        public int orderedEquivalentUnits() {
            return PurchaseOrderLine.equivalentUnits(orderedBaskets, orderedLooseUnits, unitsPerBasketSnapshot);
        }
    }

    /**
     * Instantánea inmutable de un pedido persistido.
     * @param order cabecera
     * @param lines líneas en orden de ID
     */
    record OrderWithLines(PurchaseOrder order, List<PurchaseOrderLine> lines) {
        /** Conserva una copia inmutable de las líneas. */
        public OrderWithLines {
            lines = List.copyOf(lines);
        }

        /**
         * Calcula el nuevo estado y los acumulados antes de escribir.
         * @param status estado solicitado
         * @param deltas unidades nuevas por ID de línea
         * @return instantánea con los acumulados actualizados
         * @throws IllegalArgumentException si la transición o los deltas son inválidos
         */
        public OrderWithLines applyReceipt(PurchaseOrderStatus status, Map<Long, Integer> deltas) {
            if (status == null || deltas == null || order.status() == PurchaseOrderStatus.CANCELLED
                    || order.status() == PurchaseOrderStatus.COMPLETED) {
                throw new IllegalArgumentException("El pedido o el cambio de estado no admite la operación.");
            }
            if ((status == PurchaseOrderStatus.CANCELLED) != deltas.isEmpty()) {
                throw new IllegalArgumentException("La recepción requiere cantidades y la cancelación debe omitirlas.");
            }
            for (var entry : deltas.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0
                        || lines.stream().noneMatch(line -> entry.getKey().equals(line.id()))) {
                    throw new IllegalArgumentException("Cada delta debe ser positivo y corresponder a una línea del pedido.");
                }
            }
            List<PurchaseOrderLine> updated = new ArrayList<>();
            for (PurchaseOrderLine line : lines) {
                int delta = deltas.getOrDefault(line.id(), 0);
                if (delta > line.pendingEquivalentUnits()) {
                    throw new IllegalArgumentException("La cantidad recibida supera el saldo pendiente.");
                }
                updated.add(new PurchaseOrderLine(line.id(), line.purchaseOrderId(), line.productId(),
                        line.orderedBaskets(), line.orderedLooseUnits(), line.unitsPerBasketSnapshot(),
                        line.orderedEquivalentUnits(), line.receivedEquivalentUnits() + delta));
            }
            if (status != PurchaseOrderStatus.CANCELLED) {
                boolean complete = updated.stream().allMatch(line -> line.pendingEquivalentUnits() == 0);
                if (status != (complete ? PurchaseOrderStatus.COMPLETED : PurchaseOrderStatus.PARTIAL)) {
                    throw new IllegalArgumentException("El estado solicitado no coincide con las cantidades recibidas.");
                }
            }
            return new OrderWithLines(new PurchaseOrder(order.id(), order.supplierId(), status, order.notes(),
                    order.createdByUserId(), order.createdByUsername(), order.createdAt()), updated);
        }
    }
}
