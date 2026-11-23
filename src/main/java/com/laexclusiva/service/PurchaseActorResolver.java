package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.UserRepository;
import java.util.Objects;

/** Obtiene el actor auditado desde la autoridad vigente y, si está disponible, su repositorio compartido. */
final class PurchaseActorResolver {
    private PurchaseActorResolver() { }

    static Actor resolve(AuthorizationService authorization, UserRepository users, UserSession session) {
        AuthenticatedPrincipal principal = authorization.currentPrincipal(session);
        UserAccount account = users == null ? session.getUserAccount()
                : users.findById(principal.userId().toString())
                        .orElseThrow(() -> new SecurityException("La cuenta de auditoría ya no existe."));
        if (!Objects.equals(account.getId(), principal.userId()) || !account.isActive()
                || account.getRole() != principal.role()
                || account.getSecurityVersion() != principal.securityVersion()
                || !Objects.equals(account.getAccountInstanceId(), principal.accountInstanceId())) {
            throw new SecurityException("La identidad de auditoría ya no coincide con la sesión vigente.");
        }
        return new Actor(principal.userId(), account.getUsername());
    }

    record Actor(Long userId, String username) { }
}
