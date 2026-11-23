package com.laexclusiva.service;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;

/**
 * Servicio encargado de la autorización y validación de permisos de usuario o sesión.
 * 
 * @author Daniel Arrieta
 */
public class AuthorizationService {

    private final AuthenticatedSessionValidator sessions;

    /**
     * Crea un autorizador de compatibilidad para consumidores antiguos que solo reciben sesiones
     * sintéticas. La composición JavaFX productiva debe usar el constructor con validador.
     */
    public AuthorizationService() {
        this.sessions = null;
    }

    /**
     * Crea un autorizador respaldado por la autoridad que emitió las sesiones.
     * Las decisiones se toman con el rol actual del repositorio y fallan cerradas si la sesión
     * fue revocada, desactivada o su cuenta cambió desde el inicio de sesión.
     *
     * @param sessions validador compartido con AuthenticationService
     * @throws IllegalArgumentException si falta el validador
     */
    public AuthorizationService(AuthenticatedSessionValidator sessions) {
        if (sessions == null) throw new IllegalArgumentException("El validador de sesiones es obligatorio.");
        this.sessions = sessions;
    }

    public boolean isAdministrator(UserAccount user) {
        return user != null && user.isActive() && user.getRole() == UserRole.ADMINISTRATOR;
    }

    public boolean isAdministrator(UserSession session) {
        try {
            return currentPrincipal(session).role() == UserRole.ADMINISTRATOR;
        } catch (RuntimeException deniedOrUnavailable) {
            return false;
        }
    }

    public boolean hasRole(UserAccount user, UserRole requiredRole) {
        return user != null && user.isActive() && requiredRole != null && user.getRole() == requiredRole;
    }

    public boolean hasRole(UserSession session, UserRole requiredRole) {
        if (requiredRole == null) return false;
        try {
            return currentPrincipal(session).role() == requiredRole;
        } catch (RuntimeException deniedOrUnavailable) {
            return false;
        }
    }

    public boolean hasPermission(UserAccount user, Permission permission) {
        return user != null && user.isActive() && permission != null && user.getRole() != null && user.getRole().hasPermission(permission);
    }

    public boolean hasPermission(UserSession session, Permission permission) {
        if (permission == null) return false;
        try {
            return currentPrincipal(session).role().hasPermission(permission);
        } catch (RuntimeException deniedOrUnavailable) {
            return false;
        }
    }

    /**
     * Obtiene la identidad que debe usarse para decisiones de una operación.
     * Con validador consulta la autoridad vigente; el modo sin validador solo se conserva
     * para compatibilidad con pruebas y consumidores anteriores.
     *
     * @param session sesión recibida por la operación
     * @return principal actual sin secretos
     * @throws SecurityException si la sesión no está autenticada o ya no está vigente
     */
    public AuthenticatedPrincipal currentPrincipal(UserSession session) {
        if (sessions != null) return sessions.validate(session);
        if (session == null || !session.isActive() || session.getUserAccount().getId() == null
                || session.getUserAccount().getRole() == null) {
            throw new SecurityException("La sesión no está activa.");
        }
        var account = session.getUserAccount();
        return new AuthenticatedPrincipal(account.getId(), account.getRole(), account.getSellerId(),
                account.getSecurityVersion(), account.getAccountInstanceId());
    }
}
