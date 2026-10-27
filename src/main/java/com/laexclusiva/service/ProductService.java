package com.laexclusiva.service;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;

/**
 * Casos de uso del catálogo. No imprime menús ni conoce el almacenamiento concreto.
 *
 * GRASP: Controller (coordina el caso de uso), Low Coupling (solo conoce la interfaz del
 * repositorio). SOLID: SRP y DIP.
 *
 * Contrato público para el futuro módulo de ventas:
 *   findById(id)              -> producto del catálogo (IllegalArgumentException si no existe)
 *   listAll()                 -> todas las referencias registradas
 *   priceFor(id, presentación)-> precio como BigDecimal (COP); el subtotal de una línea se calcula
 *                                con price.multiply(BigDecimal.valueOf(cantidad)), sin usar double
 *   unitsFor(id, presentación)-> unidades que descuenta esa presentación del inventario
 * Los importes monetarios son siempre BigDecimal.
 */
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /** Registra un producto. Rechaza nulos, ids duplicados y productos sin los tres precios. */
    public void register(BeerProduct product) {
        if (product == null) {
            throw new IllegalArgumentException("El producto es obligatorio.");
        }
        if (repository.existsById(product.getId())) {
            throw new IllegalArgumentException("Ya existe un producto con el id " + product.getId() + ".");
        }
        if (!product.hasAllPrices()) {
            throw new IllegalArgumentException(
                    "El producto debe tener precio de unidad, media canasta y canasta completa.");
        }
        repository.save(product);
    }

    /** Registra varios productos con las mismas reglas de {@link #register(BeerProduct)}. */
    public void registerAll(List<BeerProduct> products) {
        products.forEach(this::register);
    }

    /** @throws IllegalArgumentException si no existe un producto con ese id */
    public BeerProduct findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto con id " + id + "."));
    }

    /** Todas las referencias del catálogo. */
    public List<BeerProduct> listAll() {
        return repository.findAll();
    }

    /**
     * Precio de una presentación del producto, en COP, como BigDecimal.
     *
     * @throws IllegalArgumentException si el producto no existe o la presentación es nula
     */
    public BigDecimal priceFor(String productId, SalePresentation presentation) {
        return findById(productId).priceFor(presentation);
    }

    /**
     * Unidades que se descuentan del inventario al vender la presentación indicada.
     *
     * @throws IllegalArgumentException si el producto no existe o la presentación es nula
     */
    public int unitsFor(String productId, SalePresentation presentation) {
        return findById(productId).unitsFor(presentation);
    }
}
