package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.security.PasswordHasher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UserManagementServiceTest {
    private final InMemoryUserRepository storage = new InMemoryUserRepository();
    private final RecordingUsers users = new RecordingUsers();
    private final UserAdministrationBoundary boundary = new UserAdministrationBoundary() {
        @Override public <T> T execute(Supplier<T> operation) {
            synchronized (storage) {
                users.events.add("enter");
                try { return operation.get(); }
                finally { users.events.add("exit"); }
            }
        }
    };
    private final UserManagementService service = new UserManagementService(users, boundary, new AuthorizationService());

    UserManagementServiceTest() {
        storage.save(account(1, UserRole.ADMINISTRATOR));
        storage.save(account(2, UserRole.ADMINISTRATOR));
        storage.save(account(3, UserRole.WORKER));
    }

    @Test
    void rejectsMissingDependenciesWithoutRepositoryCalls() {
        assertThrows(IllegalArgumentException.class, () -> new UserManagementService(null, boundary, new AuthorizationService()));
        assertThrows(IllegalArgumentException.class, () -> new UserManagementService(users, null, new AuthorizationService()));
        assertThrows(IllegalArgumentException.class, () -> new UserManagementService(users, boundary, null));
        assertTrue(users.events.isEmpty());
    }

    @Test
    void changesRoleWithinOneBoundaryUsingACopyWithoutPostWriteReads() {
        UserAccount original = stored("3");
        users.forbidPostWriteReads = true;
        UserSession actor = session("1");
        var result = service.changeRole(actor, "3", 0, UserRole.ADMINISTRATOR);
        assertEquals(List.of("enter", "find:1", "find:3", "all", "update", "exit"), users.events);
        assertEquals(1, users.updates);
        assertEquals(UserRole.ADMINISTRATOR, result.role());
        assertEquals(3L, result.id());
        assertTrue(result.active());
        assertEquals(1, result.securityVersion());
        assertUnchangedExceptSecurity(original, stored("3"));
        assertEquals(UserRole.WORKER, users.loadedTarget.getRole());
        assertEquals(0, users.loadedTarget.getSecurityVersion());
        assertNotSame(users.loadedTarget, users.submitted);
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
    }

    @Test
    void changesOnlyActivityAndPreservesAllOtherAttributes() {
        UserAccount original = stored("3");
        var result = service.setActive(session("1"), "3", 0, false);
        assertFalse(result.active());
        assertEquals(UserRole.WORKER, result.role());
        assertEquals(1, result.securityVersion());
        assertUnchangedExceptSecurity(original, stored("3"));
        assertEquals(original.getRole(), stored("3").getRole());
        assertTrue(users.loadedTarget.isActive());
        assertEquals(1, users.updates);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsNullOrInvalidatedSessionsWithoutWrites(boolean roleChange) {
        assertThrows(SecurityException.class, () -> remove(null, "3", 0, roleChange));
        UserSession actor = session("1");
        actor.invalidate();
        assertThrows(SecurityException.class, () -> remove(actor, "3", 0, roleChange));
        assertEquals(0, users.updates);
        assertFalse(users.events.stream().anyMatch(event -> event.startsWith("find:")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsWorkerWithoutManageUsers(boolean roleChange) {
        assertThrows(SecurityException.class, () -> remove(session("3"), "2", 0, roleChange));
        assertEquals(0, users.updates);
    }

    @Test
    void rejectsAnActorDeletedAfterLogin() {
        UserSession actor = session("1");
        storage.delete("1");
        assertThrows(SecurityException.class, () -> service.setActive(actor, "3", 0, false));
        assertEquals(0, users.updates);
    }

    @Test
    void rejectsAnInactiveActor() {
        UserSession actor = session("1");
        UserAccount current = stored("1");
        current.setActive(false);
        storage.update(current);
        assertThrows(SecurityException.class, () -> service.changeRole(actor, "3", 0, UserRole.ADMINISTRATOR));
        assertEquals(0, users.updates);
    }

    @Test
    void rejectsAnObsoleteActorVersionEvenWhenStillAdministrator() {
        UserSession actor = session("1");
        UserAccount current = stored("1");
        current.setSellerId("CHANGED-SELLER");
        storage.update(current);
        assertThrows(SecurityException.class, () -> service.setActive(actor, "3", 0, false));
        assertEquals(0, users.updates);
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
    }

    @Test
    void ignoresMutatedActorSnapshotWhenPersistentIdentityIsAuthorized() {
        UserSession actor = session("1");
        actor.getUserAccount().setId(3L);
        actor.getUserAccount().setRole(UserRole.WORKER);
        actor.getUserAccount().setActive(false);
        actor.getUserAccount().setSecurityVersion(99);
        assertFalse(service.setActive(actor, "3", 0, false).active());
        assertTrue(users.events.contains("find:1"));
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
    }

    @Test
    void rejectsAnAbsentTarget() {
        assertThrows(IllegalArgumentException.class, () -> service.setActive(session("1"), "999", 0, false));
        assertEquals(0, users.updates);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0", "-1", "not-an-id", "9223372036854775808"})
    void rejectsInvalidTargetIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> service.setActive(session("1"), id, 0, false));
        assertEquals(0, users.updates);
        assertFalse(users.events.contains("all"));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void rejectsNegativeExpectedVersion(long version) {
        assertThrows(IllegalArgumentException.class, () -> service.changeRole(session("1"), "3", version, UserRole.ADMINISTRATOR));
        assertEquals(0, users.updates);
    }

    @Test
    void rejectsNullRoleAfterAuthorizingAndBeforeLoadingTarget() {
        assertThrows(IllegalArgumentException.class, () -> service.changeRole(session("1"), "3", 0, null));
        assertEquals(List.of("enter", "find:1", "exit"), users.events);
        assertEquals(0, users.updates);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cannotRemoveTheLastActiveAdministrator(boolean roleChange) {
        storage.delete("2");
        assertThrows(IllegalStateException.class, () -> remove(session("1"), "1", 0, roleChange));
        assertTrue(stored("1").isActive());
        assertEquals(UserRole.ADMINISTRATOR, stored("1").getRole());
        assertEquals(0, stored("1").getSecurityVersion());
        assertEquals(0, users.updates);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void permitsRemovingAnAdministratorWhenAnotherRemains(boolean roleChange) {
        var result = remove(session("1"), "2", 0, roleChange);
        assertEquals(1, result.securityVersion());
        assertEquals(roleChange ? UserRole.WORKER : UserRole.ADMINISTRATOR, result.role());
        assertEquals(roleChange, result.active());
        assertEquals(1, activeAdministrators());
        assertEquals(1, users.updates);
    }

    @Test
    void reactivatesAnInactiveAdministrator() {
        UserAccount target = stored("2");
        target.setActive(false);
        storage.update(target);
        var result = service.setActive(session("1"), "2", 1, true);
        assertTrue(result.active());
        assertEquals(UserRole.ADMINISTRATOR, result.role());
        assertEquals(2, result.securityVersion());
        assertEquals(2, activeAdministrators());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsObsoleteTargetVersionWithoutOverwritingIt(boolean roleChange) {
        UserAccount target = stored("3");
        target.setSellerId("CONCURRENT-SELLER");
        storage.update(target);
        assertThrows(ConcurrentModificationException.class, () -> remove(session("1"), "3", 0, roleChange));
        assertEquals("CONCURRENT-SELLER", stored("3").getSellerId());
        assertEquals(1, stored("3").getSecurityVersion());
        assertTrue(stored("3").isActive());
        assertEquals(0, users.updates);
    }

    @Test
    void noOpRoleAndActivityPreserveVersionAndStillValidate() {
        assertEquals(0, service.changeRole(session("1"), "3", 0, UserRole.WORKER).securityVersion());
        assertEquals(0, service.setActive(session("1"), "3", 0, true).securityVersion());
        assertEquals(2, users.updates);
        assertEquals(0, stored("3").getSecurityVersion());
    }

    @Test
    void resultContainsOnlySafeImmutableComponents() {
        var result = service.changeRole(session("1"), "3", 0, UserRole.ADMINISTRATOR);
        assertTrue(result.getClass().isRecord());
        assertEquals(List.of("id", "role", "active", "securityVersion"),
                Arrays.stream(result.getClass().getRecordComponents()).map(component -> component.getName()).toList());
        assertFalse(result.toString().contains("opaque-test-hash"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"find:1", "find:3", "all", "update"})
    void repositoryFailuresStopOperationWithoutRetryOrPartialMemoryWrite(String stage) {
        users.failureStage = stage;
        RuntimeException failure = users.failure;
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.setActive(session("1"), "3", 0, false)));
        assertEquals(1, users.events.stream().filter(stage::equals).count());
        assertEquals(stage.equals("update") ? 1 : 0, users.updates);
        assertTrue(stored("3").isActive());
        assertEquals(0, stored("3").getSecurityVersion());
    }

    @Test
    void repositoryVersionConflictIsNotRetried() {
        users.failureStage = "update";
        users.failure = new ConcurrentModificationException("simulated version conflict");
        assertSame(users.failure, assertThrows(ConcurrentModificationException.class,
                () -> service.setActive(session("1"), "3", 0, false)));
        assertEquals(1, users.updates);
        assertTrue(stored("3").isActive());
    }

    @Test
    void changingOwnRoleRevokesTheOldSessionForSubsequentOperations() {
        UserSession actor = session("1");
        assertEquals(1, service.changeRole(actor, "1", 0, UserRole.WORKER).securityVersion());
        assertTrue(actor.isValid());
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
        assertThrows(SecurityException.class, () -> service.setActive(actor, "3", 0, false));
        assertEquals(1, users.updates);
        assertTrue(stored("3").isActive());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void twoServicesCannotRemoveBothAdministratorsConcurrently(boolean roleChange) throws Exception {
        UserManagementService first = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService());
        UserManagementService second = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService());
        UserSession actor1 = session("1");
        UserSession actor2 = session("2");
        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = executor.submit(() -> compete(first, actor1, "1", roleChange, start));
            Future<Boolean> two = executor.submit(() -> compete(second, actor2, "2", roleChange, start));
            start.await(5, TimeUnit.SECONDS);
            boolean firstWon = one.get(10, TimeUnit.SECONDS);
            boolean secondWon = two.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertEquals(1, activeAdministrators());
            assertEquals(1, users.updates);
            assertEquals(1, stored("1").getSecurityVersion() + stored("2").getSecurityVersion());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @CsvSource({"ADMINISTRATOR,true", "ADMINISTRATOR,false", "WORKER,true", "WORKER,false"})
    void createsAccountsWithGeneratedMetadataAndVerifiableHash(UserRole role, boolean active) {
        users.allowCreate = true;
        users.forbidPostWriteReads = true;
        UserSession actor = session("1");
        LocalDateTime before = LocalDateTime.now();
        var request = new UserManagementService.CreateUserRequest("new-member", "Temporary-Creation-Test", role, active);
        var result = service.createUser(actor, request);
        assertEquals(List.of("enter", "find:1", "save", "exit"), users.events);
        assertEquals(1, users.saves);
        assertEquals(0, users.updates);
        assertEquals(4L, result.id());
        assertEquals(role, result.role());
        assertEquals(active, result.active());
        assertEquals(0, result.securityVersion());
        assertTrue(users.generatedIdRequested);
        UserAccount created = stored(result.id().toString());
        assertEquals("new-member", created.getUsername());
        assertEquals(role, created.getRole());
        assertEquals(active, created.isActive());
        assertNull(created.getSellerId());
        assertEquals(0, created.getSecurityVersion());
        assertFalse(created.getCreatedAt().isBefore(before));
        assertFalse(created.getCreatedAt().isAfter(LocalDateTime.now()));
        assertFalse(request.password().equals(created.getPasswordHash()));
        assertTrue(created.getPasswordHash().startsWith("$pbkdf2-sha256$i=210000$"));
        assertTrue(PasswordHasher.verify(request.password(), created.getPasswordHash()));
        assertFalse(result.toString().contains(request.password()));
        assertFalse(result.toString().contains(created.getPasswordHash()));
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
        assertTrue(actor.isValid());
    }

    @Test
    void creationRejectsNullOrInvalidatedSessionBeforeProcessingRequest() {
        assertThrows(SecurityException.class, () -> service.createUser(null, null));
        UserSession actor = session("1");
        actor.invalidate();
        assertThrows(SecurityException.class, () -> service.createUser(actor, request("new-member")));
        assertEquals(0, users.saves);
        assertFalse(users.events.stream().anyMatch(event -> event.startsWith("find:")));
    }

    @Test
    void creationRejectsWorkerBeforeProcessingInvalidCredentials() {
        assertThrows(SecurityException.class, () -> service.createUser(session("3"), null));
        assertEquals(0, users.saves);
        assertEquals(3, storage.findAll().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "inactive", "obsolete"})
    void creationRejectsAnActorNoLongerAuthorized(String state) {
        UserSession actor = session("1");
        if (state.equals("deleted")) storage.delete("1");
        else {
            UserAccount current = stored("1");
            if (state.equals("inactive")) current.setActive(false);
            else current.setSellerId("CHANGED-SELLER");
            storage.update(current);
        }
        assertThrows(SecurityException.class, () -> service.createUser(actor, request("new-member")));
        assertEquals(0, users.saves);
        assertTrue(storage.findByUsername("new-member").isEmpty());
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
    }

    @Test
    void creationCannotBootstrapAnEmptyRepositoryUsingAStaleSession() {
        UserSession actor = session("1");
        storage.delete("1");
        storage.delete("2");
        storage.delete("3");
        assertThrows(SecurityException.class, () -> service.createUser(actor,
                new UserManagementService.CreateUserRequest("initial-admin", "Temporary-Creation-Test", UserRole.ADMINISTRATOR, true)));
        assertTrue(storage.findAll().isEmpty());
        assertEquals(0, users.saves);
    }

    @Test
    void rejectsNullCreationRequestAfterAuthorization() {
        assertThrows(IllegalArgumentException.class, () -> service.createUser(session("1"), null));
        assertEquals(List.of("enter", "find:1", "exit"), users.events);
        assertEquals(0, users.saves);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "invalid\u0000name"})
    void rejectsInvalidCreationUsername(String username) {
        assertThrows(IllegalArgumentException.class, () -> service.createUser(session("1"), request(username)));
        assertEquals(0, users.saves);
    }

    @Test
    void rejectsCreationUsernameLongerThanSchemaLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.createUser(session("1"), request("x".repeat(101))));
        assertEquals(0, users.saves);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullOrEmptyCreationPassword(String password) {
        var request = new UserManagementService.CreateUserRequest("new-member", password, UserRole.WORKER, true);
        assertThrows(IllegalArgumentException.class, () -> service.createUser(session("1"), request));
        assertEquals(0, users.saves);
    }

    @Test
    void rejectsNullCreationRole() {
        var request = new UserManagementService.CreateUserRequest("new-member", "Temporary-Creation-Test", null, true);
        assertThrows(IllegalArgumentException.class, () -> service.createUser(session("1"), request));
        assertEquals(0, users.saves);
    }

    @Test
    void preservesExistingPasswordContractWithoutAddingComplexityRules() {
        users.allowCreate = true;
        var request = new UserManagementService.CreateUserRequest("new-member", " ", UserRole.WORKER, true);
        var result = service.createUser(session("1"), request);
        assertTrue(PasswordHasher.verify(" ", stored(result.id().toString()).getPasswordHash()));
        assertEquals(1, users.saves);
    }

    @Test
    void creationRequestHasOnlyPermittedComponentsAndRedactsItsText() {
        var request = request("new-member");
        assertTrue(request.getClass().isRecord());
        assertEquals(List.of("username", "password", "role", "active"),
                Arrays.stream(request.getClass().getRecordComponents()).map(component -> component.getName()).toList());
        assertFalse(request.toString().contains(request.password()));
        assertFalse(request.toString().contains(request.username()));
    }

    @Test
    void creationDuplicateUsesRepositoryUniquenessWithoutOverwritingOrRetrying() {
        users.allowCreate = true;
        UserAccount before = stored("3");
        assertThrows(IllegalArgumentException.class, () -> service.createUser(session("1"), request("  FIXTURE-3  ")));
        assertEquals(1, users.saves);
        assertEquals(List.of("enter", "find:1", "save", "exit"), users.events);
        assertEquals(3, storage.findAll().size());
        assertUnchangedExceptSecurity(before, stored("3"));
        assertEquals(before.getRole(), stored("3").getRole());
        assertEquals(before.isActive(), stored("3").isActive());
        assertEquals(0, stored("3").getSecurityVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"find:1", "save"})
    void creationPersistenceFailureDoesNotRetryOrCreateAnAccount(String stage) {
        users.allowCreate = true;
        users.failureStage = stage;
        UserSession actor = session("1");
        assertSame(users.failure, assertThrows(IllegalStateException.class,
                () -> service.createUser(actor, request("new-member"))));
        assertEquals(stage.equals("save") ? 1 : 0, users.saves);
        assertEquals(1, users.events.stream().filter(stage::equals).count());
        assertTrue(storage.findByUsername("new-member").isEmpty());
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
        assertTrue(actor.isValid());
    }

    @Test
    void samePasswordReceivesIndependentRandomSalts() {
        users.allowCreate = true;
        service.createUser(session("1"), request("first-created"));
        service.createUser(session("1"), request("second-created"));
        assertFalse(storage.findByUsername("first-created").orElseThrow().getPasswordHash()
                .equals(storage.findByUsername("second-created").orElseThrow().getPasswordHash()));
        assertEquals(2, users.saves);
    }

    @Test
    void twoServicesCreatingTheSameUsernameLeaveExactlyOneNewAccount() throws Exception {
        users.allowCreate = true;
        UserManagementService first = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService());
        UserManagementService second = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService());
        UserSession actor = session("1");
        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = executor.submit(() -> createAfterBarrier(first, actor, " Concurrent-Member ", start));
            Future<Boolean> two = executor.submit(() -> createAfterBarrier(second, actor, "concurrent-member", start));
            start.await(5, TimeUnit.SECONDS);
            boolean firstWon = one.get(10, TimeUnit.SECONDS);
            boolean secondWon = two.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertEquals(2, users.saves);
            assertEquals(4, storage.findAll().size());
            assertEquals(1, storage.findAll().stream().filter(user -> user.getUsername().trim()
                    .equalsIgnoreCase("concurrent-member")).count());
            UserAccount created = storage.findByUsername("concurrent-member").orElseThrow();
            assertTrue(created.isActive());
            assertEquals(UserRole.WORKER, created.getRole());
            assertEquals(0, created.getSecurityVersion());
            assertNull(created.getSellerId());
            assertTrue(PasswordHasher.verify(request("unused").password(), created.getPasswordHash()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void passwordResetChangesOnlyHashAndVersionWithinOneBoundary() {
        UserAccount before = preparePasswordTarget();
        users.forbidPostWriteReads = true;
        users.failureStage = "all";
        UserSession actor = session("1");
        var result = service.changePassword(actor, "3", before.getSecurityVersion(), "Replacement-Test-Only");
        assertEquals(List.of("enter", "find:1", "find:3", "update", "exit"), users.events);
        assertEquals(1, users.updates);
        assertEquals(0, users.saves);
        assertNotSame(users.loadedTarget, users.submitted);
        assertTrue(before.getPasswordHash().equals(users.loadedTarget.getPasswordHash()));
        UserAccount after = stored("3");
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getUsername(), after.getUsername());
        assertEquals(before.getRole(), after.getRole());
        assertEquals(before.isActive(), after.isActive());
        assertEquals(before.getSellerId(), after.getSellerId());
        assertEquals(before.getCreatedAt(), after.getCreatedAt());
        assertEquals(before.getSecurityVersion() + 1, after.getSecurityVersion());
        assertFalse(before.getPasswordHash().equals(after.getPasswordHash()));
        assertTrue(PasswordHasher.verify("Replacement-Test-Only", after.getPasswordHash()));
        assertFalse(PasswordHasher.verify("Original-Password-Test-Only", after.getPasswordHash()));
        assertEquals(after.getId(), result.id());
        assertEquals(after.getRole(), result.role());
        assertEquals(after.isActive(), result.active());
        assertEquals(after.getSecurityVersion(), result.securityVersion());
        assertFalse(result.toString().contains("Replacement-Test-Only"));
        assertFalse(result.toString().contains(after.getPasswordHash()));
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
    }

    @Test
    void passwordResetRejectsWorkerAndInvalidatedOrNullSessionBeforeProcessingCredentials() {
        assertThrows(SecurityException.class, () -> service.changePassword(session("3"), "2", 0, null));
        UserSession invalid = session("1");
        invalid.invalidate();
        assertThrows(SecurityException.class, () -> service.changePassword(invalid, "3", 0, null));
        assertThrows(SecurityException.class, () -> service.changePassword(null, "3", 0, null));
        assertEquals(0, users.updates);
        assertEquals(0, users.saves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "inactive", "obsolete"})
    void passwordResetRejectsAnActorNoLongerAuthorized(String state) {
        UserSession actor = session("1");
        if (state.equals("deleted")) storage.delete("1");
        else {
            UserAccount current = stored("1");
            if (state.equals("inactive")) current.setActive(false);
            else current.setSellerId("CHANGED-SELLER");
            storage.update(current);
        }
        assertThrows(SecurityException.class,
                () -> service.changePassword(actor, "3", 0, "Replacement-Test-Only"));
        assertEquals(0, users.updates);
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0", "-1", "invalid", "9223372036854775808"})
    void passwordResetRejectsInvalidTargetIds(String id) {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(session("1"), id, 0, "Replacement-Test-Only"));
        assertEquals(0, users.updates);
    }

    @Test
    void passwordResetRejectsMissingTargetAndNegativeExpectedVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(session("1"), "999", 0, "Replacement-Test-Only"));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(session("1"), "3", -1, "Replacement-Test-Only"));
        assertEquals(0, users.updates);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void passwordResetRejectsNullOrEmptyPassword(String password) {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(session("1"), "3", 0, password));
        assertEquals(List.of("enter", "find:1", "exit"), users.events);
        assertEquals(0, users.updates);
    }

    @Test
    void passwordResetRejectsOldVersionWithoutOverwritingHash() {
        UserAccount before = preparePasswordTarget();
        assertThrows(ConcurrentModificationException.class,
                () -> service.changePassword(session("1"), "3", 0, "Replacement-Test-Only"));
        assertTrue(before.getPasswordHash().equals(stored("3").getPasswordHash()));
        assertEquals(before.getSecurityVersion(), stored("3").getSecurityVersion());
        assertEquals(0, users.updates);
    }

    @Test
    void passwordResetAcceptsSameTextButUsesANewSaltAndVersion() {
        UserAccount before = preparePasswordTarget();
        var result = service.changePassword(session("1"), "3", before.getSecurityVersion(), "Original-Password-Test-Only");
        UserAccount after = stored("3");
        assertTrue(PasswordHasher.verify("Original-Password-Test-Only", after.getPasswordHash()));
        assertFalse(before.getPasswordHash().equals(after.getPasswordHash()));
        assertEquals(before.getSecurityVersion() + 1, result.securityVersion());
        assertEquals(1, users.updates);
    }

    @Test
    void passwordResetPreservesExistingWhitespacePasswordContract() {
        var result = service.changePassword(session("1"), "3", 0, " ");
        assertEquals(1, result.securityVersion());
        assertTrue(PasswordHasher.verify(" ", stored("3").getPasswordHash()));
    }

    @Test
    void ownPasswordResetMakesOldSessionObsoleteAndAllowsANewLogin() {
        storage.delete("2");
        UserAccount admin = stored("1");
        admin.setPasswordHash(PasswordHasher.hash("Original-Admin-Test-Only"));
        storage.update(admin);
        AuthenticationService authentication = new AuthenticationService(storage);
        UserSession actor = authentication.createSession("fixture-1", "Original-Admin-Test-Only").orElseThrow();
        long captured = actor.getAuthenticatedSecurityVersion();
        var result = service.changePassword(actor, "1", captured, "Replacement-Admin-Test-Only");
        assertEquals(captured + 1, result.securityVersion());
        assertTrue(actor.isValid());
        assertEquals(captured, actor.getAuthenticatedSecurityVersion());
        assertThrows(SecurityException.class, () -> service.setActive(actor, "3", 0, false));
        assertTrue(authentication.createSession("fixture-1", "Original-Admin-Test-Only").isEmpty());
        UserSession fresh = authentication.createSession("fixture-1", "Replacement-Admin-Test-Only").orElseThrow();
        assertEquals(result.securityVersion(), fresh.getAuthenticatedSecurityVersion());
        assertTrue(service.setActive(fresh, "3", 0, true).active());
        assertEquals(UserRole.ADMINISTRATOR, stored("1").getRole());
        assertTrue(stored("1").isActive());
    }

    @ParameterizedTest
    @ValueSource(strings = {"find:1", "find:3", "update"})
    void passwordResetPersistenceFailureDoesNotRetryOrModifyStoredHash(String stage) {
        UserAccount before = preparePasswordTarget();
        users.failureStage = stage;
        assertSame(users.failure, assertThrows(IllegalStateException.class,
                () -> service.changePassword(session("1"), "3", before.getSecurityVersion(), "Replacement-Test-Only")));
        assertEquals(1, users.events.stream().filter(stage::equals).count());
        assertEquals(stage.equals("update") ? 1 : 0, users.updates);
        assertTrue(before.getPasswordHash().equals(stored("3").getPasswordHash()));
        assertEquals(before.getSecurityVersion(), stored("3").getSecurityVersion());
    }

    @Test
    void twoPasswordResetsWithTheSameVersionHaveOneWinnerWithoutRevokingTheActors() throws Exception {
        UserAccount before = preparePasswordTarget();
        UserManagementService first = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService());
        UserManagementService second = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService());
        UserSession actor1 = session("1");
        UserSession actor2 = session("2");
        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = executor.submit(() -> resetAfterBarrier(first, actor1, before.getSecurityVersion(), "First-Reset-Test-Only", start));
            Future<Boolean> two = executor.submit(() -> resetAfterBarrier(second, actor2, before.getSecurityVersion(), "Second-Reset-Test-Only", start));
            start.await(5, TimeUnit.SECONDS);
            boolean firstWon = one.get(10, TimeUnit.SECONDS);
            boolean secondWon = two.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            UserAccount after = stored("3");
            assertEquals(before.getSecurityVersion() + 1, after.getSecurityVersion());
            assertTrue(PasswordHasher.verify(firstWon ? "First-Reset-Test-Only" : "Second-Reset-Test-Only", after.getPasswordHash()));
            assertFalse(PasswordHasher.verify(firstWon ? "Second-Reset-Test-Only" : "First-Reset-Test-Only", after.getPasswordHash()));
            assertEquals(1, users.updates);
            assertEquals(0, users.saves);
            assertEquals(0, actor1.getAuthenticatedSecurityVersion());
            assertEquals(0, actor2.getAuthenticatedSecurityVersion());
            assertEquals(UserRole.ADMINISTRATOR, stored("1").getRole());
            assertEquals(UserRole.ADMINISTRATOR, stored("2").getRole());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean resetAfterBarrier(UserManagementService operation, UserSession actor, long version,
                                     String password, CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            operation.changePassword(actor, "3", version, password);
            return true;
        } catch (ConcurrentModificationException expectedConflict) {
            return false;
        }
    }

    private UserAccount preparePasswordTarget() {
        UserAccount target = stored("3");
        target.setPasswordHash(PasswordHasher.hash("Original-Password-Test-Only"));
        storage.update(target);
        return stored("3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "3"})
    void assignsSellerToEitherActiveRolePreservingOtherFields(String targetId) {
        SellerLookup lookup = new SellerLookup();
        UserManagementService operation = withSellers(lookup);
        UserAccount before = stored(targetId);
        UserSession actor = session("2");
        users.forbidPostWriteReads = true;
        var result = operation.assignSeller(actor, targetId, 0, "SELLER-AVAILABLE");
        assertEquals(List.of("enter", "find:2", "find:" + targetId, "seller:SELLER-AVAILABLE", "all", "update", "exit"), users.events);
        UserAccount after = stored(targetId);
        assertEquals("SELLER-AVAILABLE", after.getSellerId());
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getUsername(), after.getUsername());
        assertTrue(before.getPasswordHash().equals(after.getPasswordHash()));
        assertEquals(before.getRole(), after.getRole());
        assertEquals(before.isActive(), after.isActive());
        assertEquals(before.getCreatedAt(), after.getCreatedAt());
        assertEquals(1, result.securityVersion());
        assertEquals(before.getRole(), result.role());
        assertEquals(1, lookup.reads);
        assertEquals(1, users.updates);
        assertEquals(0, users.saves);
        assertFalse(result.toString().contains(after.getPasswordHash()));
        if (targetId.equals("3")) {
            assertNotSame(users.loadedTarget, users.submitted);
            assertEquals(before.getSellerId(), users.loadedTarget.getSellerId());
        }
    }

    @Test
    void permitsInactiveSharingButRejectsReactivationUntilSellerIsReleased() {
        SellerLookup lookup = new SellerLookup();
        UserManagementService operation = withSellers(lookup);
        UserAccount inactive = stored("3");
        inactive.setActive(false);
        storage.update(inactive);
        operation.assignSeller(session("1"), "2", 0, "SELLER-AVAILABLE");
        var assignedInactive = operation.assignSeller(session("1"), "3", 1, "SELLER-AVAILABLE");
        assertEquals(2, assignedInactive.securityVersion());
        assertFalse(assignedInactive.active());
        int before = users.updates;
        assertThrows(IllegalStateException.class, () -> operation.setActive(session("1"), "3", 2, true));
        assertEquals(before, users.updates);
        assertFalse(stored("3").isActive());
        assertEquals(2, stored("3").getSecurityVersion());
        int lookups = lookup.reads;
        operation.assignSeller(session("1"), "2", 1, null);
        var reactivated = operation.setActive(session("1"), "3", 2, true);
        assertTrue(reactivated.active());
        assertEquals(3, reactivated.securityVersion());
        assertEquals(lookups, lookup.reads, "Desvincular y reactivar no consultan vendedores.");
    }

    @Test
    void unlinksWithoutSellerServiceAndRepeatedUnlinkDoesNotWrite() {
        UserAccount before = stored("3");
        var first = service.assignSeller(session("1"), "3", 0, null);
        var repeated = service.assignSeller(session("1"), "3", 1, null);
        assertNull(stored("3").getSellerId());
        assertEquals(1, first.securityVersion());
        assertEquals(1, repeated.securityVersion());
        assertEquals(1, users.updates);
        assertEquals(before.getRole(), stored("3").getRole());
        assertEquals(before.isActive(), stored("3").isActive());
        assertTrue(before.getPasswordHash().equals(stored("3").getPasswordHash()));
        assertFalse(users.events.contains("all"));
    }

    @Test
    void missingSellerServiceRejectsNonNullAssignmentEvenWhenUnchanged() {
        assertThrows(IllegalStateException.class, () -> service.assignSeller(session("1"), "3", 0, "SELLER-3"));
        assertEquals(0, users.updates);
        assertEquals(0, stored("3").getSecurityVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "bad\u0000seller"})
    void rejectsInvalidSellerIdsWithoutLookupOrWrite(String id) {
        SellerLookup lookup = new SellerLookup();
        assertThrows(IllegalArgumentException.class, () -> withSellers(lookup).assignSeller(session("1"), "3", 0, id));
        assertEquals(0, lookup.reads);
        assertEquals(0, users.updates);
    }

    @Test
    void rejectsSellerIdLongerThan32Units() {
        SellerLookup lookup = new SellerLookup();
        assertThrows(IllegalArgumentException.class,
                () -> withSellers(lookup).assignSeller(session("1"), "3", 0, "x".repeat(33)));
        assertEquals(0, lookup.reads);
        assertEquals(0, users.updates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", " seller-available ", "seller-available"})
    void sellerLookupUsesExactIdWithoutNormalization(String id) {
        SellerLookup lookup = new SellerLookup();
        assertThrows(IllegalArgumentException.class, () -> withSellers(lookup).assignSeller(session("1"), "3", 0, id));
        assertEquals(id, lookup.requested);
        assertEquals(1, lookup.reads);
        assertEquals(0, users.updates);
    }

    @Test
    void repeatedAssignmentValidatesButDoesNotWriteOrIncrementVersion() {
        SellerLookup lookup = new SellerLookup();
        var result = withSellers(lookup).assignSeller(session("1"), "3", 0, "SELLER-3");
        assertEquals(0, result.securityVersion());
        assertEquals(0, users.updates);
        assertEquals(1, lookup.reads);
        assertTrue(users.events.contains("all"));
    }

    @Test
    void rejectsDuplicateSellerBetweenActiveAccounts() {
        SellerLookup lookup = new SellerLookup();
        assertThrows(IllegalStateException.class, () -> withSellers(lookup).assignSeller(session("1"), "3", 0, "SELLER-2"));
        assertEquals(0, users.updates);
        assertEquals("SELLER-3", stored("3").getSellerId());
    }

    @Test
    void exclusivityIsCaseSensitiveAndDoesNotNormalizeIds() {
        SellerLookup lookup = new SellerLookup();
        lookup.available.put("seller-2", seller("seller-2"));
        var result = withSellers(lookup).assignSeller(session("1"), "3", 0, "seller-2");
        assertEquals(1, result.securityVersion());
        assertEquals("seller-2", stored("3").getSellerId());
        assertEquals("SELLER-2", stored("2").getSellerId());
    }

    @Test
    void rejectsHistoricalDuplicateEvenForAlreadyActiveAccountOrRepeatedAssignment() {
        UserAccount duplicate = stored("3");
        duplicate.setSellerId("SELLER-2");
        storage.update(duplicate);
        SellerLookup lookup = new SellerLookup();
        assertThrows(IllegalStateException.class, () -> service.setActive(session("1"), "3", 1, true));
        assertThrows(IllegalStateException.class,
                () -> withSellers(lookup).assignSeller(session("1"), "3", 1, "SELLER-2"));
        assertEquals(0, users.updates);
        assertEquals(1, stored("3").getSecurityVersion());
    }

    @Test
    void activatesAccountWithoutSellerAndReactivatesUniqueHistoricalSellerWithoutLookup() {
        UserAccount target = stored("3");
        target.setActive(false);
        target.setSellerId(null);
        storage.update(target);
        assertTrue(service.setActive(session("1"), "3", 1, true).active());
        target = stored("3");
        target.setActive(false);
        target.setSellerId("HISTORICAL-UNKNOWN");
        storage.update(target);
        assertTrue(service.setActive(session("1"), "3", 3, true).active());
        assertEquals(4, stored("3").getSecurityVersion());
    }

    @Test
    void sellerAssignmentRejectsUnauthorizedInvalidatedAndObsoleteSessions() {
        SellerLookup lookup = new SellerLookup();
        UserManagementService operation = withSellers(lookup);
        assertThrows(SecurityException.class, () -> operation.assignSeller(null, "3", 0, "SELLER-AVAILABLE"));
        assertThrows(SecurityException.class, () -> operation.assignSeller(session("3"), "2", 0, "SELLER-AVAILABLE"));
        UserSession invalid = session("1");
        invalid.invalidate();
        assertThrows(SecurityException.class, () -> operation.assignSeller(invalid, "3", 0, "SELLER-AVAILABLE"));
        UserSession obsolete = session("1");
        UserAccount changed = stored("1");
        changed.setSellerId("CHANGED");
        storage.update(changed);
        assertThrows(SecurityException.class, () -> operation.assignSeller(obsolete, "3", 0, "SELLER-AVAILABLE"));
        assertEquals(0, lookup.reads);
        assertEquals(0, users.updates);
    }

    @Test
    void sellerAssignmentRejectsMissingTargetAndVersionConflictBeforeLookup() {
        SellerLookup lookup = new SellerLookup();
        UserManagementService operation = withSellers(lookup);
        assertThrows(IllegalArgumentException.class, () -> operation.assignSeller(session("1"), "999", 0, "SELLER-AVAILABLE"));
        assertThrows(IllegalArgumentException.class, () -> operation.assignSeller(session("1"), "3", -1, "SELLER-AVAILABLE"));
        assertThrows(ConcurrentModificationException.class, () -> operation.assignSeller(session("1"), "3", 1, "SELLER-AVAILABLE"));
        assertEquals(0, lookup.reads);
        assertEquals(0, users.updates);
    }

    @Test
    void sellerLookupFailureStopsWithoutWriteOrRetry() {
        SellerLookup lookup = new SellerLookup();
        lookup.failure = new IllegalStateException("simulated lookup failure");
        assertSame(lookup.failure, assertThrows(IllegalStateException.class,
                () -> withSellers(lookup).assignSeller(session("1"), "3", 0, "SELLER-AVAILABLE")));
        assertEquals(1, lookup.reads);
        assertEquals(0, users.updates);
        assertEquals("SELLER-3", stored("3").getSellerId());
    }

    @Test
    void ownSellerChangeMakesCapturedSessionObsoleteWithoutRenewal() {
        UserSession actor = session("1");
        UserManagementService operation = withSellers(new SellerLookup());
        assertEquals(1, operation.assignSeller(actor, "1", 0, "SELLER-AVAILABLE").securityVersion());
        assertEquals(0, actor.getAuthenticatedSecurityVersion());
        assertTrue(actor.isValid());
        assertThrows(SecurityException.class, () -> operation.setActive(actor, "3", 0, false));
        assertEquals(1, users.updates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignments", "reactivations", "mixed"})
    void competingSellerOperationsNeverLeaveTwoActiveOwners(String scenario) throws Exception {
        storage.save(account(4, UserRole.WORKER));
        if (!scenario.equals("assignments")) {
            UserAccount target4 = stored("4");
            target4.setActive(false);
            target4.setSellerId("SELLER-AVAILABLE");
            storage.update(target4);
        }
        if (scenario.equals("reactivations")) {
            UserAccount target3 = stored("3");
            target3.setActive(false);
            target3.setSellerId("SELLER-AVAILABLE");
            storage.update(target3);
        }
        long version3 = stored("3").getSecurityVersion();
        long version4 = stored("4").getSecurityVersion();
        SellerLookup lookup = new SellerLookup();
        UserManagementService first = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService(), lookup);
        UserManagementService second = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(storage), new AuthorizationService(), lookup);
        UserSession actor1 = session("1");
        UserSession actor2 = session("2");
        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = executor.submit(() -> sellerAfterBarrier(() -> scenario.equals("reactivations")
                    ? first.setActive(actor1, "3", version3, true)
                    : first.assignSeller(actor1, "3", version3, "SELLER-AVAILABLE"), start));
            Future<Boolean> two = executor.submit(() -> sellerAfterBarrier(() -> scenario.equals("assignments")
                    ? second.assignSeller(actor2, "4", version4, "SELLER-AVAILABLE")
                    : second.setActive(actor2, "4", version4, true), start));
            start.await(5, TimeUnit.SECONDS);
            boolean firstWon = one.get(10, TimeUnit.SECONDS);
            boolean secondWon = two.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertEquals(1, storage.findAll().stream().filter(user -> user.isActive()
                    && "SELLER-AVAILABLE".equals(user.getSellerId())).count());
            assertEquals(version3 + (firstWon ? 1 : 0), stored("3").getSecurityVersion());
            assertEquals(version4 + (secondWon ? 1 : 0), stored("4").getSecurityVersion());
            assertEquals(1, users.updates);
            assertEquals(0, actor1.getAuthenticatedSecurityVersion());
            assertEquals(0, actor2.getAuthenticatedSecurityVersion());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean sellerAfterBarrier(Supplier<UserManagementService.UserChangeResult> operation,
                                       CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try { operation.get(); return true; }
        catch (IllegalStateException duplicate) {
            assertTrue(duplicate.getMessage().contains("otra cuenta activa"));
            return false;
        }
    }

    private UserManagementService withSellers(SellerLookup lookup) {
        return new UserManagementService(users, boundary, new AuthorizationService(), lookup);
    }

    private static Seller seller(String id) {
        return new Seller(id, "Vendedor de prueba", "3001234567", "Dirección de prueba");
    }

    /** Consulta simulada de solo lectura; exige el monitor administrativo, sin tocar otro módulo. */
    private final class SellerLookup extends SellerService {
        final Map<String, Seller> available = new HashMap<>();
        int reads;
        String requested;
        RuntimeException failure;
        SellerLookup() {
            super(null);
            for (String id : List.of("SELLER-1", "SELLER-2", "SELLER-3", "SELLER-AVAILABLE")) available.put(id, seller(id));
        }
        @Override public Optional<Seller> getSellerById(String id) {
            reads++;
            requested = id;
            users.enter("seller:" + id);
            if (failure != null) throw failure;
            return Optional.ofNullable(available.get(id));
        }
    }

    private boolean createAfterBarrier(UserManagementService operation, UserSession actor, String username,
                                       CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            operation.createUser(actor, request(username));
            return true;
        } catch (IllegalArgumentException duplicate) {
            assertTrue(duplicate.getMessage().contains("registrado"));
            return false;
        }
    }

    private static UserManagementService.CreateUserRequest request(String username) {
        return new UserManagementService.CreateUserRequest(username, "Temporary-Creation-Test", UserRole.WORKER, true);
    }

    private boolean compete(UserManagementService operation, UserSession actor, String id,
                            boolean roleChange, CyclicBarrier start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            if (roleChange) operation.changeRole(actor, id, 0, UserRole.WORKER);
            else operation.setActive(actor, id, 0, false);
            return true;
        } catch (IllegalStateException rejectedLastAdministrator) {
            assertTrue(rejectedLastAdministrator.getMessage().contains("último administrador"));
            return false;
        }
    }

    private UserManagementService.UserChangeResult remove(UserSession actor, String id, long version, boolean roleChange) {
        return roleChange ? service.changeRole(actor, id, version, UserRole.WORKER)
                : service.setActive(actor, id, version, false);
    }

    private UserAccount stored(String id) { return storage.findById(id).orElseThrow(); }
    private UserSession session(String id) { return new UserSession(stored(id)); }
    private long activeAdministrators() {
        return storage.findAll().stream().filter(account -> account.isActive()
                && account.getRole() == UserRole.ADMINISTRATOR).count();
    }

    private static UserAccount account(long id, UserRole role) {
        UserAccount result = new UserAccount(id, "fixture-" + id, "opaque-test-hash", role, "SELLER-" + id);
        result.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return result;
    }

    private static void assertUnchangedExceptSecurity(UserAccount original, UserAccount updated) {
        assertEquals(original.getId(), updated.getId());
        assertEquals(original.getUsername(), updated.getUsername());
        assertEquals(original.getPasswordHash(), updated.getPasswordHash());
        assertEquals(original.getSellerId(), updated.getSellerId());
        assertEquals(original.getCreatedAt(), updated.getCreatedAt());
    }

    /** Exige el monitor durante cada acceso: no basta la sincronización individual del delegado. */
    private final class RecordingUsers implements UserRepository {
        final List<String> events = new ArrayList<>();
        int updates, saves;
        UserAccount loadedTarget, submitted;
        boolean forbidPostWriteReads;
        boolean allowCreate, generatedIdRequested;
        String failureStage;
        RuntimeException failure = new IllegalStateException("simulated repository failure");

        private void enter(String event) {
            assertTrue(Thread.holdsLock(storage), "Todos los accesos deben ocurrir dentro de la frontera.");
            if (forbidPostWriteReads && (updates > 0 || saves > 0)
                    && !event.equals("update") && !event.equals("save")) fail("No consultar tras escribir.");
            events.add(event);
            if (event.equals(failureStage)) throw failure;
        }
        @Override public Optional<UserAccount> findById(String id) {
            enter("find:" + id);
            Optional<UserAccount> result = storage.findById(id);
            if (id.equals("3")) loadedTarget = result.orElse(null);
            return result;
        }
        @Override public List<UserAccount> findAll() { enter("all"); return storage.findAll(); }
        @Override public void update(UserAccount user) {
            updates++;
            submitted = user;
            enter("update");
            storage.update(user);
        }
        @Override public void save(UserAccount user) {
            if (!allowCreate) throw new AssertionError("No crear cuentas en operaciones de rol o estado.");
            saves++;
            enter("save");
            generatedIdRequested = user.getId() == null;
            assertEquals(0, user.getSecurityVersion());
            storage.save(user);
        }
        @Override public void delete(String id) { throw new AssertionError("No eliminar cuentas."); }
        @Override public Optional<UserAccount> findByUsername(String name) { throw new AssertionError("No usar username para autorizar."); }
    }
}
