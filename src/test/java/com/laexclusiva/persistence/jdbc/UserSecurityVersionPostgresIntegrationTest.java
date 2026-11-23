package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.security.PasswordHasher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integraciones futuras de V104: requieren autorización propia, PostgreSQL 18 local y esquema instalado.
 * No aplican migraciones ni demuestran la conversión de filas anteriores a V104.
 */
class UserSecurityVersionPostgresIntegrationTest {
    static final String ENABLE_PROPERTY = "laexclusiva.test.postgres.userSecurityVersion";

    /** Lista cerrada: sin parámetros, múltiples hosts, usuarios embebidos ni bases alternativas. */
    static void validateLocalUrl(String url) {
        if (url == null || !url.matches(
                "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::5432)?/la_exclusiva_test")) {
            throw new IllegalArgumentException("Se requiere la base de pruebas en loopback y puerto 5432, sin opciones URL.");
        }
    }

    /** Se ejecuta sobre cada conexión física, antes de entregar el control a fixtures o repositorios. */
    static void verifyConnection(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT current_database(), host(inet_server_addr()), inet_server_port(), "
                + "current_setting('server_version_num')::integer / 10000, "
                + "current_setting('session_replication_role') = 'origin'")) {
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !"la_exclusiva_test".equals(rows.getString(1))
                        || !("127.0.0.1".equals(rows.getString(2)) || "::1".equals(rows.getString(2)))
                        || rows.getInt(3) != 5432 || rows.getInt(4) != 18 || !rows.getBoolean(5)) {
                    throw new SQLException("Destino o configuración de pruebas no permitido.", "08004");
                }
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("SET search_path = pg_catalog, public");
            statement.execute("SET statement_timeout = '15s'");
            statement.execute("SET lock_timeout = '10s'");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT to_regclass('user_account') = u.oid AND u.relpersistence = 'p'
                         AND to_regclass('public.seller') IS NOT NULL AS tables_ready,
                       a.attnotnull AND a.atttypid = 'bigint'::regtype AS column_ready,
                       ck.contype = 'c' AND ck.convalidated
                         AND ck.conkey = ARRAY[a.attnum]::smallint[] AS check_ready,
                       pg_get_expr(ck.conbin, ck.conrelid, false) AS check_expression,
                       t.tgtype AS trigger_type, t.tgenabled::text AS trigger_enabled,
                       t.tgqual IS NULL AS trigger_unconditional,
                       t.tgattr::text AS trigger_columns,
                       NOT t.tgisinternal AND t.tgnargs = 0
                         AND p.oid = to_regprocedure('public.advance_user_security_version()')
                         AS trigger_function_ready,
                       NOT p.prosecdef AND p.pronargs = 0 AND p.prorettype = 'trigger'::regtype
                         AND p.prokind = 'f' AND l.lanname = 'plpgsql'
                         AND p.proconfig = ARRAY['search_path=pg_catalog']::text[] AS function_ready,
                       p.prosrc AS function_body
                FROM pg_catalog.pg_class u
                LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid = u.oid
                  AND a.attname = 'security_version' AND NOT a.attisdropped
                LEFT JOIN pg_catalog.pg_constraint ck ON ck.conrelid = u.oid
                  AND ck.conname = 'ck_user_account_security_version'
                LEFT JOIN pg_catalog.pg_trigger t ON t.tgrelid = u.oid
                  AND t.tgname = 'trg_user_account_security_version'
                LEFT JOIN pg_catalog.pg_proc p ON p.oid = t.tgfoid
                LEFT JOIN pg_catalog.pg_language l ON l.oid = p.prolang
                WHERE u.oid = to_regclass('public.user_account')
                """)) {
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !rows.getBoolean("tables_ready") || !rows.getBoolean("column_ready")
                        || !rows.getBoolean("check_ready") || !isExpectedCheck(rows.getString("check_expression"))
                        || rows.getInt("trigger_type") != 23 || !"O".equals(rows.getString("trigger_enabled"))
                        || !rows.getBoolean("trigger_unconditional") || !"".equals(rows.getString("trigger_columns"))
                        || !rows.getBoolean("trigger_function_ready") || !rows.getBoolean("function_ready")
                        || !expectedTriggerBody().equals(normalizeBody(rows.getString("function_body")))) {
                    throw new SQLException("V104 o sus protecciones no están instaladas; no se autorizan escrituras.", "55000");
                }
                if (rows.next()) throw new SQLException("Metadatos de V104 ambiguos; se rechaza el esquema.", "55000");
            }
        }
    }

    /**
     * Lista cerrada para la expresión decompilada por pg_get_expr sobre BIGINT en PostgreSQL 18.
     * Solo elimina espacios y paréntesis exteriores completos; admite el cast BIGINT de la constante cero.
     * No acepta OR, comentarios ni expresiones equivalentes distintas.
     * Puede rechazar una variante válida no prevista: se prefiere abortar antes que ampliar la aceptación.
     */
    static boolean isExpectedCheck(String expression) {
        if (expression == null) return false;
        String normalized = expression.replaceAll("\\s+", "");
        while (hasEnclosingParentheses(normalized)) normalized = normalized.substring(1, normalized.length() - 1);
        return normalized.equals("security_version>=0") || normalized.equals("security_version>=0::bigint")
                || normalized.equals("security_version>=(0)::bigint");
    }

    private static boolean hasEnclosingParentheses(String expression) {
        if (!expression.startsWith("(") || !expression.endsWith(")")) return false;
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char character = expression.charAt(i);
            if (character == '(') depth++;
            else if (character == ')') depth--;
            if (depth < 0 || (depth == 0 && i < expression.length() - 1)) return false;
        }
        return depth == 0;
    }

    /** Compara el cuerpo completo con V104, normalizando únicamente finales de línea y bordes. */
    private static String expectedTriggerBody() throws SQLException {
        try {
            String migration = Files.readString(Path.of("database/migrations/V104__user_security_version.sql"));
            String delimiter = "$function$";
            int start = migration.indexOf(delimiter);
            int end = start < 0 ? -1 : migration.indexOf(delimiter, start + delimiter.length());
            if (start < 0 || end < 0) throw new SQLException("No se pudo identificar el cuerpo de V104.");
            return normalizeBody(migration.substring(start + delimiter.length(), end));
        } catch (IOException failure) {
            throw new SQLException("No se pudo leer la migración de referencia V104.");
        }
    }

    private static String normalizeBody(String body) {
        return body == null ? null : body.replace("\r\n", "\n").strip();
    }

    private static Connection openVerified(DatabaseConfig config) throws SQLException {
        validateLocalUrl(config.getUrl());
        Connection connection = null;
        boolean verified = false;
        try {
            Properties properties = new Properties();
            properties.setProperty("user", config.getUser());
            properties.setProperty("password", config.getPassword());
            properties.setProperty("connectTimeout", "5");
            properties.setProperty("socketTimeout", "20");
            connection = DriverManager.getConnection(config.getUrl(), properties);
            verifyConnection(connection);
            verified = true;
            return connection;
        } catch (SQLException failure) {
            throw new SQLException("No se pudo abrir o validar la conexión aislada de pruebas.");
        } finally {
            if (connection != null && !verified) {
                try { connection.close(); } catch (SQLException ignored) { /* Sin detalles del servidor. */ }
            }
        }
    }

    private static Fixture fixture() {
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLE_PROPERTY), "Integración sin autorización explícita.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        validateLocalUrl(config.getUrl());
        Fixture fixture = new Fixture(config);
        try {
            fixture.create();
            return fixture;
        } catch (RuntimeException | Error failure) {
            try { fixture.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    enum SecurityField { ROLE, PASSWORD_HASH, ACTIVE, SELLER_ID }

    @ParameterizedTest
    @EnumSource(SecurityField.class)
    void sqlTriggerIncrementsForEachRealSecurityChange(SecurityField field) {
        try (Fixture fixture = fixture()) {
            String column = switch (field) {
                case ROLE -> "role";
                case PASSWORD_HASH -> "password_hash";
                case ACTIVE -> "active";
                case SELLER_ID -> "seller_id";
            };
            Object value = switch (field) {
                case ROLE -> "ADMINISTRATOR";
                case PASSWORD_HASH -> PasswordHasher.hash("New-Fixture-Only");
                case ACTIVE -> false;
                case SELLER_ID -> fixture.sellerId;
            };
            fixture.sqlUpdate(column + " = ?", value);
            assertEquals(1, fixture.current().getSecurityVersion());
            fixture.sqlUpdate(column + " = ?", value);
            assertEquals(1, fixture.current().getSecurityVersion());
        }
    }

    @Test
    void sqlTriggerStartsInsertAtZeroEvenWhenAVersionIsRequested() {
        try (Fixture fixture = fixture()) {
            String name = fixture.remember("raw-insert");
            fixture.sql(connection -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO public.user_account
                          (username, password_hash, role, active, created_at, security_version)
                        VALUES (?, ?, 'WORKER', TRUE, CURRENT_TIMESTAMP, 99)
                        RETURNING security_version
                        """)) {
                    statement.setString(1, name);
                    statement.setString(2, fixture.hash);
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(0, rows.getLong(1));
                        assertFalse(rows.next());
                    }
                }
                return null;
            });
            assertEquals(0, fixture.users.findByUsername(name).orElseThrow().getSecurityVersion());
        }
    }

    @Test
    void sqlTriggerIncrementsOnceForMultipleChangesAndHandlesNullableSeller() {
        try (Fixture fixture = fixture()) {
            fixture.sqlUpdate("role = ?, password_hash = ?, active = ?, seller_id = ?",
                    "ADMINISTRATOR", PasswordHasher.hash("Changed-Fixture-Only"), false, fixture.sellerId);
            assertEquals(1, fixture.current().getSecurityVersion());
            fixture.sqlUpdate("seller_id = NULL");
            assertEquals(2, fixture.current().getSecurityVersion());
            fixture.sqlUpdate("seller_id = NULL");
            assertEquals(2, fixture.current().getSecurityVersion());
        }
    }

    @Test
    void sqlTriggerIgnoresNoOpRenameAndManualVersionOverrides() {
        try (Fixture fixture = fixture()) {
            fixture.sqlUpdate("role = role, password_hash = password_hash, active = active, seller_id = seller_id");
            assertEquals(0, fixture.current().getSecurityVersion());
            fixture.sqlUpdate("username = ?", fixture.remember("renamed"));
            assertEquals(0, fixture.current().getSecurityVersion());
            fixture.sqlUpdate("security_version = ?", 99L);
            assertEquals(0, fixture.current().getSecurityVersion());
            fixture.sqlUpdate("active = ?, security_version = ?", false, 99L);
            assertEquals(1, fixture.current().getSecurityVersion());
            fixture.sqlUpdate("security_version = ?", 0L);
            assertEquals(1, fixture.current().getSecurityVersion());
        }
    }

    @Test
    void adapterMapsInitialAndReturnedVersionsAndRejectsStaleSnapshots() {
        try (Fixture fixture = fixture()) {
            assertEquals(0, fixture.user.getSecurityVersion());
            UserAccount stale = fixture.current();
            UserAccount winner = fixture.current();
            winner.setSellerId(fixture.sellerId);
            fixture.users.update(winner);
            assertEquals(1, winner.getSecurityVersion());
            assertEquals(1, fixture.current().getSecurityVersion());
            assertEquals(1, fixture.users.findByUsername(winner.getUsername()).orElseThrow().getSecurityVersion());
            assertEquals(1, fixture.users.findAll().stream().filter(u -> u.getId().equals(winner.getId()))
                    .findFirst().orElseThrow().getSecurityVersion());
            fixture.users.update(winner);
            assertEquals(1, winner.getSecurityVersion());
            stale.setActive(false);
            assertThrows(ConcurrentModificationException.class, () -> fixture.users.update(stale));
            assertThrows(ConcurrentModificationException.class, () -> fixture.users.update(stale));
            assertTrue(fixture.current().isActive());
            assertEquals(fixture.sellerId, fixture.current().getSellerId());
            assertEquals(0, stale.getSecurityVersion());
        }
    }

    @Test
    void adapterRollbackRestoresDataAndVersionButNotTheDetachedObject() {
        try (Fixture fixture = fixture()) {
            UserAccount changed = fixture.current();
            IllegalStateException abort = new IllegalStateException("Abortar fixture de versión");
            assertSame(abort, assertThrows(IllegalStateException.class,
                    () -> new JdbcTransactionManager(fixture.connections).inTransaction(() -> {
                        changed.setActive(false);
                        fixture.users.update(changed);
                        assertEquals(1, fixture.current().getSecurityVersion());
                        throw abort;
                    })));
            assertEquals(1, changed.getSecurityVersion());
            assertEquals(0, fixture.current().getSecurityVersion());
            assertTrue(fixture.current().isActive());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void independentSessionsSerializeWithCommitOrRollbackOfTheFirstWriter(boolean commitFirst) throws Exception {
        try (Fixture fixture = fixture()) {
            UserAccount first = fixture.current();
            UserAccount second = fixture.current();
            first.setSellerId(fixture.sellerId);
            second.setActive(false);
            CountDownLatch firstUpdated = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicInteger firstPid = new AtomicInteger();
            AtomicInteger secondPid = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            fixture.writers = executor;
            try {
                var left = executor.submit(() -> {
                    IllegalStateException abort = new IllegalStateException("Rollback del primer escritor");
                    try {
                        new JdbcTransactionManager(fixture.connections).inTransaction(() -> {
                            firstPid.set(fixture.backendPid());
                            fixture.users.update(first);
                            firstUpdated.countDown();
                            await(releaseFirst);
                            if (!commitFirst) throw abort;
                        });
                    } catch (IllegalStateException failure) {
                        if (failure != abort) throw failure;
                    }
                });
                await(firstUpdated);
                var right = executor.submit(() -> {
                    try {
                        new JdbcTransactionManager(fixture.connections).inTransaction(() -> {
                            secondPid.set(fixture.backendPid());
                            secondStarted.countDown();
                            fixture.users.update(second);
                        });
                        return true;
                    } catch (ConcurrentModificationException conflict) {
                        return false;
                    }
                });
                await(secondStarted);
                assertNotEquals(firstPid.get(), secondPid.get());
                fixture.waitForBlocking(firstPid.get(), secondPid.get());
                releaseFirst.countDown();
                left.get(20, TimeUnit.SECONDS);
                assertEquals(!commitFirst, right.get(20, TimeUnit.SECONDS));
                UserAccount current = fixture.current();
                assertEquals(1, current.getSecurityVersion());
                assertEquals(commitFirst, current.isActive());
                assertEquals(commitFirst ? fixture.sellerId : null, current.getSellerId());
                if (commitFirst) {
                    assertThrows(ConcurrentModificationException.class, () -> fixture.users.update(second));
                }
            } finally {
                releaseFirst.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(25, TimeUnit.SECONDS),
                        "Escritores sin terminar: se omite limpieza; revisar fixtures exclusivos.");
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(20, TimeUnit.SECONDS), "Timeout de coordinación del fixture.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Coordinación interrumpida.");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final JdbcConnectionProvider connections;
        final UserRepositoryJdbc users;
        final String prefix = "UV104-" + UUID.randomUUID().toString().replace("-", "") + "-";
        final String sellerId = "SV104-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        final String hash = PasswordHasher.hash("Security-Version-Fixture-Only");
        final List<String> names = new ArrayList<>();
        UserAccount user;
        boolean sellerAttempted;
        ExecutorService writers;

        Fixture(DatabaseConfig config) {
            connections = new JdbcConnectionProvider(config, () -> openVerified(config));
            users = new UserRepositoryJdbc(connections);
        }

        /** No adjunta causas SQL del servidor a fallos del fixture ni imprime hashes. */
        <T> T sql(SqlFunction<T> operation) {
            try {
                return connections.withConnection(operation);
            } catch (JdbcPersistenceException failure) {
                throw new JdbcPersistenceException("Falló una operación SQL del fixture; no se muestran datos del servidor.");
            } catch (AssertionError failure) {
                if (failure.getSuppressed().length > 0) {
                    throw new AssertionError("Falló una comprobación del fixture y el cierre de recursos SQL.");
                }
                throw failure;
            }
        }

        String remember(String suffix) {
            String name = prefix + suffix;
            if (users.findByUsername(name).isPresent()) throw new IllegalStateException("Fixture ya existente.");
            names.add(name);
            return name;
        }

        void create() {
            sql(connection -> {
                try (var check = connection.prepareStatement("SELECT 1 FROM public.seller WHERE id = ?")) {
                    check.setString(1, sellerId);
                    try (var rows = check.executeQuery()) {
                        if (rows.next()) throw new IllegalStateException("Vendedor fixture ya existente.");
                    }
                }
                sellerAttempted = true;
                try (var insert = connection.prepareStatement(
                        "INSERT INTO public.seller (id, name, phone, address) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, sellerId);
                    insert.setString(2, "Vendedor fixture V104");
                    insert.setString(3, "3000000000");
                    insert.setString(4, "Dirección ficticia");
                    assertEquals(1, insert.executeUpdate());
                }
                return null;
            });
            user = new UserAccount(null, remember("account"), hash, UserRole.WORKER, null);
            user.setSecurityVersion(99);
            users.save(user);
            assertEquals(0, user.getSecurityVersion());
        }

        UserAccount current() {
            return users.findById(user.getId().toString()).orElseThrow();
        }

        void sqlUpdate(String assignments, Object... values) {
            // Los fragmentos proceden exclusivamente de constantes de estas pruebas.
            sql(connection -> {
                try (var statement = connection.prepareStatement(
                        "UPDATE public.user_account SET " + assignments + " WHERE id = ?")) {
                    for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                    statement.setLong(values.length + 1, user.getId());
                    assertEquals(1, statement.executeUpdate());
                }
                return null;
            });
        }

        int backendPid() {
            return sql(connection -> {
                try (var statement = connection.prepareStatement("SELECT pg_backend_pid()");
                     var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    return rows.getInt(1);
                }
            });
        }

        void waitForBlocking(int blocker, int blocked) {
            sql(connection -> {
                long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                try (var statement = connection.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))")) {
                    statement.setInt(1, blocker);
                    statement.setInt(2, blocked);
                    while (System.nanoTime() < deadline) {
                        try (var rows = statement.executeQuery()) {
                            assertTrue(rows.next());
                            if (rows.getBoolean(1)) return null;
                        }
                    }
                }
                fail("No se observó el bloqueo PostgreSQL entre los dos escritores.");
                return null;
            });
        }

        @Override
        public void close() {
            if (writers != null && !writers.isTerminated()) {
                throw new IllegalStateException("Limpieza omitida: escritores activos; revisar fixtures exclusivos.");
            }
            RuntimeException cleanup = null;
            for (String name : names) {
                try {
                    sql(connection -> {
                        try (var statement = connection.prepareStatement("DELETE FROM public.user_account WHERE username = ?")) {
                            statement.setString(1, name);
                            statement.executeUpdate();
                        }
                        return null;
                    });
                } catch (RuntimeException failure) {
                    cleanup = new IllegalStateException("No se pudieron limpiar todos los usuarios fixture.");
                }
            }
            if (sellerAttempted) {
                try {
                    sql(connection -> {
                        try (var statement = connection.prepareStatement("DELETE FROM public.seller WHERE id = ?")) {
                            statement.setString(1, sellerId);
                            statement.executeUpdate();
                        }
                        return null;
                    });
                } catch (RuntimeException failure) {
                    cleanup = new IllegalStateException("No se pudo limpiar el vendedor fixture.");
                }
            }
            if (cleanup != null) throw cleanup;
        }
    }
}
