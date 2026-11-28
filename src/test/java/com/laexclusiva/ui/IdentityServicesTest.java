package com.laexclusiva.ui;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityServicesTest {
    private static final String ADMIN_PASSWORD = "Identity-Bootstrap-Only-2026";

    @Test
    void memoryCompositionStartsEmptyWithoutProvisioningAccounts() {
        IdentityServices services = IdentityServices.inMemory();

        assertInstanceOf(InMemoryUserRepository.class, services.users());
        assertTrue(services.users().findAll().isEmpty());
    }

    @Test
    void accessorsReuseTheRepositoryAndAuthenticationInstances() {
        IdentityServices services = IdentityServices.inMemory();

        assertNotNull(services.users());
        assertNotNull(services.authentication());
        assertSame(services.users(), services.users());
        assertSame(services.authentication(), services.authentication());
    }

    @Test
    void separateMemoryCompositionsDoNotShareAccounts() {
        IdentityServices first = IdentityServices.inMemory();
        IdentityServices second = IdentityServices.inMemory();
        first.users().save(new UserAccount(null, "member", PasswordHasher.hash("Composition-Test-Only"),
                UserRole.WORKER, null));

        assertNotSame(first.users(), second.users());
        assertNotSame(first.authentication(), second.authentication());
        assertEquals(1, first.users().findAll().size());
        assertTrue(second.users().findAll().isEmpty());
        assertTrue(second.authentication().createSession("member", "Composition-Test-Only").isEmpty());
    }

    @Test
    void authenticationUsesTheExposedRepository() {
        IdentityServices services = IdentityServices.inMemory();
        UserAccount account = new UserAccount(null, "member", PasswordHasher.hash("Composition-Test-Only"),
                UserRole.WORKER, "SEL-COMPOSITION");
        services.users().save(account);

        var session = services.authentication().createSession("member", "Composition-Test-Only").orElseThrow();
        assertTrue(session.isActive());
        assertEquals(account.getId(), session.getUserAccount().getId());
        assertEquals("SEL-COMPOSITION", session.getUserAccount().getSellerId());
    }

    @Test
    void initialAdministratorBootstrapIsAtomicAndNeverResetsAnExistingAccount() {
        IdentityServices services = IdentityServices.inMemory();
        assertTrue(services.bootstrapInitialAdministrator("admin", ADMIN_PASSWORD));
        var original = services.users().findByUsername("admin").orElseThrow();

        assertFalse(services.bootstrapInitialAdministrator("other", "weak"));
        assertEquals(1, services.users().findAll().size());
        assertEquals(original.getPasswordHash(), services.users().findByUsername("admin").orElseThrow().getPasswordHash());
        assertTrue(services.authentication().createSession("admin", ADMIN_PASSWORD).isPresent());
    }

    @Test
    void administrationListsOnlySafeSummariesAndRejectsWorkers() {
        IdentityServices services = IdentityServices.inMemory();
        services.bootstrapInitialAdministrator("admin", ADMIN_PASSWORD);
        services.users().save(new UserAccount(null, "worker", PasswordHasher.hash("Worker-Identity-Only-2026"),
                UserRole.WORKER, null));
        var admin = services.authentication().createSession("admin", ADMIN_PASSWORD).orElseThrow();
        var worker = services.authentication().createSession("worker", "Worker-Identity-Only-2026").orElseThrow();

        var summaries = services.userManagement().listUsers(admin);
        assertEquals(2, summaries.size());
        assertEquals("admin", summaries.get(0).username());
        assertFalse(java.util.Arrays.stream(summaries.get(0).getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).anyMatch(name -> name.contains("password") || name.contains("hash")));
        assertThrows(SecurityException.class, () -> services.userManagement().listUsers(worker));
        assertThrows(SecurityException.class, () -> services.userManagement().createUser(worker,
                new com.laexclusiva.service.UserManagementService.CreateUserRequest(
                        "blocked", "Valid-Worker-Blocked-2026", UserRole.ADMINISTRATOR, true)));
    }

}
