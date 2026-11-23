package com.laexclusiva.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.security.PasswordHasher;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Comprueba que JavaFX y los servicios consumen una única autoridad según el modo seleccionado. */
class IdentityApplicationCompositionTest {
    private static final String PASSWORD = "Identity-Composition-Only-2026";

    @Test
    void memoryModeSharesRepositoryAuthenticationAndLiveAuthorization() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        var identity = services.identity();
        var account = new UserAccount(null, "operator", PasswordHasher.hash(PASSWORD), UserRole.WORKER, null);
        identity.users().save(account);

        var session = identity.authentication().createSession("operator", PASSWORD).orElseThrow();
        assertInstanceOf(InMemoryUserRepository.class, identity.users());
        assertSame(identity.sessions(), identity.authentication().sessions());
        assertTrue(services.authorization().hasPermission(session, com.laexclusiva.model.Permission.REGISTER_SALE));
        assertFalse(services.authorization().hasPermission(session, com.laexclusiva.model.Permission.MANAGE_USERS));
        assertSame(identity.authentication(), identity.authentication());
        assertTrue(identity.authentication().createSession("operator", "incorrecta").isEmpty());
    }

    @Test
    void postgresModeBuildsJdbcIdentityWithoutConnectingOrFallingBackToMemory() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.POSTGRES_MODE, Map.of(
                "LAEXCLUSIVA_DB_URL", "jdbc:postgresql://localhost:5432/la_exclusiva_test",
                "LAEXCLUSIVA_DB_USER", "test-user",
                "LAEXCLUSIVA_DB_PASSWORD", "test-only-secret"));

        assertTrue(services.postgresMode());
        assertInstanceOf(UserRepositoryJdbc.class, services.identity().users());
        assertSame(services.identity().sessions(), services.identity().authentication().sessions());
    }
}
