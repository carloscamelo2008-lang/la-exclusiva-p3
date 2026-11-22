package com.laexclusiva.service;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.GoodsReceipt;
import com.laexclusiva.model.GoodsReceiptLine;
import com.laexclusiva.model.InventoryItem;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.PurchaseOrderLine;
import com.laexclusiva.model.PurchaseOrderStatus;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.persistence.jdbc.StockMovementRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.GoodsReceiptRepository;
import com.laexclusiva.repository.InventoryStockAdjuster;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.PurchaseOrderRepository;
import com.laexclusiva.repository.PurchaseOrderRepository.OrderWithLines;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static com.laexclusiva.service.SupplierService.requirePermission;

/** Recibe mercancía y coordina el historial, los acumulados del pedido y las existencias. */
public final class GoodsReceiptService {
    private final PurchaseOrderRepository orders;
    private final GoodsReceiptRepository receipts;
    private final ProductService products;
    private final AuthorizationService authorization;
    private final TransactionBoundary transactions;
    private final InventoryStockAdjuster stock;
    private final StockMovementRepositoryJdbc jdbcMovements;
    private final InMemoryInventoryRepository memoryStock;
    private final InMemoryStockMovementRepository memoryMovements;

    /** Configura JDBC con repositorios y ajustador construidos sobre el proveedor del mismo gestor. */
    public GoodsReceiptService(PurchaseOrderRepository orders, GoodsReceiptRepository receipts,
                               ProductService products, AuthorizationService authorization,
                               TransactionBoundary transactions, InventoryStockAdjuster stock,
                               StockMovementRepositoryJdbc movements) {
        this(orders, receipts, products, authorization, transactions,
                Objects.requireNonNull(stock), Objects.requireNonNull(movements), null, null);
    }

    /** Configura memoria con la frontera serializada que comparte el servicio de pedidos. */
    public GoodsReceiptService(PurchaseOrderRepository orders, GoodsReceiptRepository receipts,
                               ProductService products, AuthorizationService authorization,
                               TransactionBoundary transactions, InMemoryInventoryRepository stock,
                               InMemoryStockMovementRepository movements) {
        this(orders, receipts, products, authorization, transactions,
                null, null, Objects.requireNonNull(stock), Objects.requireNonNull(movements));
    }

    private GoodsReceiptService(PurchaseOrderRepository orders, GoodsReceiptRepository receipts,
                                ProductService products, AuthorizationService authorization,
                                TransactionBoundary transactions, InventoryStockAdjuster stock,
                                StockMovementRepositoryJdbc jdbcMovements, InMemoryInventoryRepository memoryStock,
                                InMemoryStockMovementRepository memoryMovements) {
        this.orders = Objects.requireNonNull(orders);
        this.receipts = Objects.requireNonNull(receipts);
        this.products = Objects.requireNonNull(products);
        this.authorization = Objects.requireNonNull(authorization);
        this.transactions = Objects.requireNonNull(transactions);
        this.stock = stock;
        this.jdbcMovements = jdbcMovements;
        this.memoryStock = memoryStock;
        this.memoryMovements = memoryMovements;
    }

    /** Identifica una línea de pedido y sus cantidades físicas recibidas. */
    public record ReceiptLineRequest(long purchaseOrderLineId, int baskets, int looseUnits) {
        /** Valida la identidad y exige al menos una cantidad positiva. */
        public ReceiptLineRequest {
            if (purchaseOrderLineId <= 0 || baskets < 0 || looseUnits < 0 || (baskets == 0 && looseUnits == 0)) {
                throw new IllegalArgumentException("La línea y las cantidades de recepción son inválidas.");
            }
        }
    }

    /**
     * Consulta el historial persistido de un pedido.
     * @param session sesión con permiso para consultar inventario
     * @param orderId identificador del pedido
     * @return recepciones del pedido, ordenadas por identificador
     */
    public List<GoodsReceipt> findByPurchaseOrderId(UserSession session, long orderId) {
        requirePermission(authorization, session, Permission.VIEW_INVENTORY);
        return transactions.inTransaction(() -> receipts.findByPurchaseOrderId(orderId));
    }

    /** Indica que la clave ya identifica una recepción con otras cantidades o líneas. */
    public static final class IdempotencyConflictException extends IllegalStateException {
        private IdempotencyConflictException() {
            super("La clave de idempotencia ya corresponde a otras líneas de recepción.");
        }
    }

