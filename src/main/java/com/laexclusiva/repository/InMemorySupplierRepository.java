package com.laexclusiva.repository;

import com.laexclusiva.model.Supplier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Almacén de proveedores sincronizado y aislado por instancia. */
public final class InMemorySupplierRepository implements SupplierRepository {
    private final Map<Long, Supplier> suppliers = new LinkedHashMap<>();
    private long nextId = 1;

    /** {@inheritDoc} */
    @Override
    public synchronized Supplier save(Supplier supplier) {
        if (supplier == null || supplier.id() != null) {
            throw new IllegalArgumentException("Se requiere un proveedor nuevo sin ID.");
        }
        Supplier saved = new Supplier(nextId++, supplier.name(), supplier.taxId(), supplier.contactInfo(),
                supplier.active(), supplier.createdAt());
        suppliers.put(saved.id(), saved);
        return saved;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Optional<Supplier> findById(long id) {
        return Optional.ofNullable(suppliers.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<Supplier> findAll() {
        return List.copyOf(suppliers.values());
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<Supplier> findActive() {
        return suppliers.values().stream().filter(Supplier::active).toList();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Supplier update(Supplier supplier) {
        Supplier current = supplier == null ? null : suppliers.get(supplier.id());
        if (current == null) {
            throw new IllegalArgumentException("El proveedor indicado no existe.");
        }
        Supplier saved = new Supplier(current.id(), supplier.name(), supplier.taxId(), supplier.contactInfo(),
                supplier.active(), current.createdAt());
        suppliers.put(saved.id(), saved);
        return saved;
    }
}
