package com.laexclusiva.repository;

import com.laexclusiva.model.Sale;
import java.util.List;
import java.util.Optional;

/** Almacenamiento de ventas. */
public interface SaleRepository {

    /** Guarda una venta nueva; rechaza una venta nula o un identificador duplicado. */
    void save(Sale sale);

    /** Reemplaza explícitamente un snapshot de venta existente. */
    void update(Sale sale);

    /** Elimina una venta por ID; se usa para compensar un registro incompleto. */
    void deleteById(String id);

    /** Busca una venta por su identificador. */
    Optional<Sale> findById(String id);

    /** Devuelve las ventas registradas en orden de almacenamiento. */
    List<Sale> findAll();
}
