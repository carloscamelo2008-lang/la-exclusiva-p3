package com.laexclusiva.service;

import java.util.function.Supplier;

/**
 * Coordina una sección administrativa de identidad en el mismo hilo.
 * No aplica permisos ni la política del último administrador y no protege rutas externas.
 * Las operaciones deben usar el repositorio/proveedor asociado a la implementación.
 */
public interface UserAdministrationBoundary {
    /**
     * Ejecuta una sección protegida; en memoria no implica rollback de sus escrituras.
     * En JDBC anidado el resultado sigue siendo provisional hasta el commit exterior.
     * @param operation operación completa, incluidas sus comprobaciones y escritura
     * @param <T> tipo del resultado
     * @return resultado de la operación
     * @throws IllegalArgumentException si la operación es nula
     */
    <T> T execute(Supplier<T> operation);
}
