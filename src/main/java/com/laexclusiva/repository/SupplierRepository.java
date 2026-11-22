package com.laexclusiva.repository;

import com.laexclusiva.model.Supplier;
import java.util.List;
import java.util.Optional;

/** Persiste y consulta proveedores. */
public interface SupplierRepository {
    /**
     * Inserta un proveedor nuevo.
     * @param supplier proveedor sin ID
     * @return proveedor con el ID asignado
     */
    Supplier save(Supplier supplier);

    /** @param id ID del proveedor
     * @return proveedor encontrado, si existe
     */
    Optional<Supplier> findById(long id);

    /** @return proveedores ordenados por ID */
    List<Supplier> findAll();

    /** @return proveedores habilitados, ordenados por ID */
    List<Supplier> findActive();

    /**
     * Actualiza un proveedor existente conservando su fecha de creación.
     * @param supplier proveedor con ID
     * @return proveedor actualizado
     * @throws IllegalArgumentException si el ID falta o no existe
     */
    Supplier update(Supplier supplier);
}
