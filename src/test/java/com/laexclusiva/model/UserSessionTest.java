package com.laexclusiva.model;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UserSessionTest {
    @ParameterizedTest
    @ValueSource(longs = {1, 42, Long.MAX_VALUE})
    void capturesPositiveAccountId(long id) {
        UserAccount account = account();
        account.setId(id);
        assertEquals(Long.valueOf(id), new UserSession(account).getAuthenticatedUserId());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 7, Long.MAX_VALUE})
    void capturesNonNegativeSecurityVersion(long version) {
        UserAccount account = account();
        account.setSecurityVersion(version);
        assertEquals(version, new UserSession(account).getAuthenticatedSecurityVersion());
    }

    @Test
    void subsequentAccountChangesCannotReplaceCapturedIdentityOrVersion() {
        UserAccount account = account();
        account.setSecurityVersion(7);
        UserSession session = new UserSession(account);

        session.getUserAccount().setId(99L);
        session.getUserAccount().setSecurityVersion(8);
        session.getUserAccount().setRole(UserRole.WORKER);
        session.getUserAccount().setSellerId("SELLER-CHANGED");

        assertEquals(1L, session.getAuthenticatedUserId());
        assertEquals(7, session.getAuthenticatedSecurityVersion());
        assertEquals(99L, session.getUserAccount().getId());
        assertEquals(8, session.getUserAccount().getSecurityVersion());
    }

    @Test
    void existingConstructorPreservesAccountReferenceLoginTimeAndActivity() {
        UserAccount account = account();
        LocalDateTime before = LocalDateTime.now();
        UserSession session = new UserSession(account);

        assertSame(account, session.getUserAccount());
        assertFalse(session.getLoginTime().isBefore(before));
        assertFalse(session.getLoginTime().isAfter(LocalDateTime.now()));
        assertTrue(session.isActive());
        assertEquals(0, session.getAuthenticatedSecurityVersion());
    }

    @Test
    void rejectsNullAccountAndMissingId() {
        assertThrows(IllegalArgumentException.class, () -> new UserSession(null));
        UserAccount account = account();
        account.setId(null);
        assertThrows(IllegalArgumentException.class, () -> new UserSession(account));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveIds(long id) {
        UserAccount account = account();
        account.setId(id);
        assertThrows(IllegalArgumentException.class, () -> new UserSession(account));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsAnAccountReportingANegativeVersion(long version) {
        // El setter del modelo ya rechaza negativos; este doble comprueba la defensa de la sesión.
        UserAccount invalid = new UserAccount(1L, "fixture", "opaque", UserRole.ADMINISTRATOR, null) {
            @Override
            public long getSecurityVersion() { return version; }
        };
        assertThrows(IllegalArgumentException.class, () -> new UserSession(invalid));
    }

    @Test
    void invalidationRemainsPermanentWithoutDeactivatingTheAccountOrChangingCapture() {
        UserAccount account = account();
        account.setSecurityVersion(3);
        UserSession session = new UserSession(account);
        session.invalidate();
        session.invalidate();
        account.setActive(false);
        account.setActive(true);
        account.setSecurityVersion(4);

        assertFalse(session.isActive());
        assertTrue(account.isActive());
        assertEquals(1L, session.getAuthenticatedUserId());
        assertEquals(3, session.getAuthenticatedSecurityVersion());
    }

    @Test
    void inactiveAccountStillConstructsAnInactiveSessionForCompatibility() {
        UserAccount account = account();
        account.setActive(false);
        UserSession session = new UserSession(account);
        assertFalse(session.isActive());
        assertEquals(1L, session.getAuthenticatedUserId());
    }

    @Test
    void onlyANewSessionCapturesANewerVersionAndInvalidationDoesNotAffectIt() {
        UserAccount account = account();
        UserSession original = new UserSession(account);
        original.invalidate();
        account.setSecurityVersion(1);
        UserSession next = new UserSession(account);

        assertFalse(original.isActive());
        assertTrue(next.isActive());
        assertEquals(0, original.getAuthenticatedSecurityVersion());
        assertEquals(1, next.getAuthenticatedSecurityVersion());
    }

    private static UserAccount account() {
        return new UserAccount(1L, "fixture", "opaque-test-hash", UserRole.ADMINISTRATOR, null);
    }
}
