package com.laexclusiva.service;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Supplier;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.SupplierRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Administra el catálogo de proveedores con los permisos vigentes de inventario. */
public final class SupplierService {
    private final SupplierRepository suppliers;
    private final AuthorizationService authorization;
    private final TransactionBoundary transactions;

    /** Recibe el repositorio, la autorización y el límite de escritura del módulo. */
    public SupplierService(SupplierRepository suppliers, AuthorizationService authorization,
                           TransactionBoundary transactions) {
        this.suppliers = Objects.requireNonNull(suppliers);
        this.authorization = Objects.requireNonNull(authorization);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** Crea un proveedor activo. */
    public Supplier create(UserSession session, String name, String taxId, String contactInfo) {
        return transactions.inTransaction(() -> {
            requirePermission(authorization, session, Permission.MANAGE_INVENTORY_ENTRIES);
            return suppliers.save(new Supplier(null, name, taxId, contactInfo, true, LocalDateTime.now()));
        });
    }

    /** Consulta un proveedor por su identificador. */
    public Optional<Supplier> findById(UserSession session, long id) {
        requirePermission(authorization, session, Permission.VIEW_INVENTORY);
        return suppliers.findById(id);
    }

    /** Consulta todos los proveedores, incluidos los desactivados. */
    public List<Supplier> findAll(UserSession session) {
        requirePermission(authorization, session, Permission.VIEW_INVENTORY);
        return suppliers.findAll();
    }

    /** Consulta los proveedores admitidos para nuevos pedidos. */
    public List<Supplier> findActive(UserSession session) {
        requirePermission(authorization, session, Permission.VIEW_INVENTORY);
        return suppliers.findActive();
    }

    /** Actualiza los datos de contacto y conserva la actividad del proveedor. */
    public Supplier update(UserSession session, long id, String name, String taxId, String contactInfo) {
        return transactions.inTransaction(() -> {
            requirePermission(authorization, session, Permission.MANAGE_INVENTORY_ENTRIES);
            Supplier current = requireSupplier(id);
            return suppliers.update(new Supplier(id, name, taxId, contactInfo, current.active(), current.createdAt()));
        });
    }

    /** Activa o desactiva un proveedor conservando los datos del catálogo. */
    public Supplier setActive(UserSession session, long id, boolean active) {
        return transactions.inTransaction(() -> {
            requirePermission(authorization, session, Permission.MANAGE_INVENTORY_ENTRIES);
            Supplier current = requireSupplier(id);
            return suppliers.update(new Supplier(id, current.name(), current.taxId(), current.contactInfo(),
                    active, current.createdAt()));
        });
    }

    private Supplier requireSupplier(long id) {
        return suppliers.findById(id).orElseThrow(() -> new IllegalArgumentException("El proveedor no existe."));
    }

    /** Exige una sesión activa y el permiso de inventario solicitado. */
    static void requirePermission(AuthorizationService authorization, UserSession session, Permission permission) {
        if (session == null || !session.isActive() || !authorization.hasPermission(session, permission)) {
            throw new SecurityException("La sesión no tiene permiso para realizar esta operación de compras.");
        }
    }
}
