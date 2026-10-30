package com.laexclusiva.repository;

import com.laexclusiva.model.Sale;
import java.util.List;
import java.util.Optional;

/**
 * Contrato para guardar, actualizar, buscar y listar ventas.
 * Las implementaciones deben preservar la identidad de cada venta mediante su identificador.
 */
public interface SaleRepository {

    /**
     * Guarda una venta nueva.
     *
     * @param sale venta que se desea almacenar
     * @throws IllegalArgumentException si la venta es nula o su identificador ya existe
     */
    void save(Sale sale);

    /**
     * Reemplaza los datos de una venta existente con el mismo identificador.
     *
     * @param sale nuevo estado de la venta
     * @throws IllegalArgumentException si la venta o su identificador son inválidos, o si no existe
     *                                  una venta con ese identificador
     */
    void update(Sale sale);

    /**
     * Elimina la venta con el identificador indicado, si existe. El servicio usa esta operación
     * para compensar un registro que no pudo completarse.
     *
     * @param id identificador de la venta que se desea eliminar
     */
    void deleteById(String id);

    /**
     * Busca una venta por su identificador.
     *
     * @param id identificador de la venta
     * @return la venta asociada al identificador o {@link Optional#empty()} si no existe
     */
    Optional<Sale> findById(String id);

    /**
     * Devuelve todas las ventas registradas.
     *
     * @return lista con las ventas registradas
     */
    List<Sale> findAll();
}
