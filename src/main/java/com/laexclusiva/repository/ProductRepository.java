package com.laexclusiva.repository;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.SalePresentation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de almacenamiento de productos.
 * SOLID: DIP (el servicio depende de esta abstracción) e ISP (solo lo que el servicio necesita).
 * Hoy lo implementa una colección en memoria; mañana puede ser PostgreSQL/JDBC sin tocar el servicio.
 */
public interface ProductRepository {

    /** Inserta un producto con sus tres precios y rechaza un identificador duplicado. */
    void save(BeerProduct product);

    /** Actualiza un precio existente. Rechaza productos o precios ausentes e importes inválidos. */
    void updatePrice(String productId, SalePresentation presentation, BigDecimal newPrice);

    /** Inserta un precio faltante. Rechaza productos ausentes, precios duplicados e importes inválidos. */
    void addMissingPrice(String productId, SalePresentation presentation, BigDecimal price);

    /** Actualiza únicamente la referencia relativa de una fotografía ya validada. */
    void updateImageFilename(String productId, String imageFilename);


    Optional<BeerProduct> findById(String id);

    List<BeerProduct> findAll();

    boolean existsById(String id);
}
