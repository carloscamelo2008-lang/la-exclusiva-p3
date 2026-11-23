package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.*;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
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

    @Test
    void executionBoundaryRejectsForgedRevokedAndStaleRoleSessionsBeforeCallingService() {
        var users = new InMemoryUserRepository();
        users.save(new UserAccount(null, "operator", PasswordHasher.hash(PASSWORD), UserRole.ADMINISTRATOR, null));
        var authentication = new AuthenticationService(users);
        var authorization = new AuthorizationService(authentication.sessions());
        var invoked = new java.util.concurrent.atomic.AtomicInteger();

        UserSession forged = new UserSession(users.findById("1").orElseThrow());
        assertThrows(SecurityException.class, () -> authorization.execute(
                forged, Permission.MANAGE_USERS, invoked::incrementAndGet));
        assertEquals(0, invoked.get(), "Una sesión construida fuera del emisor no debe iniciar la operación.");

        UserSession oldAdmin = authentication.createSession("operator", PASSWORD).orElseThrow();
        UserAccount changed = users.findById("1").orElseThrow();
        changed.setRole(UserRole.WORKER);
        users.update(changed);
        assertThrows(SecurityException.class, () -> authorization.execute(
                oldAdmin, Permission.MANAGE_USERS, invoked::incrementAndGet));
        assertEquals(0, invoked.get(), "El rol del snapshot no debe autorizar tras un cambio persistido.");

        UserSession worker = authentication.createSession("operator", PASSWORD).orElseThrow();
        authorization.execute(worker, Permission.REGISTER_SALE, invoked::incrementAndGet);
        assertEquals(1, invoked.get());
        authentication.revoke(worker);
        assertThrows(SecurityException.class, () -> authorization.execute(
                worker, Permission.REGISTER_SALE, invoked::incrementAndGet));
        assertEquals(1, invoked.get(), "La operación no debe ejecutarse con sesión revocada.");
    }
}
