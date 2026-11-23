package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.*;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import org.junit.jupiter.api.Test;

/** Verifica que permisos de la composición productiva no dependan del snapshot mutable de sesión. */
class AuthorizationServiceCurrentIdentityTest {
    private static final String PASSWORD = "Authorization-Current-Identity-2026";

    @Test
    void roleMutationDeactivationAndRevocationTakeEffectOnAlreadyIssuedSessions() {
        var users = new InMemoryUserRepository();
        users.save(new UserAccount(null, "account", PasswordHasher.hash(PASSWORD), UserRole.WORKER, "S-1"));
        var authentication = new AuthenticationService(users);
        var authorization = new AuthorizationService(authentication.sessions());
        var session = authentication.createSession("account", PASSWORD).orElseThrow();

        session.getUserAccount().setRole(UserRole.ADMINISTRATOR); // La instantánea no otorga MANAGE_USERS.
        assertFalse(authorization.hasPermission(session, Permission.MANAGE_USERS));
        assertEquals(UserRole.WORKER, authorization.currentPrincipal(session).role());

        var changed = users.findById("1").orElseThrow();
        changed.setRole(UserRole.ADMINISTRATOR);
        users.update(changed);
        assertFalse(authorization.hasPermission(session, Permission.MANAGE_USERS));
        assertTrue(authorization.hasPermission(authentication.createSession("account", PASSWORD).orElseThrow(),
                Permission.MANAGE_USERS));

        var administratorSession = authentication.createSession("account", PASSWORD).orElseThrow();
        var deactivated = users.findById("1").orElseThrow();
        deactivated.setActive(false);
        users.update(deactivated);
        assertFalse(authorization.hasPermission(administratorSession, Permission.MANAGE_USERS));

        var reactivated = users.findById("1").orElseThrow();
        reactivated.setActive(true);
        users.update(reactivated);
        var fresh = authentication.createSession("account", PASSWORD).orElseThrow();
        authentication.revoke(fresh);
        assertFalse(authorization.hasPermission(fresh, Permission.MANAGE_USERS));
    }
}
