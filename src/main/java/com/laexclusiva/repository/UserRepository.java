package com.laexclusiva.repository;

import com.laexclusiva.model.UserAccount;
import java.util.List;
import java.util.Optional;

/**
 * Interfaz que define el contrato de persistencia para el repositorio de usuarios.
 * 
 * @author Daniel Arrieta
 */
public interface UserRepository {
    void save(UserAccount user);
    Optional<UserAccount> findById(String id);
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findAll();
    void update(UserAccount user);
    void delete(String id);
}
