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
    // Claves débiles: una sesión abandonada no queda retenida por la autoridad.
    // Las sesiones emitidas son instancias exactas de UserSession y usan identidad de objeto.
    private final java.util.Map<UserSession, IssuedIdentity> issued = new java.util.WeakHashMap<>();
    private record IssuedIdentity(Long id, long version) { }
    private final AuthenticatedSessionValidator validator = new AuthenticatedSessionValidator(this::validateIssued);
    
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
        return authenticateAccount(username, password);
    }

    private Optional<UserAccount> authenticateAccount(String username, String password) {
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
        return authenticateAccount(username, password).map(account -> {
            UserSession session = new UserSession(account);
            synchronized (issued) {
                issued.put(session, new IssuedIdentity(session.getAuthenticatedUserId(), session.getAuthenticatedSecurityVersion()));
            }
            return session;
        });
    }

    /** @return validador ligado exclusivamente a este emisor y repositorio */
    public final AuthenticatedSessionValidator sessions() { return validator; }

    /**
     * Revoca solo una sesión propia; operación idempotente, sin desactivar usuarios.
     * Las validaciones posteriores al retorno fallan; no revierte operaciones ya iniciadas.
     */
    public final void revoke(UserSession session) {
        if (session == null || session.getClass() != UserSession.class) return;
        synchronized (issued) {
            if (issued.remove(session) != null) session.invalidate();
        }
    }

    /** Limpia sesiones cerradas y entradas débiles; nunca registra ni reactiva sesiones. */
    public final void cleanSessions() {
        synchronized (issued) { issued.keySet().removeIf(session -> !session.isValid()); }
    }

    final boolean usesRepository(UserRepository users) { return userRepository == users; }

    private AuthenticatedPrincipal validateIssued(UserSession session) {
        if (session == null || session.getClass() != UserSession.class || !session.isValid()) throw denied();
        IssuedIdentity identity;
        synchronized (issued) { identity = issued.get(session); }
        if (identity == null) throw denied();
        UserAccount current = userRepository.findById(identity.id().toString()).orElseThrow(AuthenticationService::denied);
        if (!identity.id().equals(current.getId()) || !current.isActive() || current.getRole() == null
                || current.getSecurityVersion() != identity.version()) throw denied();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(current.getId(), current.getRole(),
                current.getSellerId(), current.getSecurityVersion());
        synchronized (issued) {
            if (issued.get(session) != identity || !session.isValid()) throw denied();
            return principal;
        }
    }

    private static SecurityException denied() {
        return new SecurityException("La sesión no es auténtica o ya no está vigente; vuelva a iniciar sesión.");
    }
}
