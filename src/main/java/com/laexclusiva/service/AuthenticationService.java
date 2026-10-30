package com.laexclusiva.service;

import com.laexclusiva.model.PasswordHasher;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.UserRepository;

import java.util.Optional;

public class AuthenticationService {
    private final UserRepository userRepository;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserSession login(String username, char[] password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Usuario y contraseña son obligatorios.");
        }

        Optional<UserAccount> accountOpt = userRepository.findByUsername(username);
        
        // Validación genérica por seguridad para no revelar si el usuario existe o no
        if (accountOpt.isEmpty()) {
            throw new SecurityException("Credenciales inválidas o cuenta inactiva.");
        }

        UserAccount account = accountOpt.get();

        if (!account.isActive()) {
            throw new SecurityException("Credenciales inválidas o cuenta inactiva.");
        }

        boolean passwordMatch = PasswordHasher.verifyPassword(password, account.getPasswordHash());
        if (!passwordMatch) {
            throw new SecurityException("Credenciales inválidas o cuenta inactiva.");
        }

        // Crear sesión autenticada (sin exponer hashes ni secretos)
        return new UserSession(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.getSellerId()
        );
    }
}