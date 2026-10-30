package com.laexclusiva.repository;

import com.laexclusiva.model.UserAccount;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    void save(UserAccount account);
    void update(UserAccount account);
    Optional<UserAccount> findById(String id);
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findAll();
}