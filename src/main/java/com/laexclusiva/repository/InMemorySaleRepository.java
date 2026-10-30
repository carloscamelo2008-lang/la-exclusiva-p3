package com.laexclusiva.repository;

import com.laexclusiva.model.Sale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación en memoria de {@link SaleRepository}.
 * Conserva el orden de registro y almacena copias de las ventas para aislar el estado interno de
 * los objetos que entregan los llamadores. Los datos se pierden al finalizar el proceso.
 */
public class InMemorySaleRepository implements SaleRepository {

    private final Map<String, Sale> storage = new LinkedHashMap<>();

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void deleteById(String id) {
        storage.remove(id);
    }

    /**
     * {@inheritDoc}
     *
     * @return una copia independiente de la venta o un resultado vacío si el ID no está almacenado
     */
    @Override
    public Optional<Sale> findById(String id) {
        Sale sale = storage.get(id);
        return sale == null ? Optional.empty() : Optional.of(sale.copy());
    }

    /**
     * {@inheritDoc}
     *
     * @return copias independientes en el orden en que se registraron las ventas
     */
    @Override
    public List<Sale> findAll() {
        List<Sale> copies = new ArrayList<>(storage.size());
        storage.values().forEach(sale -> copies.add(sale.copy()));
        return copies;
    }
}
