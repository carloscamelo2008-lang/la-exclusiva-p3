package com.laexclusiva.repository;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.service.AuthorizationService;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryUserRepositoryTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 1, 2, 3, 4);
    private InMemoryUserRepository users;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
    }

    private static UserAccount account(String name) {
        UserAccount account = new UserAccount(null, name, "opaque-test-hash", UserRole.WORKER, "SEL-TEST");
        account.setCreatedAt(CREATED);
        return account;
    }

    private UserAccount stored(String name) {
        return users.findByUsername(name).orElseThrow();
    }

    @Test
    void saveCopiesEveryAttributeAndResetsTheRequestedVersion() {
        UserAccount input = account("member");
        input.setActive(false);
        input.setSecurityVersion(99);
        users.save(input);
        Long assignedId = input.getId();
        assertNotNull(assignedId);
        assertEquals(0, input.getSecurityVersion());

        input.setId(100L);
        input.setUsername("mutated");
        input.setPasswordHash("mutated-hash");
        input.setRole(UserRole.ADMINISTRATOR);
        input.setActive(true);
        input.setSellerId(null);
        input.setCreatedAt(CREATED.plusYears(1));
        input.setSecurityVersion(88);

        UserAccount saved = users.findById(assignedId.toString()).orElseThrow();
        assertNotSame(input, saved);
        assertEquals(assignedId, saved.getId());
        assertEquals("member", saved.getUsername());
        assertEquals("opaque-test-hash", saved.getPasswordHash());
        assertEquals(UserRole.WORKER, saved.getRole());
        assertFalse(saved.isActive());
        assertEquals("SEL-TEST", saved.getSellerId());
        assertEquals(CREATED, saved.getCreatedAt());
        assertEquals(0, saved.getSecurityVersion());
        assertTrue(users.findByUsername("mutated").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "username", "all"})
    void everyReadReturnsAnIndependentCopyIncludingTheVersion(String lookup) {
        UserAccount input = account("member");
        users.save(input);
        input.setActive(false);
        users.update(input);
        UserAccount found = switch (lookup) {
            case "id" -> users.findById(input.getId().toString()).orElseThrow();
            case "username" -> stored("member");
            default -> users.findAll().get(0);
        };
        assertEquals(1, found.getSecurityVersion());
        assertEquals(CREATED, found.getCreatedAt());
        assertEquals(UserRole.WORKER, found.getRole());
        assertEquals("SEL-TEST", found.getSellerId());
        found.setActive(true);
        found.setUsername("changed");
        found.setSecurityVersion(44);

        UserAccount unchanged = stored("member");
        assertNotSame(found, unchanged);
        assertFalse(unchanged.isActive());
        assertEquals(1, unchanged.getSecurityVersion());
    }

    @Test
    void modifyingAListDoesNotRemoveStoredAccounts() {
        users.save(account("member"));
        List<UserAccount> list = users.findAll();
        list.clear();
        assertEquals(1, users.findAll().size());
    }

    enum SecurityField { ROLE, PASSWORD, ACTIVE, SELLER }

    @ParameterizedTest
    @EnumSource(SecurityField.class)
    void eachSecurityChangeIncrementsOnceAndStoresADefensiveCopy(SecurityField field) {
        UserAccount input = account("member");
        users.save(input);
        switch (field) {
            case ROLE -> input.setRole(UserRole.ADMINISTRATOR);
            case PASSWORD -> input.setPasswordHash("new-test-hash");
            case ACTIVE -> input.setActive(false);
            case SELLER -> input.setSellerId("SEL-OTHER");
        }
        users.update(input);
        assertEquals(1, input.getSecurityVersion());
        UserAccount saved = stored("member");
        assertEquals(1, saved.getSecurityVersion());
        assertEquals(input.getRole(), saved.getRole());
        assertEquals(input.getPasswordHash(), saved.getPasswordHash());
        assertEquals(input.isActive(), saved.isActive());
        assertEquals(input.getSellerId(), saved.getSellerId());
        input.setUsername("after-update");
        input.setSecurityVersion(44);
        assertEquals(1, stored("member").getSecurityVersion());
        assertTrue(users.findByUsername("after-update").isEmpty());
    }

    @Test
    void multipleSecurityChangesIncrementOnlyOnce() {
        UserAccount input = account("member");
        users.save(input);
        input.setRole(UserRole.ADMINISTRATOR);
        input.setPasswordHash("new-test-hash");
        input.setActive(false);
        input.setSellerId(null);
        users.update(input);
        assertEquals(1, stored("member").getSecurityVersion());
        assertEquals(1, input.getSecurityVersion());
    }

    @Test
    void sellerTransitionsToAndFromNullAreVersionedButRepeatedNullIsNot() {
        UserAccount input = account("member");
        input.setSellerId(null);
        users.save(input);
        users.update(input);
        assertEquals(0, input.getSecurityVersion());
        input.setSellerId("SEL-NEW");
        users.update(input);
        assertEquals(1, input.getSecurityVersion());
        input.setSellerId(null);
        users.update(input);
        assertEquals(2, input.getSecurityVersion());
        users.update(input);
        assertEquals(2, stored("member").getSecurityVersion());
    }

    @Test
    void noOpAndUsernameOnlyChangesPreserveTheCurrentVersionAndCreationTime() {
        UserAccount input = account("member");
        users.save(input);
        input.setActive(false);
        users.update(input);
        users.update(input);
        assertEquals(1, input.getSecurityVersion());
        input.setUsername("  Renamed  ");
        input.setCreatedAt(CREATED.plusYears(2));
        users.update(input);
        UserAccount saved = stored("RENAMED");
        assertEquals("  Renamed  ", saved.getUsername());
        assertEquals(1, saved.getSecurityVersion());
        assertEquals(CREATED, saved.getCreatedAt());
        assertTrue(users.findByUsername("member").isEmpty());
    }

    @Test
    void staleOrForgedVersionsCannotOverwriteTheAccountOrMutateInputMetadata() {
        users.save(account("member"));
        UserAccount stale = stored("member");
        UserAccount winner = stored("member");
        winner.setSellerId("SEL-WINNER");
        users.update(winner);
        stale.setId(winner.getId());
        stale.setUsername("lost");
        stale.setRole(UserRole.ADMINISTRATOR);
        stale.setCreatedAt(CREATED.plusYears(1));

        assertThrows(ConcurrentModificationException.class, () -> users.update(stale));
        assertEquals(0, stale.getSecurityVersion());
        assertEquals(CREATED.plusYears(1), stale.getCreatedAt());
        assertEquals(winner.getId(), stale.getId());
        assertEquals(UserRole.WORKER, stored("member").getRole());
        assertEquals("SEL-WINNER", stored("member").getSellerId());
        assertEquals(1, stored("member").getSecurityVersion());
        assertTrue(users.findByUsername("lost").isEmpty());

        stale.setSecurityVersion(99);
        assertThrows(ConcurrentModificationException.class, () -> users.update(stale));
        assertEquals(99, stale.getSecurityVersion());
        assertEquals(1, stored("member").getSecurityVersion());
    }

    @Test
    void duplicateSavePreservesInputAndDoesNotConsumeTheNextId() {
        UserAccount first = account("  Member  ");
        users.save(first);
        UserAccount duplicate = account("MEMBER");
        duplicate.setSecurityVersion(7);
        assertThrows(IllegalArgumentException.class, () -> users.save(duplicate));
        assertNull(duplicate.getId());
        assertEquals(7, duplicate.getSecurityVersion());
        UserAccount next = account("next");
        users.save(next);
        assertEquals(first.getId() + 1, next.getId());
        assertEquals("  Member  ", stored("member").getUsername());
    }

    @Test
    void duplicateUpdatePreservesBothAccountsAndInputVersion() {
        users.save(account("first"));
        users.save(account("second"));
        UserAccount input = stored("second");
        input.setUsername(" FIRST ");
        input.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> users.update(input));
        assertEquals(0, input.getSecurityVersion());
        assertTrue(stored("second").isActive());
        assertEquals(0, stored("second").getSecurityVersion());
        assertEquals(2, users.findAll().size());
    }

    @Test
    void manualIdsAdvanceTheGeneratorAndDuplicateIdsNeverReplaceAccounts() {
        UserAccount historical = account("historical");
        historical.setId(50L);
        users.save(historical);
        UserAccount duplicate = account("duplicate-id");
        duplicate.setId(50L);
        duplicate.setSecurityVersion(9);
        assertThrows(IllegalArgumentException.class, () -> users.save(duplicate));
        assertEquals(50L, duplicate.getId());
        assertEquals(9, duplicate.getSecurityVersion());
        UserAccount next = account("next");
        users.save(next);
        assertEquals(51L, next.getId());
        assertEquals("historical", users.findById("50").orElseThrow().getUsername());
    }

    @Test
    void finalAutomaticIdCanBeUsedOnceAndExhaustionDoesNotWrapOrMutateInput() {
        UserAccount historical = account("historical");
        historical.setId(Long.MAX_VALUE - 1);
        users.save(historical);
        UserAccount last = account("last");
        users.save(last);
        assertEquals(Long.MAX_VALUE, last.getId());
        UserAccount exhausted = account("exhausted");
        exhausted.setSecurityVersion(7);
        assertThrows(ArithmeticException.class, () -> users.save(exhausted));
        assertNull(exhausted.getId());
        assertEquals(7, exhausted.getSecurityVersion());
        assertEquals(2, users.findAll().size());
        assertEquals("last", users.findById(Long.toString(Long.MAX_VALUE)).orElseThrow().getUsername());
    }

    @Test
    void explicitMaximumIdDoesNotOverflowAndOtherExplicitIdsRemainAvailable() {
        UserAccount maximum = account("maximum");
        maximum.setId(Long.MAX_VALUE);
        users.save(maximum);
        assertThrows(ArithmeticException.class, () -> users.save(account("automatic")));
        UserAccount manual = account("manual");
        manual.setId(2L);
        users.save(manual);
        assertEquals(0, manual.getSecurityVersion());
        assertEquals(2, users.findAll().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void versionOverflowLeavesTheStoredAccountAndInputUnchanged() throws Exception {
        UserAccount input = account("member");
        users.save(input);
        // Estado límite inalcanzable mediante un número práctico de actualizaciones; sin API de fixtures pública.
        var field = InMemoryUserRepository.class.getDeclaredField("storage");
        field.setAccessible(true);
        Map<Long, UserAccount> storage = (Map<Long, UserAccount>) field.get(users);
        storage.get(input.getId()).setSecurityVersion(Long.MAX_VALUE);
        input = stored("member");
        input.setActive(false);
        UserAccount attempted = input;
        assertThrows(ArithmeticException.class, () -> users.update(attempted));
        assertEquals(Long.MAX_VALUE, attempted.getSecurityVersion());
        assertTrue(stored("member").isActive());
        assertEquals(Long.MAX_VALUE, stored("member").getSecurityVersion());
        UserAccount noOp = stored("member");
        users.update(noOp);
        assertEquals(Long.MAX_VALUE, noOp.getSecurityVersion());
    }

    @Test
    void invalidInputsAndMissingAccountsKeepExistingExceptionSemantics() {
        assertThrows(IllegalArgumentException.class, () -> users.save(null));
        assertThrows(IllegalArgumentException.class, () -> users.update(null));
        UserAccount missingId = account("missing-id");
        assertThrows(IllegalArgumentException.class, () -> users.update(missingId));
        UserAccount missing = account("missing");
        missing.setId(9L);
        assertThrows(IllegalArgumentException.class, () -> users.update(missing));
        UserAccount invalid = account(" ");
        assertThrows(IllegalArgumentException.class, () -> users.save(invalid));
        assertNull(invalid.getId());
        assertTrue(users.findById("invalid").isEmpty());
        assertTrue(users.findById(null).isEmpty());
        assertTrue(users.findByUsername(" ").isEmpty());
        assertTrue(users.findByUsername(null).isEmpty());
        assertDoesNotThrow(() -> users.delete("missing"));
        assertTrue(users.findAll().isEmpty());
    }

    @Test
    void deleteDoesNotDependOnMutableSnapshots() {
        UserAccount input = account("member");
        users.save(input);
        UserAccount detached = stored("member");
        detached.setId(100L);
        users.delete(input.getId().toString());
        assertTrue(users.findAll().isEmpty());
        assertDoesNotThrow(() -> users.delete(input.getId().toString()));
    }

    @Test
    void concurrentSecurityUpdatesHaveExactlyOneWinner() throws Exception {
        users.save(account("member"));
        UserAccount first = stored("member");
        UserAccount second = stored("member");
        first.setPasswordHash("first-hash");
        second.setPasswordHash("second-hash");
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Boolean> results = race(() -> updateAfterBarrier(first, barrier),
                () -> updateAfterBarrier(second, barrier));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(results.get(0) ? "first-hash" : "second-hash", stored("member").getPasswordHash());
        assertEquals(1, stored("member").getSecurityVersion());
        assertEquals(results.get(0) ? 1 : 0, first.getSecurityVersion());
        assertEquals(results.get(1) ? 1 : 0, second.getSecurityVersion());
    }

    @Test
    void concurrentNormalizedCreatesHaveExactlyOneWinner() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        UserAccount first = account("  Member  ");
        UserAccount second = account("MEMBER");
        List<Boolean> results = race(() -> saveAfterBarrier(first, barrier),
                () -> saveAfterBarrier(second, barrier));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, users.findAll().size());
        assertNull(results.get(0) ? second.getId() : first.getId());
        assertEquals(0, stored("member").getSecurityVersion());
    }

    @Test
    void concurrentRenamesCannotIntroduceDuplicateUsernames() throws Exception {
        users.save(account("first"));
        users.save(account("second"));
        UserAccount first = stored("first");
        UserAccount second = stored("second");
        first.setUsername("shared");
        second.setUsername(" SHARED ");
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Boolean> results = race(() -> renameAfterBarrier(first, barrier),
                () -> renameAfterBarrier(second, barrier));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(2, users.findAll().size());
        assertEquals(1, users.findAll().stream().filter(u -> u.getUsername().trim().equalsIgnoreCase("shared")).count());
        assertEquals(0, stored("shared").getSecurityVersion());
    }

    @Test
    void authenticationReadsCurrentAccountsAndPreservesRoleAndSeller() {
        UserAccount input = account("member");
        input.setPasswordHash(PasswordHasher.hash("Memory-Version-Test-Only"));
        users.save(input);
        AuthenticationService authentication = new AuthenticationService(users);
        var session = authentication.createSession(" MEMBER ", "Memory-Version-Test-Only").orElseThrow();
        assertEquals(UserRole.WORKER, session.getUserAccount().getRole());
        assertEquals("SEL-TEST", session.getUserAccount().getSellerId());
        assertFalse(new AuthorizationService().hasPermission(session, Permission.MANAGE_USERS));
        input.setActive(false);
        users.update(input);
        assertTrue(authentication.createSession("member", "Memory-Version-Test-Only").isEmpty());
        assertEquals(0, session.getUserAccount().getSecurityVersion());
        assertEquals(1, stored("member").getSecurityVersion());
        // La revocación de una sesión ya emitida corresponde a una actividad posterior.
    }

    private boolean updateAfterBarrier(UserAccount account, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            users.update(account);
            return true;
        } catch (ConcurrentModificationException expected) {
            return false;
        }
    }

    private boolean saveAfterBarrier(UserAccount account, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            users.save(account);
            return true;
        } catch (IllegalArgumentException duplicate) {
            return false;
        }
    }

    private boolean renameAfterBarrier(UserAccount account, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            users.update(account);
            return true;
        } catch (IllegalArgumentException duplicate) {
            return false;
        }
    }

    private static List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(first);
            var right = executor.submit(second);
            return List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Los escritores deben terminar.");
        }
    }
}
