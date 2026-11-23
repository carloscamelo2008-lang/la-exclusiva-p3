package com.laexclusiva;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class UserModuleTest {

    private UserRepository userRepository;
    private AuthenticationService authService;
    private AuthorizationService authzService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        authService = new AuthenticationService(userRepository);
        authzService = new AuthorizationService();
    }

    @Test
    void testPasswordHasherHashingAndVerification() {
        String password = "SecretPassword123";
        String hash = PasswordHasher.hash(password);

        assertNotNull(hash);
        assertTrue(PasswordHasher.verify(password, hash));
        assertFalse(PasswordHasher.verify("WrongPassword", hash));
    }

    @Test
    void testSuccessfulAuthenticationAndSessionCreation() {
        String hash = PasswordHasher.hash("admin123");
        UserAccount admin = new UserAccount(null, "admin", hash, UserRole.ADMINISTRATOR, "SEL-00000001");
        userRepository.save(admin);

        Optional<UserAccount> authenticated = authService.authenticate("  ADMIN  ", "admin123");
        assertTrue(authenticated.isPresent());
        assertEquals("SEL-00000001", authenticated.get().getSellerId());

        Optional<UserSession> sessionOpt = authService.createSession("admin", "admin123");
        assertTrue(sessionOpt.isPresent());
        assertNotNull(sessionOpt.get().getLoginTime());
        UserSession session = sessionOpt.orElseThrow();
        assertTrue(session.isActive());
        assertEquals(admin.getId(), session.getUserAccount().getId());
        assertEquals(UserRole.ADMINISTRATOR, session.getUserAccount().getRole());
        assertEquals("SEL-00000001", session.getUserAccount().getSellerId());
    }

    @Test
    void testAuthenticationFailsForDisabledAccount() {
        String hash = PasswordHasher.hash("user123");
        UserAccount user = new UserAccount(null, "disabled_user", hash, UserRole.WORKER, null);
        user.setActive(false);
        userRepository.save(user);

        Optional<UserAccount> authenticated = authService.authenticate("disabled_user", "user123");
        assertFalse(authenticated.isPresent());
        assertTrue(authService.createSession("disabled_user", "user123").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "member, Incorrect-Test-Password",
        "unknown, Temporary-Test-Password"
    })
    void rejectedCredentialsDoNotAuthenticateOrCreateSessions(String username, String password) {
        UserAccount account = new UserAccount(null, "member", PasswordHasher.hash("Temporary-Test-Password"),
                UserRole.WORKER, "SEL-TEST-01");
        userRepository.save(account);

        assertTrue(authService.authenticate(username, password).isEmpty());
        assertTrue(authService.createSession(username, password).isEmpty());
        assertTrue(account.isActive());
        assertEquals(1, userRepository.findAll().size());
    }

    @Test
    void reconstructedServiceUsesTheSameMemoryRepositoryAndItsUpdatedCredentials() {
        UserAccount account = new UserAccount(null, "member", PasswordHasher.hash("Original-Test-Password"),
                UserRole.WORKER, "SEL-TEST-01");
        userRepository.save(account);
        UserSession original = authService.createSession("member", "Original-Test-Password").orElseThrow();
        original.invalidate();

        account.setPasswordHash(PasswordHasher.hash("Updated-Test-Password"));
        account.setSellerId("SEL-TEST-02");
        userRepository.update(account);
        AuthenticationService reconstructed = new AuthenticationService(userRepository);

        assertTrue(reconstructed.createSession("member", "Original-Test-Password").isEmpty());
        UserSession current = reconstructed.createSession("  MEMBER  ", "Updated-Test-Password").orElseThrow();
        assertNotSame(original, current);
        assertFalse(original.isActive());
        assertTrue(current.isActive());
        assertEquals(account.getId(), current.getUserAccount().getId());
        assertEquals(UserRole.WORKER, current.getUserAccount().getRole());
        assertEquals("SEL-TEST-02", current.getUserAccount().getSellerId());
        assertEquals(1, userRepository.findAll().size());
    }

    @Test
    void reconstructedServiceSwitchesUsersWithoutRevivingTheLoggedOutAdministrator() {
        userRepository.save(new UserAccount(null, "admin", PasswordHasher.hash("Admin-Test-Password"),
                UserRole.ADMINISTRATOR, "SEL-ADMIN-TEST"));
        userRepository.save(new UserAccount(null, "worker", PasswordHasher.hash("Worker-Test-Password"),
                UserRole.WORKER, "SEL-WORKER-TEST"));
        UserSession previous = authService.createSession("admin", "Admin-Test-Password").orElseThrow();
        assertTrue(authzService.hasPermission(previous, Permission.MANAGE_USERS));
        previous.invalidate();

        AuthenticationService reconstructed = new AuthenticationService(userRepository);
        UserSession worker = reconstructed.createSession("WORKER", "Worker-Test-Password").orElseThrow();
        assertNotSame(previous, worker);
        assertNotEquals(previous.getUserAccount().getId(), worker.getUserAccount().getId());
        assertFalse(previous.isActive());
        assertTrue(previous.getUserAccount().isActive());
        assertFalse(authzService.hasPermission(previous, Permission.MANAGE_USERS));
        assertTrue(worker.isActive());
        assertEquals(UserRole.WORKER, worker.getUserAccount().getRole());
        assertEquals("SEL-WORKER-TEST", worker.getUserAccount().getSellerId());
        assertFalse(authzService.hasPermission(worker, Permission.MANAGE_USERS));
        assertTrue(authzService.hasPermission(worker, Permission.REGISTER_SALE));
        assertEquals(2, userRepository.findAll().size());
    }

    @Test
    void testRoleAndPermissionAuthorizationsAndMatrix() {
        UserAccount admin = new UserAccount(1L, "admin", "hash", UserRole.ADMINISTRATOR, null);
        UserAccount worker = new UserAccount(2L, "worker", "hash", UserRole.WORKER, "SEL-00000002");

        assertTrue(authzService.isAdministrator(admin));
        assertFalse(authzService.isAdministrator(worker));

        assertTrue(worker.getRole().hasPermission(Permission.VIEW_CATALOG));
        assertTrue(worker.getRole().hasPermission(Permission.VIEW_INVENTORY));
        assertTrue(worker.getRole().hasPermission(Permission.REGISTER_SALE));
        assertTrue(worker.getRole().hasPermission(Permission.REGISTER_CUSTOMER));
        assertTrue(worker.getRole().hasPermission(Permission.VIEW_CUSTOMER));
        assertTrue(worker.getRole().hasPermission(Permission.VIEW_SALES));
        assertTrue(worker.getRole().hasPermission(Permission.VIEW_PORTFOLIO));
        assertTrue(worker.getRole().hasPermission(Permission.REGISTER_PAYMENT));

        assertFalse(worker.getRole().hasPermission(Permission.MANAGE_USERS));
        assertFalse(worker.getRole().hasPermission(Permission.MANAGE_PRODUCTS));
        assertFalse(worker.getRole().hasPermission(Permission.MANAGE_PRICES));
        assertFalse(worker.getRole().hasPermission(Permission.MANAGE_INVENTORY_ENTRIES));
    }

    @Test
    void testDuplicateManualIdIsRejectedAndPreservesOriginal() {
        UserAccount user1 = new UserAccount(50L, "originalUser", "hash1", UserRole.WORKER, null);
        userRepository.save(user1);

        UserAccount user2 = new UserAccount(50L, "duplicateUser", "hash2", UserRole.WORKER, null);
        assertThrows(IllegalArgumentException.class, () -> userRepository.save(user2));

        Optional<UserAccount> fetched = userRepository.findById("50");
        assertTrue(fetched.isPresent());
        assertEquals("originalUser", fetched.get().getUsername());
    }

    @Test
    void testUsernameNormalizationAndValidation() {
        UserAccount u1 = new UserAccount(null, "  JohnDoe  ", "hash", UserRole.WORKER, null);
        userRepository.save(u1);

        assertTrue(userRepository.findByUsername("johndoe").isPresent());
        assertTrue(userRepository.findByUsername("JOHNDOE").isPresent());

        UserAccount invalidUser = new UserAccount(null, "", "", null, null);
        assertThrows(IllegalArgumentException.class, () -> userRepository.save(invalidUser));
    }
}
