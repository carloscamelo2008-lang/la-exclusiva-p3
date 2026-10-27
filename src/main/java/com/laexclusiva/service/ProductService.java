package com.laexclusiva.service;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.repository.ProductRepository;
import java.util.List;

/**
 * Casos de uso del catálogo. No imprime menús ni conoce el almacenamiento concreto.
 *
 * GRASP: Controller (coordina el caso de uso), Low Coupling (solo conoce la interfaz del
 * repositorio). SOLID: SRP y DIP.
 *
 * Métodos públicos que consumirán los demás módulos (contrato a acordar con Carlos):
 * findById, listAll, priceFor y unitsFor.
 */
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

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

    public void registerAll(List<BeerProduct> products) {
        products.forEach(this::register);
    }

    public BeerProduct findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto con id " + id + "."));
    }

    public List<BeerProduct> listAll() {
        return repository.findAll();
    }

    public long priceFor(String productId, SalePresentation presentation) {
        return findById(productId).priceFor(presentation);
    }

    public int unitsFor(String productId, SalePresentation presentation) {
        return findById(productId).unitsFor(presentation);
    }
}
