package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.security.PasswordHasher;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica SQL parametrizado, mapeo, errores y participación transaccional sin abrir PostgreSQL. */
class UserRepositoryJdbcTest {
    private static final String HASH = "$pbkdf2-sha256$i=210000$AAAAAAAAAAAAAAAAAAAAAA==$"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 3, 4, 5, 6, 7, 123456000);
    private ScriptedJdbc jdbc;
    private JdbcConnectionProvider connections;
    private UserRepositoryJdbc users;

    @BeforeEach
    void setUp() {
        jdbc = new ScriptedJdbc();
        connections = new JdbcConnectionProvider(new DatabaseConfig(
                "jdbc:postgresql://localhost:5432/la_exclusiva_test", "fixture", "unused"), jdbc::open);
        users = new UserRepositoryJdbc(connections);
    }

    @Test
    void rejectsMissingProvider() {
        assertThrows(IllegalArgumentException.class, () -> new UserRepositoryJdbc(null));
    }

    @Test
    void generatesIdAndBindsEveryFieldWithoutInterpolatingCredentials() {
        jdbc.query(row("id", 7L));
        UserAccount user = account(null);
        user.setUsername("  Daniel  ");
        user.setActive(false);
        users.save(user);
        assertEquals(7L, user.getId());
        Call call = jdbc.calls.get(0);
        assertFalse(call.sql.contains(HASH));
        assertTrue(call.sql.contains("RETURNING id"));
        assertEquals("Daniel", call.values.get(1));
        assertEquals(HASH, call.values.get(2));
        assertEquals("WORKER", call.values.get(3));
        assertEquals(false, call.values.get(4));
        assertNull(call.values.get(5));
        assertEquals(CREATED, call.values.get(6));
        assertResourcesClosed(1, 1);
    }

    @Test
    void preservesExplicitHistoricalIdAndNullableSeller() {
        jdbc.update(1);
        UserAccount user = account(930L);
        user.setSellerId("SEL-HISTORY");
        users.save(user);
        assertEquals(930L, user.getId());
        assertEquals(930L, jdbc.calls.get(0).values.get(1));
        assertEquals("SEL-HISTORY", jdbc.calls.get(0).values.get(6));
        assertEquals(CREATED, jdbc.calls.get(0).values.get(7));
        assertResourcesClosed(1, 0);
    }

    @Test
    void skipsGeneratedIdAlreadyUsedByAHistoricalAccountWithoutReplacingIt() {
        jdbc.query();
        jdbc.query(row("id", 2L));
        UserAccount user = account(null);
        users.save(user);
        assertEquals(2L, user.getId());
        assertEquals(2, jdbc.calls.size());
        assertTrue(jdbc.calls.stream().allMatch(call -> call.sql.contains("ON CONFLICT (id) DO NOTHING")));
        assertResourcesClosed(2, 2);
    }

    @Test
    void limitsSequenceCollisionRetriesAndDoesNotAssignAnIdOnFailure() {
        for (int i = 0; i < 64; i++) jdbc.query();
        UserAccount user = account(null);
        assertThrows(JdbcPersistenceException.class, () -> users.save(user));
        assertNull(user.getId());
        assertEquals(64, jdbc.calls.size());
        assertResourcesClosed(64, 64);
    }

    @Test
    void restoresIdRoleInactiveStateHashSellerAndHistoricalTimestamp() {
        jdbc.query(stored(55L, "Original", false));
        UserAccount restored = users.findById(" 55 ").orElseThrow();
        assertEquals(55L, restored.getId());
        assertEquals("Original", restored.getUsername());
        assertEquals(HASH, restored.getPasswordHash());
        assertEquals(UserRole.WORKER, restored.getRole());
        assertFalse(restored.isActive());
        assertEquals("SEL-1", restored.getSellerId());
        assertEquals(CREATED, restored.getCreatedAt());
        assertEquals(55L, jdbc.calls.get(0).values.get(1));
        assertResourcesClosed(1, 1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "-1", "0", "9223372036854775808", "1 OR 1=1"})
    void invalidIdsDoNotQueryOrDelete(String id) {
        assertTrue(users.findById(id).isEmpty());
        users.delete(id);
        assertEquals(0, jdbc.opened);
    }

    @Test
    void missingUserReturnsEmpty() {
        jdbc.query();
        assertTrue(users.findById("1").isEmpty());
    }

    @Test
    void usernameLookupUsesTheDatabaseIndexNormalizationAndParameters() {
        jdbc.query(stored(1L, "Daniel", true));
        assertTrue(users.findByUsername("  DANIEL  ").isPresent());
        assertTrue(jdbc.calls.get(0).sql.contains("lower(btrim(username)) = lower(btrim(?))"));
        assertEquals("DANIEL", jdbc.calls.get(0).values.get(1));
        jdbc.query();
        String input = "name' OR '1'='1";
        assertTrue(users.findByUsername(input).isEmpty());
        assertFalse(jdbc.calls.get(1).sql.contains(input));
        assertEquals(input, jdbc.calls.get(1).values.get(1));
        assertResourcesClosed(2, 2);
    }

    @Test
    void usernameNormalizationPreservesTabsLikePostgresBtrim() {
        jdbc.query(stored(1L, "\tDaniel\t", true));
        assertEquals("\tDaniel\t", users.findByUsername("  \tDaniel\t  ").orElseThrow().getUsername());
        assertEquals("\tDaniel\t", jdbc.calls.get(0).values.get(1));
        jdbc.query(row("id", 2L));
        UserAccount user = account(null);
        user.setUsername("  \tDaniel\t  ");
        users.save(user);
        assertEquals("\tDaniel\t", jdbc.calls.get(1).values.get(1));
        assertResourcesClosed(2, 2);
    }

    @Test
    void invalidUsernamesDoNotOpenConnections() {
        assertTrue(users.findByUsername(null).isEmpty());
        assertTrue(users.findByUsername(" ").isEmpty());
        assertTrue(users.findByUsername("x".repeat(101)).isEmpty());
        assertEquals(0, jdbc.opened);
    }

    @Test
    void listReturnsIndependentSnapshotsIncludingInactiveAccountsAndEmptyLists() {
        jdbc.query(stored(1L, "First", true), stored(2L, "Second", false));
        List<UserAccount> list = users.findAll();
        assertEquals(List.of(1L, 2L), list.stream().map(UserAccount::getId).toList());
        assertFalse(list.get(1).isActive());
        assertTrue(jdbc.calls.get(0).sql.endsWith("ORDER BY id"));
        list.get(0).setUsername("Changed locally");
        jdbc.query(stored(1L, "First", true));
        assertEquals("First", users.findById("1").orElseThrow().getUsername());
        jdbc.query();
        assertTrue(users.findAll().isEmpty());
        assertResourcesClosed(3, 3);
    }

    @Test
    void updateBindsSecurityFieldsButCannotChangeIdOrCreationTime() {
        jdbc.update(1);
        UserAccount user = account(9L);
        user.setRole(UserRole.ADMINISTRATOR);
        user.setActive(false);
        users.update(user);
        Call call = jdbc.calls.get(0);
        assertFalse(call.sql.contains("created_at"));
        assertEquals("ADMINISTRATOR", call.values.get(3));
        assertEquals(false, call.values.get(4));
        assertNull(call.values.get(5));
        assertEquals(9L, call.values.get(6));
        assertResourcesClosed(1, 0);
    }

    @Test
    void updateRequiresAnExistingIdAndExactlyOneAffectedRow() {
        assertThrows(IllegalArgumentException.class, () -> users.update(account(null)));
        jdbc.update(0);
        assertThrows(IllegalArgumentException.class, () -> users.update(account(1L)));
        jdbc.update(2);
        assertThrows(JdbcPersistenceException.class, () -> users.update(account(1L)));
    }

    @Test
    void deleteUsesBoundIdAndAllowsAnAlreadyMissingUser() {
        jdbc.update(1);
        users.delete(" 9 ");
        assertEquals(9L, jdbc.calls.get(0).values.get(1));
        assertEquals("DELETE FROM user_account WHERE id = ?", jdbc.calls.get(0).sql);
        jdbc.update(0);
        assertDoesNotThrow(() -> users.delete("9"));
        assertResourcesClosed(2, 0);
    }

    @Test
    void duplicateAndForeignKeyFailuresNeverExposeTheServerRowOrHash() {
        for (String state : List.of("23505", "23503")) {
            jdbc.fail(state, "Fila sensible: " + HASH);
            UserAccount user = account(null);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> users.save(user));
            assertFalse(failure.getMessage().contains(HASH));
            assertNull(failure.getCause());
            assertNull(user.getId());
        }
        jdbc.fail("23505", HASH);
        assertThrows(IllegalArgumentException.class, () -> users.update(account(1L)));
        assertResourcesClosed(3, 0);
    }

    @Test
    void sqlReadFailureClosesResourcesAndDoesNotExposeDriverDetails() {
        jdbc.fail("08006", HASH);
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class, users::findAll);
        assertFalse(failure.getMessage().contains(HASH));
        assertTrue(failure.getMessage().contains("SQLSTATE=08006"));
        assertTrue(failure.getMessage().contains("operación=LIST_USERS"));
        assertNull(failure.getCause());
        assertResourcesClosed(1, 0);
    }

    @Test
    void connectionFailureDoesNotLeakDriverDetailsOrAttemptStatements() {
        jdbc.openFailure = new SQLException("Detalle privado: " + HASH, "08001");
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class, users::findAll);
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(HASH));
        assertTrue(failure.getMessage().contains("SQLSTATE=08001"));
        assertTrue(failure.getMessage().contains("operación=LIST_USERS"));
        assertTrue(jdbc.calls.isEmpty());
    }

    @Test
    void unexpectedWriteFailureRetainsOnlySafeDiagnostics() {
        jdbc.replies.add(new Reply(List.of(), 0, new SQLException("Datos privados: " + HASH, "23514", 123)));
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class, () -> users.save(account(null)));
        assertTrue(failure.getMessage().contains("SQLSTATE=23514"));
        assertTrue(failure.getMessage().contains("operación=INSERT_USER"));
        assertTrue(failure.getMessage().contains("código=123"));
        assertFalse(failure.getMessage().contains(HASH));
        assertNull(failure.getCause());
        assertResourcesClosed(1, 0);
    }

    @Test
    void untrustedSqlStateCannotInjectPrivateTextIntoDiagnostics() {
        jdbc.fail("secret-" + HASH, HASH);
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class, users::findAll);
        assertTrue(failure.getMessage().contains("SQLSTATE=NO_DISPONIBLE"));
        assertFalse(failure.getMessage().contains(HASH));
    }

    @Test
    void suppressedCloseFailureIsSanitizedWithoutLosingItsSqlState() {
        jdbc.update(2);
        jdbc.statementCloseFailure = new SQLException("Detalle privado: " + HASH, "08006");
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class, () -> users.update(account(1L)));
        assertTrue(failure.getMessage().contains("operación=UPDATE_USER"));
        assertTrue(failure.getMessage().contains("SQLSTATE=08006"));
        assertFalse(failure.getMessage().contains(HASH));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertResourcesClosed(1, 0);
    }

    @Test
    void manualInsertionRejectsAnUnexpectedAffectedRowCount() {
        jdbc.update(0);
        assertThrows(JdbcPersistenceException.class, () -> users.save(account(8L)));
        assertResourcesClosed(1, 0);
    }

    @Test
    void rejectsPlaintextAndInvalidDataBeforeOpeningAConnection() {
        assertThrows(IllegalArgumentException.class, () -> users.save(null));
        List<java.util.function.Consumer<UserAccount>> invalidations = List.of(
                user -> user.setId(0L), user -> user.setUsername(" "),
                user -> user.setUsername("x".repeat(101)), user -> user.setUsername("bad\0name"),
                user -> user.setPasswordHash("plaintext-password"), user -> user.setPasswordHash(null),
                user -> user.setPasswordHash("$pbkdf2-sha256$i=0$AA==$AA=="),
                user -> user.setRole(null), user -> user.setCreatedAt(null),
                user -> user.setSellerId(" "), user -> user.setSellerId("s".repeat(33)));
        for (var invalidate : invalidations) {
            UserAccount user = account(null);
            invalidate.accept(user);
            assertThrows(IllegalArgumentException.class, () -> users.save(user));
        }
        assertEquals(0, jdbc.opened);
    }

    @Test
    void malformedPersistedRoleIsReportedWithoutSensitiveDetails() {
        Map<String, Object> row = stored(1L, "Name", true);
        row.put("role", "UNKNOWN");
        jdbc.query(row);
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class, users::findAll);
        assertFalse(failure.getMessage().contains(HASH));
        assertResourcesClosed(1, 1);
    }

    @Test
    void historicalHashAcceptedByPasswordHasherCanBeReadWithoutRelaxingNewWrites() throws Exception {
        byte[] salt = new byte[8];
        var spec = new javax.crypto.spec.PBEKeySpec("Historical-Test-Only".toCharArray(), salt, 1000, 128);
        byte[] key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        String historicalHash = "$pbkdf2-sha256$i=1000$" + java.util.Base64.getEncoder().encodeToString(salt)
                + "$" + java.util.Base64.getEncoder().encodeToString(key);
        Map<String, Object> historical = stored(1L, "Historical", true);
        historical.put("password_hash", historicalHash);
        jdbc.query(historical);
        UserAccount loaded = users.findById("1").orElseThrow();
        assertEquals(historicalHash, loaded.getPasswordHash());
        assertTrue(PasswordHasher.verify("Historical-Test-Only", loaded.getPasswordHash()));
        assertThrows(IllegalArgumentException.class, () -> users.save(loaded));
        loaded.setPasswordHash("plaintext-password");
        assertThrows(IllegalArgumentException.class, () -> users.update(loaded));
        assertResourcesClosed(1, 1);
    }

    @Test
    void joinsTheOuterTransactionWithoutCommittingOrClosingItsConnection() {
        jdbc.query(row("id", 1L));
        jdbc.query(stored(1L, "Name", true));
        IllegalStateException abort = new IllegalStateException("Abortar prueba");
        assertSame(abort, assertThrows(IllegalStateException.class,
                () -> new JdbcTransactionManager(connections).inTransaction(() -> {
                    users.save(account(null));
                    assertTrue(users.findById("1").isPresent());
                    assertEquals(1, jdbc.opened);
                    assertEquals(0, jdbc.closedConnections);
                    assertEquals(0, jdbc.commits);
                    throw abort;
                })));
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
        assertResourcesClosed(2, 2);
    }

    @Test
    void caughtSqlFailureStillMarksTheOuterTransactionForRollback() {
        jdbc.fail("23505", HASH);
        assertThrows(JdbcPersistenceException.class,
                () -> new JdbcTransactionManager(connections).inTransaction(() -> {
                    assertThrows(IllegalArgumentException.class, () -> users.save(account(null)));
                }));
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
    }

    @Test
    void successfulOuterTransactionCommitsOnceForSeveralRepositoryOperations() {
        jdbc.query(row("id", 1L));
        jdbc.update(1);
        new JdbcTransactionManager(connections).inTransaction(() -> {
            UserAccount user = account(null);
            users.save(user);
            users.update(user);
            assertEquals(0, jdbc.commits);
            assertEquals(0, jdbc.closedConnections);
        });
        assertEquals(1, jdbc.opened);
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertResourcesClosed(2, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "delete"})
    void updateAndDeleteJoinAnOuterRollbackWithoutClosingOrCommitting(String operation) {
        jdbc.update(1);
        IllegalStateException abort = new IllegalStateException("Abortar operación del fixture");
        assertSame(abort, assertThrows(IllegalStateException.class,
                () -> new JdbcTransactionManager(connections).inTransaction(() -> {
                    if (operation.equals("update")) users.update(account(1L));
                    else users.delete("1");
                    assertEquals(0, jdbc.commits);
                    assertEquals(0, jdbc.closedConnections);
                    throw abort;
                })));
        assertEquals(1, jdbc.opened);
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
        assertResourcesClosed(1, 0);
    }

    @Test
    void unexpectedDeleteFailureRollsBackAndRetainsTheOperationAndSqlState() {
        jdbc.fail("40P01", HASH);
        JdbcPersistenceException failure = assertThrows(JdbcPersistenceException.class,
                () -> new JdbcTransactionManager(connections).inTransaction(() -> users.delete("1")));
        assertTrue(failure.getMessage().contains("SQLSTATE=40P01"));
        assertTrue(failure.getMessage().contains("operación=DELETE_USER"));
        assertFalse(failure.getMessage().contains(HASH));
        assertEquals(1, jdbc.rollbacks);
        assertEquals(0, jdbc.commits);
        assertResourcesClosed(1, 0);
    }

    @Test
    void existingAuthenticationServiceCanConsumeTheAdapterWithoutChangingItsContract() {
        Map<String, Object> stored = stored(1L, "Name", true);
        stored.put("password_hash", PasswordHasher.hash("Temporary-Test-Only"));
        jdbc.query(stored);
        assertTrue(new AuthenticationService(users).createSession("Name", "Temporary-Test-Only").isPresent());
        stored.put("active", false);
        jdbc.query(stored);
        assertTrue(new AuthenticationService(users).createSession("Name", "Temporary-Test-Only").isEmpty());
    }

    @Test
    void fixtureCleansThePreRegisteredNameWhenAnInsertResponseIsLost() {
        jdbc.query(row("database", "la_exclusiva_test"));
        var fixture = new UserRepositoryJdbcPostgresIntegrationTest.Fixture(new DatabaseConfig(
                "jdbc:postgresql://localhost/la_exclusiva_test", "fixture", "unused"), connections);
        jdbc.query();
        jdbc.update(1);
        fixture.createSeller();
        UserAccount user = fixture.account("uncertain");
        jdbc.query();
        fixture.rememberName(user.getUsername());
        jdbc.query(row("id", 9L));
        jdbc.connectionCloseFailure = new SQLException("Respuesta perdida", "08006");
        assertThrows(JdbcPersistenceException.class, () -> fixture.persist(user));
        assertNull(user.getId(), "El retorno no confirmó el ID, pero el nombre ya debe estar registrado para limpiar.");
        jdbc.update(1);
        jdbc.update(1);
        fixture.close();
        Call cleanup = jdbc.calls.get(jdbc.calls.size() - 2);
        assertEquals("DELETE FROM user_account WHERE lower(btrim(username)) = lower(btrim(?))", cleanup.sql);
        assertEquals(user.getUsername().toLowerCase(java.util.Locale.ROOT), cleanup.values.get(1));
        assertTrue(jdbc.replies.isEmpty());
    }

    @Test
    void fixtureDoesNotIssueCleanupSqlWhileAWriterCanStillRun() throws Exception {
        jdbc.query(row("database", "la_exclusiva_test"));
        var fixture = new UserRepositoryJdbcPostgresIntegrationTest.Fixture(new DatabaseConfig(
                "jdbc:postgresql://localhost/la_exclusiva_test", "fixture", "unused"), connections);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        fixture.writers = executor;
        try {
            executor.submit(() -> { started.countDown(); release.await(); return null; });
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
            int callsBefore = jdbc.calls.size();
            assertThrows(IllegalStateException.class, fixture::close);
            assertEquals(callsBefore, jdbc.calls.size(), "No debe empezar ninguna limpieza SQL.");
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertDoesNotThrow(fixture::close);
    }

    @Test
    void writerTimeoutIsExplicitAndDoesNotPretendTheExecutorHasTerminated() {
        var executor = new java.util.concurrent.AbstractExecutorService() {
            boolean shutdown;
            @Override public void shutdown() { shutdown = true; }
            @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
            @Override public boolean isShutdown() { return shutdown; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) { return false; }
            @Override public void execute(Runnable command) { throw new UnsupportedOperationException(); }
        };
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> UserRepositoryJdbcPostgresIntegrationTest.stopWriters(executor));
        assertTrue(executor.isShutdown());
        assertFalse(executor.isTerminated());
        assertTrue(failure.getMessage().contains("se omite la limpieza"));
    }

    private void assertResourcesClosed(int statements, int resultSets) {
        assertEquals(jdbc.opened, jdbc.closedConnections);
        assertEquals(statements, jdbc.closedStatements);
        assertEquals(resultSets, jdbc.closedRows);
        assertTrue(jdbc.replies.isEmpty(), "Todas las respuestas JDBC preparadas deben consumirse.");
    }

    private static UserAccount account(Long id) {
        UserAccount user = new UserAccount(id, "Name", HASH, UserRole.WORKER, null);
        user.setCreatedAt(CREATED);
        return user;
    }

    private static Map<String, Object> stored(long id, String name, boolean active) {
        return row("id", id, "username", name, "password_hash", HASH, "role", "WORKER",
                "active", active, "seller_id", "SEL-1", "created_at", CREATED);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        return row;
    }

    private record Reply(List<Map<String, Object>> rows, int changed, SQLException failure) { }
    private record Call(String sql, Map<Integer, Object> values) { }

    /** Driver simulado por respuestas, sin interpretar ni volver a implementar el SQL de producción. */
    private static final class ScriptedJdbc {
        private final Deque<Reply> replies = new ArrayDeque<>();
        private final List<Call> calls = new ArrayList<>();
        private int opened, closedConnections, closedStatements, closedRows, commits, rollbacks;
        private SQLException openFailure;
        private SQLException statementCloseFailure;
        private SQLException connectionCloseFailure;

        @SafeVarargs
        final void query(Map<String, Object>... rows) { replies.add(new Reply(List.of(rows), 0, null)); }
        void update(int changed) { replies.add(new Reply(List.of(), changed, null)); }
        void fail(String state, String message) { replies.add(new Reply(List.of(), 0, new SQLException(message, state))); }

        Connection open() throws SQLException {
            if (openFailure != null) throw openFailure;
            opened++;
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> statement((String) args[0]);
                case "setAutoCommit" -> null;
                case "commit" -> { commits++; yield null; }
                case "rollback" -> { rollbacks++; yield null; }
                case "close" -> {
                    closedConnections++;
                    SQLException failure = connectionCloseFailure;
                    connectionCloseFailure = null;
                    if (failure != null) throw failure;
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        PreparedStatement statement(String sql) {
            Map<Integer, Object> values = new HashMap<>();
            return proxy(PreparedStatement.class, (object, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set")) {
                    values.put((Integer) args[0], name.equals("setNull") ? null : args[1]);
                    return null;
                }
                if (name.equals("close")) {
                    closedStatements++;
                    if (statementCloseFailure != null) throw statementCloseFailure;
                    return null;
                }
                if (!name.equals("executeQuery") && !name.equals("executeUpdate")) {
                    throw new UnsupportedOperationException(name);
                }
                calls.add(new Call(sql, new HashMap<>(values)));
                Reply reply = replies.removeFirst();
                if (reply.failure() != null) throw reply.failure();
                return name.equals("executeUpdate") ? reply.changed() : rows(reply.rows());
            });
        }

        ResultSet rows(List<Map<String, Object>> rows) {
            int[] index = {-1};
            return proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
                case "next" -> ++index[0] < rows.size();
                case "getString", "getBoolean", "getLong", "getObject" -> args[0] instanceof Integer
                        ? rows.get(index[0]).values().iterator().next() : rows.get(index[0]).get(args[0]);
                case "close" -> { closedRows++; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}
