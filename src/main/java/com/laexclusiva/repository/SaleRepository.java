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
     * Obtiene el cliente asociado sin exigir que el llamador cargue un snapshot financiero completo.
     * La implementación JDBC consulta solo la clave foránea antes de bloquear la fila del cliente.
     *
     * @param saleId identificador de la venta
     * @return identificador del cliente, o {@code null} si la venta no tiene cliente
     * @throws IllegalArgumentException si la venta no existe o el ID es inválido
     */
    default String findCustomerIdBySaleId(String saleId) {
        Sale sale = findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No existe una venta con el ID: " + saleId));
        return sale.getCustomer() == null ? null : sale.getCustomer().getId();
    }

    /**
     * Devuelve todas las ventas registradas.
     *
     * @return lista con las ventas registradas
     */
    List<Sale> findAll();
}
