package com.laexclusiva.ui;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityServicesTest {
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
}
