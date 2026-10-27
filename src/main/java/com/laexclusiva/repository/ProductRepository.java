package com.laexclusiva.repository;

import com.laexclusiva.model.BeerProduct;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de almacenamiento de productos.
 * SOLID: DIP (el servicio depende de esta abstracción) e ISP (solo lo que el servicio necesita).
 * Hoy lo implementa una colección en memoria; mañana puede ser PostgreSQL/JDBC sin tocar el servicio.
 */
public interface ProductRepository {

    void save(BeerProduct product);

    Optional<BeerProduct> findById(String id);

    List<BeerProduct> findAll();

    boolean existsById(String id);
}
