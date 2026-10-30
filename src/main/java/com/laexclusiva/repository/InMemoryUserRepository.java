package com.laexclusiva.repository;

import com.laexclusiva.model.UserAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, UserAccount> accountsById = new ConcurrentHashMap<>();

    @Override
    public void save(UserAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("La cuenta de usuario no puede ser nula.");
        }
        if (findByUsername(account.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con el nombre: " + account.getUsername());
        }
        accountsById.put(account.getId(), account);
    }

    @Override
    public void update(UserAccount account) {
        if (account == null || account.getId() == null || !accountsById.containsKey(account.getId())) {
            throw new IllegalArgumentException("No se puede actualizar una cuenta inexistente.");
        }
        accountsById.put(account.getId(), account);
    }

    @Override
    public Optional<UserAccount> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(accountsById.get(id));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return accountsById.values().stream()
                .filter(acc -> acc.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    @Override
    public List<UserAccount> findAll() {
        return new ArrayList<>(accountsById.values());
    }
}