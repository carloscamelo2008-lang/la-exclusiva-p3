package com.laexclusiva.repository;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.SalePresentation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Implementación en memoria (los datos se pierden al cerrar el programa). */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, BeerProduct> storage = new LinkedHashMap<>();

    @Override
    public void save(BeerProduct product) {
        if (product == null || !product.hasAllPrices()) {
            throw new IllegalArgumentException("Se requiere un producto con sus tres precios.");
        }
        if (storage.putIfAbsent(product.getId(), product.copy()) != null) {
            throw new IllegalArgumentException("Ya existe un producto con el id " + product.getId() + ".");
        }
    }

    @Override
    public void updatePrice(String productId, SalePresentation presentation, BigDecimal newPrice) {
        BeerProduct product = requireProduct(productId);
        SalePresentation supported = requirePresentation(presentation);
        if (!product.hasPrice(supported)) {
            throw new IllegalArgumentException("No existe el precio para " + supported.code() + ".");
        }
        product.definePrice(supported, newPrice);
    }

    @Override
    public void addMissingPrice(String productId, SalePresentation presentation, BigDecimal price) {
        BeerProduct product = requireProduct(productId);
        SalePresentation supported = requirePresentation(presentation);
        if (product.hasPrice(supported)) {
            throw new IllegalArgumentException("Ya existe un precio para " + supported.code() + ".");
        }
        product.definePrice(supported, price);
    }

    @Override
    public void updateImageFilename(String productId, String imageFilename) {
        BeerProduct product = requireProduct(productId);
        product.setImageFilename(imageFilename);
    }

    private BeerProduct requireProduct(String productId) {
        BeerProduct product = storage.get(productId);
        if (product == null) {
            throw new IllegalArgumentException("No existe el producto con el id " + productId + ".");
        }
        return product;
    }

    private static SalePresentation requirePresentation(SalePresentation presentation) {
        if (presentation == null) {
            throw new IllegalArgumentException("La presentación es obligatoria.");
        }
        return SalePresentation.fromCode(presentation.code());
    }

    @Override
    public Optional<BeerProduct> findById(String id) {
        return Optional.ofNullable(storage.get(id)).map(BeerProduct::copy);
    }

    @Override
    public List<BeerProduct> findAll() {
        List<BeerProduct> products = new ArrayList<>(storage.size());
        storage.values().forEach(product -> products.add(product.copy()));
        return products;
    }

    @Override
    public boolean existsById(String id) {
        return storage.containsKey(id);
    }
}
