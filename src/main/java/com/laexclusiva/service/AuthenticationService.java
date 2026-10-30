package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.security.PasswordHasher;
import java.util.Optional;

/**
 * Servicio encargado de la autenticación de usuarios y creación de sesiones.
 * 
 * @author Daniel Arrieta
 */
public class AuthenticationService {
    
    private final UserRepository userRepository;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Autentica un usuario por credenciales.
     * 
     * @param username Nombre de usuario.
     * @param password Contraseña en texto plano.
     * @return Optional con la cuenta autenticada.
     */
    public Optional<UserAccount> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        
        Optional<UserAccount> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            UserAccount account = userOpt.get();
            if (account.isActive() && PasswordHasher.verify(password, account.getPasswordHash())) {
                return Optional.of(account);
            }
        }
        return Optional.empty();
    }

    /**
     * Autentica un usuario y crea una sesión activa.
     * 
     * @param username Nombre de usuario.
     * @param password Contraseña en texto plano.
     * @return Optional con la sesión de usuario activa.
     */
    public Optional<UserSession> createSession(String username, String password) {
        return authenticate(username, password).map(UserSession::new);
    }
}
