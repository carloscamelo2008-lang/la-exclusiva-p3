package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ActiveAdministratorPolicyTest {
    private final ActiveAdministratorPolicy policy = new ActiveAdministratorPolicy();

    @Test
    void rejectsDeactivatingTheOnlyActiveAdministratorWithoutMutatingAccounts() {
        UserAccount current = account(UserRole.ADMINISTRATOR, true);
        UserAccount proposed = account(UserRole.ADMINISTRATOR, false);
        assertThrows(IllegalStateException.class, () -> policy.validateChange(current, proposed, 1));
        assertTrue(current.isActive());
        assertFalse(proposed.isActive());
        assertEquals(0, current.getSecurityVersion());
        assertEquals(0, proposed.getSecurityVersion());
    }

    @Test
    void rejectsDemotingTheOnlyActiveAdministrator() {
        assertThrows(IllegalStateException.class, () -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, true), account(UserRole.WORKER, true), 1));
    }

    @Test
    void permitsDeactivationWhenAnotherAdministratorRemains() {
        assertDoesNotThrow(() -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, true), account(UserRole.ADMINISTRATOR, false), 2));
    }

    @Test
    void permitsDemotionWhenAnotherAdministratorRemains() {
        assertDoesNotThrow(() -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, true), account(UserRole.WORKER, true), 2));
    }

    @Test
    void permitsDeactivatingAWorkerEvenWithNoAdministrators() {
        assertDoesNotThrow(() -> policy.validateChange(
                account(UserRole.WORKER, true), account(UserRole.WORKER, false), 0));
    }

    @Test
    void permitsChangingPasswordWithoutRemovingAdministrator() {
        UserAccount current = account(UserRole.ADMINISTRATOR, true);
        UserAccount proposed = account(UserRole.ADMINISTRATOR, true);
        proposed.setPasswordHash("another-opaque-test-hash");
        assertDoesNotThrow(() -> policy.validateChange(current, proposed, 1));
        assertEquals("opaque-test-hash", current.getPasswordHash());
        assertEquals("another-opaque-test-hash", proposed.getPasswordHash());
    }

    @Test
    void permitsChangingUsernameWithoutRemovingAdministrator() {
        UserAccount proposed = account(UserRole.ADMINISTRATOR, true);
        proposed.setUsername("renamed");
        assertDoesNotThrow(() -> policy.validateChange(account(UserRole.ADMINISTRATOR, true), proposed, 1));
        assertEquals("renamed", proposed.getUsername());
    }

    @Test
    void permitsPromotionWhenNoAdministratorExists() {
        assertDoesNotThrow(() -> policy.validateChange(
                account(UserRole.WORKER, true), account(UserRole.ADMINISTRATOR, true), 0));
    }

    @Test
    void permitsReactivationWhenNoAdministratorExists() {
        assertDoesNotThrow(() -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, false), account(UserRole.ADMINISTRATOR, true), 0));
    }

    @Test
    void permitsChangingAnInactiveAdministratorToWorker() {
        assertDoesNotThrow(() -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, false), account(UserRole.WORKER, false), 0));
    }

    @Test
    void permitsNoOpForTheOnlyAdministrator() {
        assertDoesNotThrow(() -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, true), account(UserRole.ADMINISTRATOR, true), 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeCountsEvenWhenNoAdministratorIsRemoved(long count) {
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(
                account(UserRole.WORKER, true), account(UserRole.WORKER, true), count));
    }

    @Test
    void rejectsZeroCountForAnExistingActiveAdministratorEvenForNoOp() {
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, true), account(UserRole.ADMINISTRATOR, true), 0));
    }

    @Test
    void rejectsNullAccounts() {
        UserAccount valid = account(UserRole.ADMINISTRATOR, true);
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(null, valid, 1));
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(valid, null, 1));
    }

    @Test
    void rejectsMissingRolesOnEitherAccount() {
        UserAccount valid = account(UserRole.ADMINISTRATOR, true);
        UserAccount invalid = account(null, true);
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(invalid, valid, 1));
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(valid, invalid, 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsInvalidIdsOnEitherAccount(long id) {
        UserAccount valid = account(UserRole.ADMINISTRATOR, true);
        UserAccount invalid = account(UserRole.ADMINISTRATOR, true);
        invalid.setId(id);
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(invalid, valid, 1));
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(valid, invalid, 1));
    }

    @Test
    void rejectsMissingIdsOnEitherAccount() {
        UserAccount valid = account(UserRole.ADMINISTRATOR, true);
        UserAccount invalid = account(UserRole.ADMINISTRATOR, true);
        invalid.setId(null);
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(invalid, valid, 1));
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(valid, invalid, 1));
    }

    @Test
    void rejectsDifferentAccountIds() {
        UserAccount proposed = account(UserRole.WORKER, true);
        proposed.setId(2L);
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange(
                account(UserRole.ADMINISTRATOR, true), proposed, 2));
    }

    private static UserAccount account(UserRole role, boolean active) {
        UserAccount account = new UserAccount(1L, "member", "opaque-test-hash", role, null);
        account.setActive(active);
        return account;
    }
}
