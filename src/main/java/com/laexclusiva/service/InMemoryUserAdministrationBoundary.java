package com.laexclusiva.service;

import com.laexclusiva.repository.InMemoryUserRepository;
import java.util.function.Supplier;

/**
 * Serializa secciones completas sobre el monitor del repositorio recibido.
 * Distintas fronteras del mismo repositorio comparten el bloqueo reentrante.
 * No revierte escrituras si la operación falla ni protege protocolos que la omitan.
 */
public final class InMemoryUserAdministrationBoundary implements UserAdministrationBoundary {
    private final InMemoryUserRepository users;

    /** @param users instancia compartida; no puede ser nula */
    public InMemoryUserAdministrationBoundary(InMemoryUserRepository users) {
        if (users == null) throw new IllegalArgumentException("El repositorio de usuarios es obligatorio.");
        this.users = users;
    }

    @Override
    public <T> T execute(Supplier<T> operation) {
        if (operation == null) throw new IllegalArgumentException("La operación administrativa es obligatoria.");
        synchronized (users) {
            return operation.get();
        }
    }
}
