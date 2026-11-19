package com.laexclusiva.persistence.jdbc;

import java.util.function.Supplier;

/** Límite de transacción que permite probar los casos de uso sin abrir una conexión real. */
public interface TransactionBoundary {

    /**
     * Ejecuta una operación y devuelve su resultado solo si la transacción se confirma.
     *
     * @param operation lógica que debe ejecutarse dentro del límite transaccional
     * @param <T> tipo del resultado producido
     * @return resultado de la operación confirmada
     */
    <T> T inTransaction(Supplier<T> operation);

    /**
     * Ejecuta una lectura que necesita repetir consultas bajo una misma instantánea.
     *
     * <p>El límite JDBC debe usar aislamiento {@code REPEATABLE_READ}. Implementaciones de prueba
     * o en memoria pueden delegar a su transacción ordinaria cuando no exista concurrencia SQL.</p>
     *
     * @param operation lecturas que deben compartir una instantánea
     * @param <T> tipo del resultado
     * @return resultado leído
     * @throws IllegalArgumentException si la operación es nula y la implementación valida el argumento
     */
    default <T> T inReadOnlyRepeatableRead(Supplier<T> operation) {
        return inTransaction(operation);
    }

    /**
     * Ejecuta una operación sin resultado dentro de la misma transacción.
     *
     * @param operation lógica que debe ejecutarse atómicamente
     */
    default void inTransaction(Runnable operation) {
        if (operation == null) {
            throw new IllegalArgumentException("La operación transaccional es obligatoria.");
        }
        inTransaction(() -> {
            operation.run();
            return null;
        });
    }
}
