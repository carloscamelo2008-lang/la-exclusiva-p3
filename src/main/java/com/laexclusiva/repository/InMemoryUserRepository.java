package com.laexclusiva.repository;

import com.laexclusiva.model.UserAccount;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Almacena snapshots independientes y serializa las operaciones por instancia.
 * La versión solo detecta cambios de seguridad; no es una revisión general de fila.
 * Los llamadores no deben modificar simultáneamente un mismo objeto de entrada.
 * 
 * @author Daniel Arrieta
 */
public class InMemoryUserRepository implements UserRepository {
    
    private final Map<Long, UserAccount> storage = new HashMap<>();
    private long nextId = 1;
    private boolean generatedIdsExhausted;

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario no puede ser nulo ni vacío.");
        }
        return username.trim().toLowerCase();
    }

    private void validateUserAccount(UserAccount user) {
        if (user == null) {
            throw new IllegalArgumentException("La cuenta de usuario no puede ser nula.");
        }
        normalizeUsername(user.getUsername());
        if (user.getPasswordHash() == null || user.getPasswordHash().trim().isEmpty()) {
            throw new IllegalArgumentException("El hash de contraseña no puede ser nulo ni vacío.");
        }
        if (user.getRole() == null) {
            throw new IllegalArgumentException("El rol de usuario no puede ser nulo.");
        }
    }

    /**
     * Guarda una copia con versión cero y devuelve ID y versión al llamador tras el éxito.
     * Los IDs automáticos no se reutilizan; al agotarse, todavía se admiten IDs explícitos libres.
     * @throws IllegalArgumentException por datos, username o ID inválidos o duplicados
     * @throws ArithmeticException si se agotan los identificadores automáticos positivos
     */
    @Override
    public synchronized void save(UserAccount user) {
        validateUserAccount(user);
        UserAccount snapshot = copy(user);
        String normalizedUsername = normalizeUsername(snapshot.getUsername());
        boolean usernameExists = storage.values().stream()
                .anyMatch(u -> normalizeUsername(u.getUsername()).equals(normalizedUsername));
        
        if (usernameExists) {
            throw new IllegalArgumentException("El nombre de usuario ya se encuentra registrado.");
        }

        long assignedId;
        if (snapshot.getId() == null) {
            if (generatedIdsExhausted) {
                throw new ArithmeticException("Se agotaron los identificadores automáticos de usuarios.");
            }
            assignedId = nextId;
            // Defensa adicional: nunca sustituir una cuenta por un ID automático ocupado.
            while (storage.containsKey(assignedId)) {
                assignedId = Math.incrementExact(assignedId);
            }
        } else {
            assignedId = snapshot.getId();
            if (assignedId <= 0) {
                throw new IllegalArgumentException("El ID del usuario debe ser mayor a cero.");
            }
            if (storage.containsKey(assignedId)) {
                throw new IllegalArgumentException("Ya existe una cuenta registrada con el ID especificado.");
            }
        }
        long followingId = nextId;
        boolean exhausted = generatedIdsExhausted;
        if (assignedId >= nextId && !exhausted) {
            if (assignedId == Long.MAX_VALUE) exhausted = true;
            else followingId = Math.incrementExact(assignedId);
        }
        snapshot.setId(assignedId);
        java.util.UUID instanceId;
        do { instanceId = java.util.UUID.randomUUID(); }
        while (containsInstanceId(instanceId));
        snapshot.setAccountInstanceId(instanceId);
        snapshot.setSecurityVersion(0);
        storage.put(assignedId, snapshot);
        nextId = followingId;
        generatedIdsExhausted = exhausted;
        user.setId(assignedId);
        user.setAccountInstanceId(instanceId);
        user.setSecurityVersion(0);
    }

    @Override
    public synchronized Optional<UserAccount> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            Long longId = Long.valueOf(id.trim());
            return Optional.ofNullable(storage.get(longId)).map(InMemoryUserRepository::copy);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<UserAccount> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        String target = username.trim().toLowerCase();
        return storage.values().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().trim().toLowerCase().equals(target))
                .findFirst().map(InMemoryUserRepository::copy);
    }

    @Override
    public synchronized List<UserAccount> findAll() {
        List<UserAccount> users = new ArrayList<>();
        for (UserAccount user : storage.values()) users.add(copy(user));
        return users;
    }

    /**
     * Actualiza una copia si coincide la versión esperada y conserva la fecha histórica.
     * Incrementa una vez por cambios reales de rol, hash, estado o vendedor, no de username.
     * @throws IllegalArgumentException por datos inválidos, duplicados o cuenta inexistente
     * @throws ConcurrentModificationException si la versión recibida no coincide con la almacenada
     * @throws ArithmeticException si un cambio de seguridad desborda la versión
     */
    @Override
    public synchronized void update(UserAccount user) {
        validateUserAccount(user);
        UserAccount snapshot = copy(user);
        if (snapshot.getId() == null || snapshot.getId() <= 0) {
            throw new IllegalArgumentException("El ID debe ser un valor válido mayor a cero para actualizar.");
        }

        UserAccount stored = storage.get(snapshot.getId());
        if (stored == null) {
            throw new IllegalArgumentException("No existe un usuario con el ID especificado para actualizar.");
        }
        if (!Objects.equals(snapshot.getAccountInstanceId(), stored.getAccountInstanceId())
                || snapshot.getSecurityVersion() != stored.getSecurityVersion()) {
            throw new ConcurrentModificationException("La cuenta cambió; vuelva a cargarla antes de actualizar.");
        }

        String newUsername = normalizeUsername(snapshot.getUsername());
        boolean usernameTaken = storage.values().stream()
                .anyMatch(u -> !u.getId().equals(snapshot.getId()) &&
                               normalizeUsername(u.getUsername()).equals(newUsername));

        if (usernameTaken) {
            throw new IllegalArgumentException("El nuevo nombre de usuario ya pertenece a otra cuenta.");
        }

        boolean securityChanged = !Objects.equals(stored.getRole(), snapshot.getRole())
                || !Objects.equals(stored.getPasswordHash(), snapshot.getPasswordHash())
                || stored.isActive() != snapshot.isActive()
                || !Objects.equals(stored.getSellerId(), snapshot.getSellerId());
        long version = securityChanged ? Math.incrementExact(stored.getSecurityVersion())
                : stored.getSecurityVersion();
        snapshot.setSecurityVersion(version);
        snapshot.setCreatedAt(stored.getCreatedAt());
        storage.put(snapshot.getId(), snapshot);
        user.setSecurityVersion(version);
    }

    @Override
    public synchronized void delete(String id) {
        findById(id).ifPresent(u -> storage.remove(u.getId()));
    }

    /** Copia todos los atributos sin compartir la instancia mutable de la cuenta. */
    private static UserAccount copy(UserAccount user) {
        UserAccount snapshot = new UserAccount(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getRole(), user.getSellerId());
        snapshot.setActive(user.isActive());
        snapshot.setCreatedAt(user.getCreatedAt());
        snapshot.setSecurityVersion(user.getSecurityVersion());
        if (user.getAccountInstanceId() != null) snapshot.setAccountInstanceId(user.getAccountInstanceId());
        return snapshot;
    }

    private boolean containsInstanceId(java.util.UUID instanceId) {
        return storage.values().stream().anyMatch(user -> instanceId.equals(user.getAccountInstanceId()));
    }
}
