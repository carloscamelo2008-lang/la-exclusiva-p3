package com.laexclusiva.service;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import java.util.function.Supplier;

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

    /** Indica si este servicio valida sesiones emitidas por una autoridad concreta. */
    public boolean hasSessionAuthority() {
        return sessions != null;
    }

    /**
     * Rechaza el uso de este autorizador como frontera de una ruta productiva.
     * El constructor sin validador permanece disponible para consumidores de compatibilidad,
     * pero solo puede evaluar snapshots y nunca habilita operaciones protegidas.
     *
     * @throws SecurityException si no se inyectó el validador de la autoridad actual
     */
    public void requireSessionAuthority() {
        if (sessions == null) {
            throw new SecurityException("La operación requiere una autoridad de sesiones vigente.");
        }
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

    private static final ThreadLocal<AuthenticatedPrincipal> operationActor = new ThreadLocal<>();

    /** Actor validado para la operación síncrona en curso, si existe. */
    public static AuthenticatedPrincipal operationActor() {
        return operationActor.get();
    }

    static Long operationUserId() {
        AuthenticatedPrincipal principal = operationActor();
        return principal == null ? null : principal.userId();
    }

    static void requireSaleSeller(String sellerId, boolean requireActor) {
        AuthenticatedPrincipal principal = operationActor();
        if (principal == null) {
            if (requireActor) throw new SecurityException("Se requiere una sesión autorizada para registrar ventas.");
            return;
        }
        if (!principal.role().hasPermission(Permission.REGISTER_SALE)
                || (!principal.role().hasPermission(Permission.REGISTER_SALE_FOR_OTHERS)
                    && !java.util.Objects.equals(principal.sellerId(), sellerId))) {
            throw new SecurityException("La venta debe registrarse con el vendedor asignado a la sesión.");
        }
    }

    /**
     * Ejecuta una operación comercial solo después de validar la sesión y el permiso vigente.
     * La validación y la invocación quedan en un mismo punto de aplicación; conserva intactos
     * los contratos de los servicios legacy que también consumen la consola.
     *
     * @param session sesión emitida por la autoridad activa
     * @param permission permiso requerido en el instante de la operación
     * @param operation operación de lectura o escritura que no debe iniciarse si se deniega
     * @param <T> resultado de la operación
     * @return resultado producido por la operación
     * @throws SecurityException si la sesión no fue emitida por la autoridad vigente, está revocada,
     *                           la cuenta cambió o el permiso no corresponde al rol actual
     * @throws IllegalArgumentException si falta el permiso o la operación
     */
    public <T> T execute(UserSession session, Permission permission, Supplier<T> operation) {
        if (permission == null || operation == null) {
            throw new IllegalArgumentException("El permiso y la operación son obligatorios.");
        }
        requireSessionAuthority();
        AuthenticatedPrincipal principal = currentPrincipal(session);
        if (!principal.role().hasPermission(permission)) {
            throw new SecurityException("La sesión no permite ejecutar esta operación.");
        }
        AuthenticatedPrincipal previous = operationActor.get();
        operationActor.set(principal);
        try {
            return operation.get();
        } finally {
            if (previous == null) operationActor.remove();
            else operationActor.set(previous);
        }
    }

    /** Variante para operaciones que no devuelven resultado. */
    public void execute(UserSession session, Permission permission, Runnable operation) {
        if (operation == null) throw new IllegalArgumentException("La operación es obligatoria.");
        execute(session, permission, () -> {
            operation.run();
            return null;
        });
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
