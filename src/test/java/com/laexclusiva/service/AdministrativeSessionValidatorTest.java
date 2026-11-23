package com.laexclusiva.service;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AdministrativeSessionValidatorTest {
    private static final String TEST_HASH = com.laexclusiva.security.PasswordHasher.hash("Identity-Test-Password");
    private UserSession issued(UserAccount snapshot) {
        repository.loginAccount = snapshot;
        return authentication.createSession(snapshot.getUsername(), "Identity-Test-Password").orElseThrow();
    }
    private final ReadOnlyRepository repository = new ReadOnlyRepository();
    private final AuthenticationService authentication = new AuthenticationService(repository);
    private final AdministrativeSessionValidator validator =
            new AdministrativeSessionValidator(repository, new AuthorizationService(), authentication);

    @Test
    void rejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new AdministrativeSessionValidator(null, new AuthorizationService()));
        assertThrows(IllegalArgumentException.class, () -> new AdministrativeSessionValidator(repository, null));
    }

    @Test
    void authorizesAnActiveAdministratorWithTheCapturedVersionWithoutMutations() {
        UserAccount snapshot = account();
        UserSession session = issued(snapshot);
        LocalDateTime loginTime = session.getLoginTime();
        validator.validate(session);
        assertEquals(1, repository.reads);
        assertEquals("1", repository.requestedId);
        assertAccountUnchanged(snapshot);
        assertAccountUnchanged(repository.current);
        assertTrue(session.isValid());
        assertEquals(loginTime, session.getLoginTime());
        assertEquals(0, session.getAuthenticatedSecurityVersion());
    }

    @Test
    void rejectsNullSessionWithoutConsultingRepository() {
        assertThrows(SecurityException.class, () -> validator.validate(null));
        assertEquals(0, repository.reads);
    }

    @Test
    void rejectsInvalidatedSessionEvenWhenThePersistedAccountIsActive() {
        UserSession session = issued(account());
        session.invalidate();
        assertTrue(repository.current.isActive());
        assertThrows(SecurityException.class, () -> validator.validate(session));
        assertFalse(session.isValid());
        assertEquals(0, repository.reads);
    }

    @Test
    void rejectsMissingActor() {
        repository.current = null;
        UserSession session = issued(account());
        assertThrows(SecurityException.class, () -> validator.validate(session));
        assertTrue(session.isValid());
        assertEquals(1, repository.reads);
    }

    @Test
    void rejectsInactivePersistedActorWithoutReactivatingIt() {
        repository.current.setActive(false);
        UserSession session = issued(account());
        assertThrows(SecurityException.class, () -> validator.validate(session));
        assertFalse(repository.current.isActive());
        assertTrue(session.isValid());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, Long.MAX_VALUE})
    void rejectsDifferentPersistedVersionWithoutRenewingSession(long version) {
        repository.current.setSecurityVersion(version);
        UserSession session = issued(account());
        assertThrows(SecurityException.class, () -> validator.validate(session));
        assertEquals(0, session.getAuthenticatedSecurityVersion());
        assertEquals(version, repository.current.getSecurityVersion());
        assertTrue(session.isValid());
    }

    @Test
    void rejectsWorkerWithoutManageUsers() {
        repository.current.setRole(UserRole.WORKER);
        UserAccount snapshot = account();
        snapshot.setRole(UserRole.WORKER);
        assertThrows(SecurityException.class, () -> validator.validate(issued(snapshot)));
        assertEquals(UserRole.WORKER, repository.current.getRole());
    }

    @Test
    void rejectsPersistedDemotionEvenWhenSnapshotStillSaysAdministratorAtTheSameVersion() {
        // Doble inconsistente para aislar la comprobación de permisos de la de versión.
        repository.current.setRole(UserRole.WORKER);
        UserSession session = issued(account());
        assertEquals(UserRole.ADMINISTRATOR, session.getUserAccount().getRole());
        assertThrows(SecurityException.class, () -> validator.validate(session));
    }

    @Test
    void ignoresMutatedSnapshotIdRoleActivityAndVersion() {
        UserSession session = issued(account());
        UserAccount snapshot = session.getUserAccount();
        snapshot.setId(99L);
        snapshot.setRole(UserRole.WORKER);
        snapshot.setActive(false);
        snapshot.setSecurityVersion(50);
        snapshot.setSellerId("OTHER-SELLER");
        assertFalse(session.isActive());
        assertTrue(session.isValid());
        validator.validate(session);
        assertEquals("1", repository.requestedId);
        assertEquals(1L, session.getAuthenticatedUserId());
        assertEquals(0, session.getAuthenticatedSecurityVersion());
        assertEquals(99L, snapshot.getId());
        assertFalse(snapshot.isActive());
        assertEquals(50, snapshot.getSecurityVersion());
        assertAccountUnchanged(repository.current);
    }

    @Test
    void usesAccountAuthorizationOverloadWithTheReloadedAccount() {
        AuthorizationService permissions = new AuthorizationService() {
            @Override
            public boolean hasPermission(UserAccount user, Permission permission) {
                assertEquals(repository.current.getRole(), user.getRole());
                assertEquals(Permission.MANAGE_USERS, permission);
                return super.hasPermission(user, permission);
            }
            @Override
            public boolean hasPermission(UserSession session, Permission permission) {
                throw new AssertionError("No utilizar autorización sobre el snapshot de sesión.");
            }
        };
        new AdministrativeSessionValidator(repository, permissions, authentication).validate(issued(account()));
        assertEquals(1, repository.reads);
    }

    @Test
    void repositoryFailurePreventsContinuationWithoutFallbackOrRetry() {
        IllegalStateException failure = new IllegalStateException("simulated repository failure");
        repository.failure = failure;
        AtomicBoolean authorized = new AtomicBoolean();
        UserSession session = issued(account());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> {
            validator.validate(session);
            authorized.set(true);
        }));
        assertFalse(authorized.get());
        assertEquals(1, repository.reads);
        assertTrue(session.isValid());
        assertAccountUnchanged(session.getUserAccount());
    }

    @Test
    void rejectsMismatchedReturnedIdentity() {
        repository.current.setId(2L);
        assertThrows(SecurityException.class, () -> validator.validate(issued(account())));
    }

    @Test
    void rejectsNullPersistedRole() {
        repository.current.setRole(null);
        assertThrows(SecurityException.class, () -> validator.validate(issued(account())));
    }

    @Test
    void doesNotAuthorizeASessionInvalidatedDuringReload() {
        UserSession session = issued(account());
        repository.onRead = session::invalidate;
        assertThrows(SecurityException.class, () -> validator.validate(session));
        assertFalse(session.isValid());
        assertEquals(1, repository.reads);
    }

    @Test
    void integratesInsideMemoryBoundaryAndRejectsARealRepositorySecurityChange() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        users.save(account());
        AuthenticationService emitter = new AuthenticationService(users);
        UserSession session = emitter.createSession("fixture", "Identity-Test-Password").orElseThrow();
        var boundary = new InMemoryUserAdministrationBoundary(users);
        var revalidator = new AdministrativeSessionValidator(users, new AuthorizationService(), emitter);
        boundary.execute(() -> {
            assertTrue(Thread.holdsLock(users));
            revalidator.validate(session);
            return null;
        });
        boundary.execute(() -> {
            UserAccount updated = users.findById("1").orElseThrow();
            updated.setSellerId("NEW-SELLER");
            users.update(updated);
            return null;
        });
        assertThrows(SecurityException.class, () -> boundary.execute(() -> {
            revalidator.validate(session);
            return null;
        }));
        assertEquals(0, session.getAuthenticatedSecurityVersion());
        assertEquals(1, users.findById("1").orElseThrow().getSecurityVersion());
        assertTrue(session.isValid());
    }

    private static UserAccount account() {
        UserAccount account = new UserAccount(1L, "fixture", TEST_HASH, UserRole.ADMINISTRATOR, null);
        account.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return account;
    }

    private static void assertAccountUnchanged(UserAccount account) {
        assertEquals(1L, account.getId());
        assertEquals("fixture", account.getUsername());
        assertEquals(TEST_HASH, account.getPasswordHash());
        assertEquals(UserRole.ADMINISTRATOR, account.getRole());
        assertTrue(account.isActive());
        assertNull(account.getSellerId());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), account.getCreatedAt());
        assertEquals(0, account.getSecurityVersion());
    }

    /** Solo findById está permitido; cualquier escritura o consulta alternativa hace fallar la prueba. */
    private static final class ReadOnlyRepository implements UserRepository {
        UserAccount current = account();
        UserAccount loginAccount = account();
        int reads;
        String requestedId;
        RuntimeException failure;
        Runnable onRead = () -> {};
        @Override public Optional<UserAccount> findById(String id) {
            reads++;
            requestedId = id;
            onRead.run();
            if (failure != null) throw failure;
            return Optional.ofNullable(current);
        }
        @Override public void save(UserAccount user) { throw new AssertionError("No escribir."); }
        @Override public void update(UserAccount user) { throw new AssertionError("No escribir."); }
        @Override public void delete(String id) { throw new AssertionError("No escribir."); }
        @Override public List<UserAccount> findAll() { throw new AssertionError("No listar."); }
        @Override public Optional<UserAccount> findByUsername(String username) { return Optional.of(loginAccount); }
    }
}
