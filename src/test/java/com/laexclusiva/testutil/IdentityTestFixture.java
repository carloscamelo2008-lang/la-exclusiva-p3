package com.laexclusiva.testutil;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.service.AuthorizationService;

/** Emite sesiones reales de prueba ligadas a un mismo repositorio y autoridad. */
public final class IdentityTestFixture {
    private static final String PASSWORD = "Fixture-Identity-Password-2026";
    private static final String PASSWORD_HASH = PasswordHasher.hash(PASSWORD);

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final AuthenticationService authentication = new AuthenticationService(users);
    private final AuthorizationService authorization = new AuthorizationService(authentication.sessions());
    private int nextUser = 1;

    /** Emite una sesión autenticada de prueba con el rol indicado. */
    public IssuedUser issue(UserRole role) {
        return issue("fixture-" + nextUser++, role, null);
    }

    /** Emite una sesión autenticada de prueba, con vendedor opcional. */
    public IssuedUser issue(String username, UserRole role, String sellerId) {
        UserAccount account = new UserAccount(null, username, PASSWORD_HASH, role, sellerId);
        users.save(account);
        UserAccount stored = users.findById(account.getId().toString()).orElseThrow();
        UserSession session = authentication.createSession(username, PASSWORD).orElseThrow();
        return new IssuedUser(stored, session);
    }

    public InMemoryUserRepository users() { return users; }
    public AuthenticationService authentication() { return authentication; }
    public AuthorizationService authorization() { return authorization; }

    /** Actualiza el registro persistido de prueba para simular un cambio administrativo vigente. */
    public void update(UserAccount account) {
        users.update(account);
    }

    public record IssuedUser(UserAccount account, UserSession session) { }
}
