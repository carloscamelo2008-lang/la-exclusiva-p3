package com.laexclusiva.service;

import com.laexclusiva.model.UserSession;
import java.util.function.Function;

/** Revalida procedencia y vigencia; el consumidor debe exigir sus permisos y transacción. */
public final class AuthenticatedSessionValidator {
    private final Function<UserSession, AuthenticatedPrincipal> validation;

    // Solo AuthenticationService proporciona una validación respaldada por su autoridad privada.
    AuthenticatedSessionValidator(Function<UserSession, AuthenticatedPrincipal> validation) {
        this.validation = validation;
    }

    /** @return datos actuales sin credenciales; falla cerrado ante errores de persistencia */
    public AuthenticatedPrincipal validate(UserSession session) {
        return validation.apply(session);
    }
}
