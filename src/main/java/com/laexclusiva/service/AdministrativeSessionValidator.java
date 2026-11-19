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
 * No autentica la procedencia de objetos de sesión construidos por otros componentes.
 */
public final class AdministrativeSessionValidator {
    private final UserRepository users;
    private final AuthorizationService authorization;

    /**
     * @param users repositorio compartido con la frontera administrativa
     * @param authorization mecanismo existente de permisos
     * @throws IllegalArgumentException si falta alguna dependencia
     */
    public AdministrativeSessionValidator(UserRepository users, AuthorizationService authorization) {
        if (users == null || authorization == null) {
            throw new IllegalArgumentException("El repositorio y la autorización son obligatorios.");
        }
        this.users = users;
        this.authorization = authorization;
    }

    /**
     * Exige una sesión válida, cuenta activa, versión vigente y MANAGE_USERS actual.
     * No devuelve cuentas ni hashes, no renueva la captura y no invalida la sesión.
     * Los errores del repositorio se propagan e impiden continuar; no hay fallback ni reintentos.
     * @param session sesión cuyo ID y versión fueron capturados al construirla
     * @throws SecurityException si la sesión o la cuenta no cumplen las condiciones
     */
    public void validate(UserSession session) {
        if (session == null || !session.isValid()) throw denied();
        Long userId = session.getAuthenticatedUserId();
        long version = session.getAuthenticatedSecurityVersion();
        if (userId == null || userId <= 0 || version < 0) throw denied();
        UserAccount current = users.findById(userId.toString()).orElseThrow(AdministrativeSessionValidator::denied);
        if (!session.isValid() || !userId.equals(current.getId()) || !current.isActive()
                || current.getSecurityVersion() != version
                || !authorization.hasPermission(current, Permission.MANAGE_USERS)) {
            throw denied();
        }
    }

    private static SecurityException denied() {
        return new SecurityException("La sesión no permite administrar usuarios; vuelva a iniciar sesión.");
    }
}
