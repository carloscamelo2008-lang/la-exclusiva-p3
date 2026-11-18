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
