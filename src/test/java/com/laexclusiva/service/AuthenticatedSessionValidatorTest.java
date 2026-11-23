package com.laexclusiva.service;

import com.laexclusiva.model.*;
import com.laexclusiva.repository.*;
import com.laexclusiva.security.PasswordHasher;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Emisión real y revalidación sin PostgreSQL; no acredita transacciones comerciales. */
class AuthenticatedSessionValidatorTest {
    private static final String PASSWORD = "Session-Identity-Test-Only";
    private static final String HASH = PasswordHasher.hash(PASSWORD);
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final AuthenticationService auth = new AuthenticationService(users);

    private UserSession login(UserRole role) {
        users.save(new UserAccount(1L, "member", HASH, role, "SELLER-1"));
        return auth.createSession("member", PASSWORD).orElseThrow();
    }

    @Test
    void legitimateWorkerNeedsNoManageUsersAndPrincipalHasNoSecrets() {
        UserSession session = login(UserRole.WORKER);
        var principal = auth.sessions().validate(session);
        assertEquals(1L, principal.userId()); assertEquals(UserRole.WORKER, principal.role());
        assertEquals("SELLER-1", principal.sellerId()); assertEquals(0, principal.securityVersion());
        assertEquals(List.of("userId", "role", "sellerId", "securityVersion", "accountInstanceId"),
                Arrays.stream(AuthenticatedPrincipal.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
        assertThrows(SecurityException.class, () -> new AdministrativeSessionValidator(users,
                new AuthorizationService(), auth).validate(session));
    }

    @Test
    void administratorWorksOnlyWithExplicitMatchingAuthority() {
        UserSession session = login(UserRole.ADMINISTRATOR);
        new AdministrativeSessionValidator(users, new AuthorizationService(), auth).validate(session);
        assertThrows(SecurityException.class, () -> new AdministrativeSessionValidator(users, new AuthorizationService()).validate(session));
        assertThrows(IllegalArgumentException.class, () -> new AdministrativeSessionValidator(users,
                new AuthorizationService(), new AuthenticationService(new InMemoryUserRepository())));
    }

    @Test
    void manualForeignNullAndInvalidCredentialsCannotBecomeIssued() {
        UserSession original = login(UserRole.ADMINISTRATOR);
        assertThrows(SecurityException.class, () -> auth.sessions().validate(null));
        assertThrows(SecurityException.class, () -> auth.sessions().validate(new UserSession(original.getUserAccount())));
        AuthenticationService foreign = new AuthenticationService(users);
        assertThrows(SecurityException.class, () -> auth.sessions().validate(foreign.createSession("member", PASSWORD).orElseThrow()));
        assertTrue(auth.createSession("member", "incorrect").isEmpty());
        assertTrue(auth.createSession("missing", PASSWORD).isEmpty());
    }

    @Test
    void overridingThePublicAuthenticationMethodCannotMintAnIssuedSession() {
        login(UserRole.ADMINISTRATOR);
        AuthenticationService subclass = new AuthenticationService(users) {
            @Override public Optional<UserAccount> authenticate(String name, String password) {
                return users.findById("1");
            }
        };
        assertTrue(subclass.createSession("member", "incorrect").isEmpty());
    }

    @Test
    void hostileSessionSubclassIsRejectedBeforeCallingItsMethodsOrMapEquality() {
        UserSession original = login(UserRole.ADMINISTRATOR);
        UserSession hostile = new UserSession(original.getUserAccount()) {
            @Override public boolean isValid() { throw new AssertionError("No consultar estado manipulado"); }
            @Override public int hashCode() { throw new AssertionError("No consultar hashCode manipulado"); }
            @Override public boolean equals(Object other) { throw new AssertionError("No consultar equals manipulado"); }
            @Override public Long getAuthenticatedUserId() { throw new AssertionError("No consultar identidad manipulada"); }
        };
        assertThrows(SecurityException.class, () -> auth.sessions().validate(hostile));
        assertThrows(SecurityException.class, () -> new AdministrativeSessionValidator(users, new AuthorizationService(), auth).validate(hostile));
        auth.revoke(hostile); auth.cleanSessions();
        assertEquals(1L, auth.sessions().validate(original).userId());
    }

    @Test
    void overriddenSessionFactoryCannotRegisterAnArbitrarySessionAndForeignRevokeIsHarmless() {
        UserSession original = login(UserRole.ADMINISTRATOR);
        AuthenticationService foreign = new AuthenticationService(users) {
            @Override public Optional<UserSession> createSession(String name, String password) {
                return Optional.of(new UserSession(original.getUserAccount()));
            }
        };
        UserSession forged = foreign.createSession("member", "incorrect").orElseThrow();
        assertThrows(SecurityException.class, () -> foreign.sessions().validate(forged));
        assertThrows(SecurityException.class, () -> auth.sessions().validate(forged));
        foreign.revoke(original);
        assertTrue(original.isValid());
        assertEquals(1L, auth.sessions().validate(original).userId());
    }

    @Test
    void mutationOfSessionAccountCannotChangeTrustedIdentityOrPrincipal() {
        UserSession session = login(UserRole.WORKER);
        var snapshot = session.getUserAccount();
        snapshot.setId(99L); snapshot.setRole(UserRole.ADMINISTRATOR); snapshot.setActive(false);
        snapshot.setSecurityVersion(50); snapshot.setSellerId("OTHER");
        snapshot.setAccountInstanceId(UUID.randomUUID());
        var principal = auth.sessions().validate(session);
        assertEquals(1L, principal.userId()); assertEquals(UserRole.WORKER, principal.role());
        assertEquals("SELLER-1", principal.sellerId()); assertEquals(0, principal.securityVersion());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(UserRole.class)
    void deletedAccountCannotBeReplacedUnderAnOldSessionEvenWithSameIdVersionAndPassword(UserRole role) {
        UserSession old = login(UserRole.ADMINISTRATOR);
        UUID oldIdentity = old.getAuthenticatedAccountInstanceId();
        UserAccount stale = users.findById("1").orElseThrow();
        users.delete("1");
        UserAccount replacement = new UserAccount(1L, "member", HASH, role, "SELLER-1");
        replacement.setAccountInstanceId(oldIdentity); // El llamador no impone la identidad de una inserción.
        users.save(replacement);
        assertEquals(0, replacement.getSecurityVersion());
        assertNotEquals(oldIdentity, replacement.getAccountInstanceId());
        assertThrows(SecurityException.class, () -> auth.sessions().validate(old));
        assertThrows(SecurityException.class, () -> new AdministrativeSessionValidator(users, new AuthorizationService(), auth).validate(old));
        stale.setRole(UserRole.WORKER);
        assertThrows(ConcurrentModificationException.class, () -> users.update(stale));
        assertEquals(role, users.findById("1").orElseThrow().getRole());
        UserSession fresh = auth.createSession("member", PASSWORD).orElseThrow();
        assertEquals(replacement.getAccountInstanceId(), auth.sessions().validate(fresh).accountInstanceId());
    }

    @Test
    void normalUpdateCannotReplacePersistedInstanceIdentity() {
        UserSession session = login(UserRole.ADMINISTRATOR);
        UserAccount account = users.findById("1").orElseThrow();
        UUID original = account.getAccountInstanceId();
        account.setAccountInstanceId(UUID.randomUUID());
        assertThrows(ConcurrentModificationException.class, () -> users.update(account));
        assertEquals(original, users.findById("1").orElseThrow().getAccountInstanceId());
        assertEquals(original, auth.sessions().validate(session).accountInstanceId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "password", "seller", "version", "active", "missing"})
    void rejectsPersistedSecurityChanges(String change) {
        UserSession session = login(UserRole.ADMINISTRATOR);
        UserAccount current = users.findById("1").orElseThrow();
        switch (change) {
            case "role" -> current.setRole(UserRole.WORKER);
            case "password" -> current.setPasswordHash("changed-opaque-hash");
            case "seller" -> current.setSellerId("OTHER");
            case "active" -> current.setActive(false);
            case "missing" -> users.delete("1");
            default -> current.setSecurityVersion(1);
        }
        if (!change.equals("missing")) {
            if (change.equals("version")) {
                // El repositorio rechaza imponer versiones: utilizar un cambio real.
                current.setSecurityVersion(0); current.setPasswordHash("changed-version-hash");
            }
            users.update(current);
        }
        assertThrows(SecurityException.class, () -> auth.sessions().validate(session));
    }

    @Test
    void revokeAndCleanupAreIdempotentAndDoNotAffectOtherSessions() {
        UserSession first = login(UserRole.ADMINISTRATOR);
        UserSession second = auth.createSession("member", PASSWORD).orElseThrow();
        auth.revoke(first); auth.revoke(first); auth.cleanSessions();
        assertFalse(first.isValid()); assertThrows(SecurityException.class, () -> auth.sessions().validate(first));
        assertEquals(1L, auth.sessions().validate(second).userId());
        second.invalidate(); second.invalidate(); auth.cleanSessions();
        assertThrows(SecurityException.class, () -> auth.sessions().validate(second));
        assertTrue(users.findById("1").orElseThrow().isActive());
        assertEquals(1L, auth.sessions().validate(auth.createSession("member", PASSWORD).orElseThrow()).userId());
    }

    @Test
    void repositoryFailureIsPropagatedWithoutFallback() {
        final boolean[] fail = {false};
        InMemoryUserRepository repository = new InMemoryUserRepository() {
            @Override public synchronized Optional<UserAccount> findById(String id) {
                if (fail[0]) throw new IllegalStateException("simulated failure");
                return super.findById(id);
            }
        };
        repository.save(new UserAccount(1L, "member", HASH, UserRole.ADMINISTRATOR, null));
        AuthenticationService emitter = new AuthenticationService(repository);
        UserSession session = emitter.createSession("member", PASSWORD).orElseThrow(); fail[0] = true;
        assertThrows(IllegalStateException.class, () -> emitter.sessions().validate(session));
    }

    @Test
    void concurrentRevokePreventsAllValidationsStartedAfterItsCompletion() throws Exception {
        UserSession session = login(UserRole.ADMINISTRATOR);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch revoked = new CountDownLatch(1);
        try {
            Future<?> close = executor.submit(() -> { auth.revoke(session); revoked.countDown(); });
            Future<?> checks = executor.submit(() -> {
                try { assertTrue(revoked.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                for (int i=0;i<100;i++) assertThrows(SecurityException.class, () -> auth.sessions().validate(session));
            });
            close.get(10, TimeUnit.SECONDS); checks.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closeDuringRepositoryReadRejectsTheInFlightValidationWithoutDeadlock(boolean directLogout) throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        InMemoryUserRepository repository = new InMemoryUserRepository() {
            @Override public Optional<UserAccount> findById(String id) {
                reading.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return super.findById(id);
            }
        };
        repository.save(new UserAccount(1L, "member", HASH, UserRole.ADMINISTRATOR, null));
        AuthenticationService emitter = new AuthenticationService(repository);
        UserSession session = emitter.createSession("member", PASSWORD).orElseThrow();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> result = executor.submit(() -> assertThrows(SecurityException.class,
                    () -> emitter.sessions().validate(session)));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            if (directLogout) session.invalidate(); else emitter.revoke(session);
            release.countDown(); result.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test
    void identityCompositionSharesItsEmitterAndOldManagementConstructorsFailClosed() {
        var identity = com.laexclusiva.ui.IdentityServices.inMemory();
        identity.users().save(new UserAccount(1L, "member", HASH, UserRole.ADMINISTRATOR, null));
        UserSession session = identity.authentication().createSession("member", PASSWORD).orElseThrow();
        assertSame(identity.authentication().sessions(), identity.sessions());
        assertEquals(1L, identity.sessions().validate(session).userId());
        var legacy = new UserManagementService(users, new InMemoryUserAdministrationBoundary(users), new AuthorizationService());
        assertThrows(SecurityException.class, () -> legacy.setActive(session, "1", 0, false));
    }
}
