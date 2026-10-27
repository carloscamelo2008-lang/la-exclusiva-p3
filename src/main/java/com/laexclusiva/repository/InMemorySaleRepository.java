package com.laexclusiva.repository;

import com.laexclusiva.model.Sale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositorio en memoria que conserva el orden de registro de las ventas. */
public class InMemorySaleRepository implements SaleRepository {

    private final Map<String, Sale> storage = new LinkedHashMap<>();

    @Override
    public void save(Sale sale) {
        if (sale == null) {
            throw new IllegalArgumentException("La venta no puede ser nula.");
        }
        String id = sale.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El identificador de la venta es obligatorio.");
        }
        if (storage.containsKey(id)) {
            throw new IllegalArgumentException("Ya existe una venta con el ID: " + id);
        }
        storage.put(id, sale.copy());
    }

    @Override
    public void update(Sale sale) {
        if (sale == null || sale.getId() == null || sale.getId().isBlank()) {
            throw new IllegalArgumentException("La venta y su identificador son obligatorios.");
        }
        if (!storage.containsKey(sale.getId())) {
            throw new IllegalArgumentException("No existe una venta con el ID: " + sale.getId());
        }
        storage.put(sale.getId(), sale.copy());
    }

    @Override
    public void deleteById(String id) {
        storage.remove(id);
    }

    @Override
    public Optional<Sale> findById(String id) {
        Sale sale = storage.get(id);
        return sale == null ? Optional.empty() : Optional.of(sale.copy());
    }

    @Override
    public List<Sale> findAll() {
        List<Sale> copies = new ArrayList<>(storage.size());
        storage.values().forEach(sale -> copies.add(sale.copy()));
        return copies;
    }
}