    /**
     * Registra una recepción parcial o completa.
     * Un reintento con las mismas líneas y cantidades devuelve la recepción original, incluso
     * si el pedido ya cerró. El orden de las líneas y las notas no intervienen en la comparación.
     *
     * @param session sesión del usuario que recibe
     * @param orderId identificador del pedido
     * @param idempotencyKey clave estable de la solicitud dentro del pedido
     * @param notes observaciones opcionales
     * @param lines cantidades por línea del pedido
     * @return recepción guardada o recuperada en un reintento
     * @throws IdempotencyConflictException si se reutiliza la clave con cantidades o líneas distintas
     */
    public GoodsReceipt receive(UserSession session, long orderId, String idempotencyKey,
                               String notes, List<ReceiptLineRequest> lines) {
        return transactions.inTransaction(() -> {
            requirePermission(authorization, session, Permission.MANAGE_INVENTORY_ENTRIES);
            if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
                throw new IllegalArgumentException("La clave de idempotencia debe tener entre 1 y 64 caracteres.");
            }
            OrderWithLines order = orders.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("El pedido no existe."));
            Map<Long, ReceiptLineRequest> requested = indexRequests(lines);
            var previous = receipts.findByOrderAndIdempotencyKey(orderId, idempotencyKey);
            if (previous.isPresent()) {
                GoodsReceipt saved = previous.get();
                if (saved.lines().size() != requested.size() || saved.lines().stream().anyMatch(line ->
                        !new ReceiptLineRequest(line.purchaseOrderLineId(), line.receivedBaskets(),
                                line.receivedLooseUnits()).equals(requested.get(line.purchaseOrderLineId())))) {
                    throw new IdempotencyConflictException();
                }
                return saved;
            }
            if (order.order().status() == PurchaseOrderStatus.CANCELLED
                    || order.order().status() == PurchaseOrderStatus.COMPLETED) {
                throw new IllegalArgumentException("El pedido está cerrado a nuevas recepciones.");
            }
            return register(session, order, idempotencyKey, notes, requested);
        });
    }

    private GoodsReceipt register(UserSession session, OrderWithLines order, String key, String notes,
                                  Map<Long, ReceiptLineRequest> requested) {
        Map<Long, Integer> deltas = new HashMap<>();
        Map<String, InventoryItem> stagedStock = new TreeMap<>();
        List<GoodsReceiptLine> details = new ArrayList<>();
        for (PurchaseOrderLine line : order.lines()) {
            ReceiptLineRequest request = requested.get(line.id());
            if (request == null) {
                continue;
            }
            BeerProduct product = products.findById(line.productId());
            int units = product.getUnitsPerBasket() == line.unitsPerBasketSnapshot()
                    ? product.equivalentUnits(request.baskets(), request.looseUnits())
                    : PurchaseOrderLine.equivalentUnits(request.baskets(), request.looseUnits(),
                            line.unitsPerBasketSnapshot());
            details.add(new GoodsReceiptLine(null, null, line, request.baskets(), request.looseUnits(), units, null));
            deltas.put(line.id(), units);
            if (memoryStock != null) {
                stagedStock.computeIfAbsent(line.productId(), id ->
                        memoryStock.findByProductId(id).orElseGet(() -> new InventoryItem(product))).add(units);
            }
        }
        if (deltas.size() != requested.size()) {
            throw new IllegalArgumentException("Todas las líneas deben pertenecer al pedido.");
        }
        boolean complete = order.lines().stream().allMatch(line ->
                line.pendingEquivalentUnits() == deltas.getOrDefault(line.id(), 0));
        PurchaseOrderStatus status = complete ? PurchaseOrderStatus.COMPLETED : PurchaseOrderStatus.PARTIAL;
        GoodsReceipt draft = new GoodsReceipt(null, order.order().id(), key, null,
                session.getUserAccount().getUsername(), notes, LocalDateTime.now(), details);
        List<StockMovement> movements = new ArrayList<>();
        if (jdbcMovements != null) {
            draft = registerJdbcMovements(draft);
        } else {
            for (GoodsReceiptLine detail : details) {
                movements.add(movement(draft, detail));
            }
        }
        GoodsReceipt saved = receipts.save(draft);
        orders.updateStatusAndReceivedUnits(order, status, deltas);
        if (memoryStock != null) {
            stagedStock.values().forEach(memoryStock::save);
            movements.forEach(memoryMovements::save);
        }
        return saved;
    }

    private GoodsReceipt registerJdbcMovements(GoodsReceipt draft) {
        List<GoodsReceiptLine> details = new ArrayList<>(draft.lines());
        // Mantiene un orden común de adquisición de bloqueos de inventario entre recepciones.
        details.sort(java.util.Comparator.comparing(GoodsReceiptLine::productId)
                .thenComparingLong(GoodsReceiptLine::purchaseOrderLineId));
        List<GoodsReceiptLine> registered = new ArrayList<>();
        for (GoodsReceiptLine detail : details) {
            stock.increaseStock(detail.productId(), detail.receivedEquivalentUnits());
            long movementId = jdbcMovements.saveAndReturnId(movement(draft, detail));
            registered.add(new GoodsReceiptLine(null, null, detail.orderLineBeforeReceipt(),
                    detail.receivedBaskets(), detail.receivedLooseUnits(), detail.receivedEquivalentUnits(), movementId));
        }
        return new GoodsReceipt(null, draft.purchaseOrderId(), draft.idempotencyKey(), draft.receivedByUserId(),
                draft.receivedByUsername(), draft.notes(), draft.receivedAt(), registered);
    }

    private static StockMovement movement(GoodsReceipt receipt, GoodsReceiptLine line) {
        return new StockMovement(line.productId(), MovementType.ENTRY, line.receivedEquivalentUnits(),
                "Recepción de mercancía del pedido " + receipt.purchaseOrderId(), receipt.receivedAt());
    }

    private static Map<Long, ReceiptLineRequest> indexRequests(List<ReceiptLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una línea de recepción.");
        }
        Map<Long, ReceiptLineRequest> requested = new HashMap<>();
        for (ReceiptLineRequest line : lines) {
            if (line == null || requested.putIfAbsent(line.purchaseOrderLineId(), line) != null) {
                throw new IllegalArgumentException("Cada línea del pedido debe aparecer una sola vez en la recepción.");
            }
        }
        return requested;
    }
}
