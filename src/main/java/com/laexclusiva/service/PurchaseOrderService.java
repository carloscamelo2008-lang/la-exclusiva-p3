package com.laexclusiva.service;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.PurchaseOrder;
import com.laexclusiva.model.PurchaseOrderStatus;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.PurchaseOrderRepository;
import com.laexclusiva.repository.PurchaseOrderRepository.NewLine;
import com.laexclusiva.repository.PurchaseOrderRepository.OrderWithLines;
import com.laexclusiva.repository.SupplierRepository;
import com.laexclusiva.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.laexclusiva.service.SupplierService.requirePermission;

/** Crea, consulta y cancela pedidos de compra. */
public final class PurchaseOrderService {
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductService products;
    private final AuthorizationService authorization;
    private final TransactionBoundary transactions;
    private final UserRepository auditUsers;

    /** Recibe los repositorios, el catálogo y el límite transaccional del módulo. */
    public PurchaseOrderService(PurchaseOrderRepository orders, SupplierRepository suppliers,
                                ProductService products, AuthorizationService authorization,
                                TransactionBoundary transactions) {
        this(orders, suppliers, products, authorization, transactions, null);
    }

    /**
     * Configura la identidad persistida que se guardará como autoría histórica de cada pedido.
     * @param orders repositorio transaccional de pedidos
     * @param suppliers repositorio de proveedores
     * @param products catálogo compartido con inventario y ventas
     * @param authorization autoridad que valida la sesión y el permiso vigente
     * @param transactions frontera que agrupa las escrituras del pedido
     * @param auditUsers repositorio compartido para resolver el usuario persistido de auditoría
     */
    public PurchaseOrderService(PurchaseOrderRepository orders, SupplierRepository suppliers,
                                ProductService products, AuthorizationService authorization,
                                TransactionBoundary transactions, UserRepository auditUsers) {
        this.orders = Objects.requireNonNull(orders);
        this.suppliers = Objects.requireNonNull(suppliers);
        this.products = Objects.requireNonNull(products);
        this.authorization = Objects.requireNonNull(authorization);
        this.transactions = Objects.requireNonNull(transactions);
        this.auditUsers = auditUsers;
    }

    /** Cantidad solicitada de una referencia en canastas completas y unidades sueltas. */
    public record OrderLineRequest(String productId, int baskets, int looseUnits) {}

    /** Crea un pedido pendiente para un proveedor activo y conserva la capacidad histórica por línea. */
    public OrderWithLines create(UserSession session, long supplierId, String notes, List<OrderLineRequest> lines) {
        return transactions.inTransaction(() -> {
            requirePermission(authorization, session, Permission.MANAGE_INVENTORY_ENTRIES);
            if (suppliers.findById(supplierId).filter(com.laexclusiva.model.Supplier::active).isEmpty()) {
                throw new IllegalArgumentException("Se requiere un proveedor activo.");
            }
            if (lines == null || lines.isEmpty() || lines.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Se requiere al menos una línea de pedido válida.");
            }
            List<NewLine> requested = lines.stream().map(line -> {
                BeerProduct product = products.findById(line.productId());
                return new NewLine(product.getId(), line.baskets(), line.looseUnits(), product.getUnitsPerBasket());
            }).toList();
            PurchaseActorResolver.Actor actor = PurchaseActorResolver.resolve(authorization, auditUsers, session);
            return orders.save(new PurchaseOrder(null, supplierId, PurchaseOrderStatus.PENDING, notes,
                    actor.userId(), actor.username(), LocalDateTime.now()), requested);
        });
    }

    /** Consulta un pedido con sus líneas. */
    public Optional<OrderWithLines> findById(UserSession session, long id) {
        requirePermission(authorization, session, Permission.VIEW_INVENTORY);
        return orders.findById(id);
    }

    /** Consulta las cabeceras de todos los pedidos. */
    public List<PurchaseOrder> findAll(UserSession session) {
        requirePermission(authorization, session, Permission.VIEW_INVENTORY);
        return orders.findAll();
    }

    /** Consulta las cabeceras de los pedidos en el estado solicitado. */
    public List<PurchaseOrder> findByStatus(UserSession session, PurchaseOrderStatus status) {
        requirePermission(authorization, session, Permission.VIEW_INVENTORY);
        return orders.findByStatus(status);
    }

    /** Cierra un pedido pendiente o parcial y conserva todas las entradas de inventario previas. */
    public OrderWithLines cancel(UserSession session, long id) {
        return transactions.inTransaction(() -> {
            requirePermission(authorization, session, Permission.MANAGE_INVENTORY_ENTRIES);
            OrderWithLines order = orders.findByIdForUpdate(id)
                    .orElseThrow(() -> new IllegalArgumentException("El pedido no existe."));
            if (order.order().status() == PurchaseOrderStatus.CANCELLED) {
                return order;
            }
            return orders.updateStatusAndReceivedUnits(order, PurchaseOrderStatus.CANCELLED, Map.of());
        });
    }
}
