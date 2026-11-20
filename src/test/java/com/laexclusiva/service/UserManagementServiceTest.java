package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.security.PasswordHasher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
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
