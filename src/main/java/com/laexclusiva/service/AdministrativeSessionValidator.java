package com.laexclusiva.service;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.UserRepository;

/**
 * Revalida una sesión administrativa contra la cuenta actual, sin modificarla.
 * Debe invocarse DENTRO de UserAdministrationBoundary.execute, junto con las lecturas
 * y escrituras que protege y sobre el mismo repositorio/proveedor. No abre transacciones
 * ni adquiere bloqueos: validarla aisladamente no autoriza una escritura posterior.
 * Exige procedencia reconocida por el emisor explícito. El constructor anterior falla cerrado.
 */
public final class AdministrativeSessionValidator {
    private final AuthorizationService authorization;
    private final AuthenticatedSessionValidator sessions;

    /**
     * @param users repositorio compartido con la frontera administrativa
     * @param authorization mecanismo existente de permisos
     * @throws IllegalArgumentException si falta alguna dependencia
     */
    public AdministrativeSessionValidator(UserRepository users, AuthorizationService authorization) {
        this(users, authorization, null);
    }

    /** Comparte el emisor; el constructor antiguo falla cerrado al no disponer de autoridad. */
    public AdministrativeSessionValidator(UserRepository users, AuthorizationService authorization,
                                          AuthenticationService authentication) {
        if (users == null || authorization == null) {
            throw new IllegalArgumentException("El repositorio y la autorización son obligatorios.");
        }
        this.authorization = authorization;
        if (authentication != null && !authentication.usesRepository(users)) {
            throw new IllegalArgumentException("Autenticación y validación deben compartir el mismo repositorio.");
        }
        this.sessions = authentication == null ? null : authentication.sessions();
    }

    /**
     * Exige una sesión válida, cuenta activa, versión vigente y MANAGE_USERS actual.
     * No devuelve cuentas ni hashes, no renueva la captura y no invalida la sesión.
     * Los errores del repositorio se propagan e impiden continuar; no hay fallback ni reintentos.
     * @param session sesión cuyo ID y versión fueron capturados al construirla
     * @throws SecurityException si la sesión o la cuenta no cumplen las condiciones
     */
    public void validate(UserSession session) {
        if (sessions == null) throw denied();
        AuthenticatedPrincipal principal = sessions.validate(session);
        UserAccount current = new UserAccount();
        current.setId(principal.userId());
        current.setRole(principal.role());
        current.setActive(true);
        if (!authorization.hasPermission(current, Permission.MANAGE_USERS)) {
            throw denied();
        }
    }

    private static SecurityException denied() {
        return new SecurityException("La sesión no permite administrar usuarios; vuelva a iniciar sesión.");
    }
}
