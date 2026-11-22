package com.laexclusiva.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Recepción de mercancía de un pedido, con su clave de reintento y datos de auditoría.
 *
 * @param id identificador positivo, o nulo antes de persistir
 * @param purchaseOrderId identificador positivo del pedido
 * @param idempotencyKey clave de reintento única dentro del pedido
 * @param receivedByUserId identificador positivo de la cuenta persistida, si está disponible
 * @param receivedByUsername nombre de usuario registrado al recibir la mercancía
 * @param notes notas opcionales
 * @param receivedAt fecha y hora de la recepción
 * @param lines detalles de la recepción, con una entrada por línea de pedido
 */
public record GoodsReceipt(Long id, long purchaseOrderId, String idempotencyKey,
                           Long receivedByUserId, String receivedByUsername, String notes,
                           LocalDateTime receivedAt, List<GoodsReceiptLine> lines) {
    /**
     * Construye una recepción inmutable y verifica que sus detalles correspondan al mismo pedido.
     *
     * @throws IllegalArgumentException si la cabecera es inválida, faltan detalles, un detalle
     *         pertenece a otro pedido o recepción, o hay una línea de pedido repetida
     */
    public GoodsReceipt {
        if ((id != null && id <= 0) || purchaseOrderId <= 0 || (receivedByUserId != null && receivedByUserId <= 0)) {
            throw new IllegalArgumentException("Los identificadores de la recepción deben ser positivos.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("La clave de idempotencia debe tener entre 1 y 64 caracteres.");
        }
        if (receivedByUsername == null || receivedByUsername.isBlank() || receivedByUsername.length() > 80) {
            throw new IllegalArgumentException("El nombre del usuario receptor debe tener entre 1 y 80 caracteres.");
        }
        if (receivedAt == null || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La recepción requiere fecha y al menos un detalle.");
        }
        Set<Long> orderLineIds = new HashSet<>();
        for (GoodsReceiptLine line : lines) {
            if (line == null || line.orderLineBeforeReceipt().purchaseOrderId() != purchaseOrderId
                    || !Objects.equals(id, line.goodsReceiptId())) {
                throw new IllegalArgumentException("Todos los detalles deben pertenecer al pedido y a la recepción.");
            }
            if (!orderLineIds.add(line.purchaseOrderLineId())) {
                throw new IllegalArgumentException("La recepción contiene una línea de pedido repetida.");
            }
        }
        lines = List.copyOf(lines);
    }
}
