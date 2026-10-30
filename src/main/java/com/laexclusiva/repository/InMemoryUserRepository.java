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

    @Override
    public void save(UserAccount user) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("La cuenta de usuario y el username no pueden ser nulos ni vacíos.");
        }
        
        String username = user.getUsername().trim().toLowerCase();
        boolean usernameExists = storage.values().stream()
                .anyMatch(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(username));
        
        if (usernameExists) {
            throw new IllegalArgumentException("El nombre de usuario ya se encuentra registrado.");
        }

        if (user.getId() == null) {
            user.setId(idCounter.getAndIncrement());
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
                .filter(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(target))
                .findFirst();
    }

    @Override
    public List<UserAccount> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void update(UserAccount user) {
        if (user == null || user.getId() == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("El usuario, ID y nombre deben ser válidos para actualizar.");
        }

        if (!storage.containsKey(user.getId())) {
            throw new IllegalArgumentException("No existe un usuario con el ID especificado para actualizar.");
        }

        String newUsername = user.getUsername().trim().toLowerCase();
        boolean usernameTaken = storage.values().stream()
                .anyMatch(u -> !u.getId().equals(user.getId()) && 
                               u.getUsername() != null && 
                               u.getUsername().equalsIgnoreCase(newUsername));

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
