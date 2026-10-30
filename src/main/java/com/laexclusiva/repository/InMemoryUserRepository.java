package com.laexclusiva.repository;

import com.laexclusiva.model.UserAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementación en memoria del repositorio de cuentas de usuario indexada por ID.
 * 
 * @author Daniel Arrieta
 */
public class InMemoryUserRepository implements UserRepository {
    
    private final Map<Long, UserAccount> storage = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

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

    @Override
    public void save(UserAccount user) {
        validateUserAccount(user);
        
        String normalizedUsername = normalizeUsername(user.getUsername());
        boolean usernameExists = storage.values().stream()
                .anyMatch(u -> normalizeUsername(u.getUsername()).equals(normalizedUsername));
        
        if (usernameExists) {
            throw new IllegalArgumentException("El nombre de usuario ya se encuentra registrado.");
        }

        if (user.getId() == null) {
            user.setId(idCounter.getAndIncrement());
        } else {
            if (user.getId() <= 0) {
                throw new IllegalArgumentException("El ID del usuario debe ser mayor a cero.");
            }
            if (storage.containsKey(user.getId())) {
                throw new IllegalArgumentException("Ya existe una cuenta registrada con el ID especificado.");
            }
            idCounter.updateAndGet(current -> Math.max(current, user.getId() + 1));
        }
        storage.put(user.getId(), user);
    }

    @Override
    public Optional<UserAccount> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            Long longId = Long.valueOf(id.trim());
            return Optional.ofNullable(storage.get(longId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        String target = username.trim().toLowerCase();
        return storage.values().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().trim().toLowerCase().equals(target))
                .findFirst();
    }

    @Override
    public List<UserAccount> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void update(UserAccount user) {
        validateUserAccount(user);
        if (user.getId() == null || user.getId() <= 0) {
            throw new IllegalArgumentException("El ID debe ser un valor válido mayor a cero para actualizar.");
        }

        if (!storage.containsKey(user.getId())) {
            throw new IllegalArgumentException("No existe un usuario con el ID especificado para actualizar.");
        }

        String newUsername = normalizeUsername(user.getUsername());
        boolean usernameTaken = storage.values().stream()
                .anyMatch(u -> !u.getId().equals(user.getId()) && 
                               normalizeUsername(u.getUsername()).equals(newUsername));

        if (usernameTaken) {
            throw new IllegalArgumentException("El nuevo nombre de usuario ya pertenece a otra cuenta.");
        }

        storage.put(user.getId(), user);
    }

    @Override
    public void delete(String id) {
        findById(id).ifPresent(u -> storage.remove(u.getId()));
    }
}
