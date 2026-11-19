package com.laexclusiva.model;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UserAccountTest {
    @Test
    void emptyConstructorPreservesDefaultsAndStartsAtVersionZero() {
        UserAccount account = new UserAccount();

        assertEquals(0L, account.getSecurityVersion());
        assertTrue(account.isActive());
        assertNotNull(account.getCreatedAt());
        assertNull(account.getId());
        assertNull(account.getUsername());
        assertNull(account.getPasswordHash());
        assertNull(account.getRole());
        assertNull(account.getSellerId());
    }

    @Test
    void existingParameterizedConstructorPreservesAttributesAndStartsAtVersionZero() {
        UserAccount account = new UserAccount(42L, "member", "opaque-test-hash", UserRole.WORKER, "SEL-TEST");

        assertEquals(0L, account.getSecurityVersion());
        assertEquals(42L, account.getId());
        assertEquals("member", account.getUsername());
        assertEquals("opaque-test-hash", account.getPasswordHash());
        assertEquals(UserRole.WORKER, account.getRole());
        assertEquals("SEL-TEST", account.getSellerId());
        assertTrue(account.isActive());
        assertNotNull(account.getCreatedAt());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, Long.MAX_VALUE})
    void acceptsNonNegativePersistedVersionsWithoutChangingOtherAttributes(long version) {
        UserAccount account = new UserAccount(42L, "member", "opaque-test-hash", UserRole.WORKER, "SEL-TEST");
        LocalDateTime createdAt = account.getCreatedAt();
        account.setActive(false);
        account.setSecurityVersion(version);

        assertEquals(version, account.getSecurityVersion());
        assertEquals(42L, account.getId());
        assertEquals("member", account.getUsername());
        assertEquals("opaque-test-hash", account.getPasswordHash());
        assertEquals(UserRole.WORKER, account.getRole());
        assertEquals("SEL-TEST", account.getSellerId());
        assertEquals(createdAt, account.getCreatedAt());
        assertFalse(account.isActive());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeVersionsWithoutReplacingThePreviousValue(long version) {
        UserAccount account = new UserAccount();
        account.setSecurityVersion(7L);

        assertThrows(IllegalArgumentException.class, () -> account.setSecurityVersion(version));
        assertEquals(7L, account.getSecurityVersion());
    }

    @Test
    void existingSettersDoNotAutomaticallyIncrementTheSecurityVersion() {
        UserAccount account = new UserAccount();
        account.setSecurityVersion(7L);
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 2, 3, 4);
        account.setId(42L);
        account.setUsername("updated");
        account.setPasswordHash("updated-opaque-test-hash");
        account.setRole(UserRole.ADMINISTRATOR);
        account.setActive(false);
        account.setSellerId("SEL-UPDATED");
        account.setCreatedAt(createdAt);

        assertEquals(7L, account.getSecurityVersion());
        assertEquals(42L, account.getId());
        assertEquals("updated", account.getUsername());
        assertEquals("updated-opaque-test-hash", account.getPasswordHash());
        assertEquals(UserRole.ADMINISTRATOR, account.getRole());
        assertFalse(account.isActive());
        assertEquals("SEL-UPDATED", account.getSellerId());
        assertEquals(createdAt, account.getCreatedAt());
    }
}
