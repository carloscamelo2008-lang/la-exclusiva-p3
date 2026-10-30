package com.laexclusiva.repository;

import com.laexclusiva.model.UserAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementación en memoria del repositorio de cuentas de usuario.
 * 
 * @author Daniel Arrieta
 */
public class InMemoryUserRepository implements UserRepository {
    
    private final Map<String, UserAccount> storage = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    @Override
    public void save(UserAccount user) {
        if (user == null) {
            throw new IllegalArgumentException("La cuenta de usuario no puede ser nula.");
        }
        boolean usernameExists = storage.values().stream()
                .anyMatch(u -> u.getUsername().equalsIgnoreCase(user.getUsername()));
        
        if (usernameExists) {
            throw new IllegalArgumentException("El nombre de usuario ya se encuentra registrado.");
        }
        if (user.getId() == null) {
            user.setId(idCounter.getAndIncrement());
        }
        storage.put(user.getUsername().toLowerCase(), user);
    }

    @Override
    public Optional<UserAccount> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return storage.values().stream()
                .filter(u -> u.getId() != null && u.getId().toString().equals(id))
                .findFirst();
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(username.trim().toLowerCase()));
    }

    @Override
    public List<UserAccount> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void update(UserAccount user) {
        if (user == null || user.getUsername() == null) {
            throw new IllegalArgumentException("El usuario y su nombre deben ser válidos para actualizar.");
        }
        storage.put(user.getUsername().toLowerCase(), user);
    }

    @Override
    public void delete(String id) {
        findById(id).ifPresent(u -> storage.remove(u.getUsername().toLowerCase()));
    }
}
