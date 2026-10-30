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

        Optional<UserAccount> authenticated = authService.authenticate("admin", "admin123");
        assertTrue(authenticated.isPresent());
        assertEquals("SEL-00000001", authenticated.get().getSellerId());

        Optional<UserSession> sessionOpt = authService.createSession("admin", "admin123");
        assertTrue(sessionOpt.isPresent());
        assertNotNull(sessionOpt.get().getLoginTime());
    }

    @Test
    void testAuthenticationFailsForDisabledAccount() {
        String hash = PasswordHasher.hash("user123");
        UserAccount user = new UserAccount(null, "disabled_user", hash, UserRole.WORKER, null);
        user.setActive(false);
        userRepository.save(user);

        Optional<UserAccount> authenticated = authService.authenticate("disabled_user", "user123");
        assertFalse(authenticated.isPresent());
    }

    @Test
    void testRoleAndPermissionAuthorizations() {
        UserAccount admin = new UserAccount(1L, "admin", "hash", UserRole.ADMINISTRATOR, null);
        UserAccount worker = new UserAccount(2L, "worker", "hash", UserRole.WORKER, "SEL-00000002");

        assertTrue(authzService.isAdministrator(admin));
        assertFalse(authzService.isAdministrator(worker));

        assertTrue(authzService.hasPermission(admin, Permission.MANAGE_USERS));
        assertFalse(authzService.hasPermission(worker, Permission.MANAGE_USERS));
        assertTrue(authzService.hasPermission(worker, Permission.PROCESS_SALES));
    }

    @Test
    void testRepositoryUpdateAndUniqueness() {
        UserAccount u1 = new UserAccount(null, "user1", "hash1", UserRole.WORKER, null);
        userRepository.save(u1);

        u1.setUsername("user1_updated");
        userRepository.update(u1);

        assertTrue(userRepository.findByUsername("user1_updated").isPresent());
        assertFalse(userRepository.findByUsername("user1").isPresent());

        UserAccount u2 = new UserAccount(null, "user2", "hash2", UserRole.WORKER, null);
        userRepository.save(u2);

        u2.setUsername("user1_updated");
        assertThrows(IllegalArgumentException.class, () -> userRepository.update(u2));
    }
}
