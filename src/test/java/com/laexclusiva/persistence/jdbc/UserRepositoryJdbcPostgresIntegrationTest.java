package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.Seller;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.jdbc.SellerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.security.PasswordHasher;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integraciones opcionales de usuarios, sin migraciones y con limpieza de fixtures propios.
 * Requieren autorización para escribir, {@code -Dlaexclusiva.test.postgres.users=true}
 * y variables JDBC dirigidas exclusivamente a {@code la_exclusiva_test} con V001, V104 y V106 preparadas.
 * No aplican migraciones. La prueba temporal de IDs no copia el trigger ni valida sus incrementos.
 */
class UserRepositoryJdbcPostgresIntegrationTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 1, 2, 3, 4, 5, 123456000);

    @Test
    void persistsAcrossConnectionsAndUpdatesWithoutChangingHistoricalCreationTime() {
        try (Fixture fixture = fixture()) {
            UserAccount original = fixture.account("original");
            original.setSellerId(fixture.sellerId);
            original.setActive(false);
            fixture.persist(original);
            assertNotNull(original.getId());

            UserRepositoryJdbc restarted = new UserRepositoryJdbc(new JdbcConnectionProvider(fixture.config));
            UserAccount restored = restarted.findByUsername("  " + original.getUsername().toUpperCase(Locale.ROOT)
                    + "  ").orElseThrow();
            assertEquals(original.getId(), restored.getId());
            assertEquals(fixture.hash, restored.getPasswordHash());
            assertFalse(restored.isActive());
            assertEquals(UserRole.WORKER, restored.getRole());
            assertEquals(fixture.sellerId, restored.getSellerId());
            assertEquals(CREATED, restored.getCreatedAt());

            restored.setUsername(fixture.prefix + "updated");
            restored.setRole(UserRole.ADMINISTRATOR);
            restored.setActive(true);
            restored.setSellerId(null);
            restored.setCreatedAt(CREATED.plusYears(2));
            fixture.rememberName(restored.getUsername());
            restarted.update(restored);
            UserAccount updated = fixture.users.findById(original.getId().toString()).orElseThrow();
            assertEquals(restored.getUsername(), updated.getUsername());
            assertEquals(UserRole.ADMINISTRATOR, updated.getRole());
            assertTrue(updated.isActive());
            assertNull(updated.getSellerId());
            assertEquals(CREATED, updated.getCreatedAt());
            assertTrue(fixture.users.findAll().stream().anyMatch(user -> user.getId().equals(original.getId())));
            restarted.delete(original.getId().toString());
            assertTrue(fixture.users.findById(original.getId().toString()).isEmpty());
            assertDoesNotThrow(() -> restarted.delete(original.getId().toString()));
        }
    }

    @Test
    void rejectsNormalizedDuplicatesUnknownSellersAndRollsBackWithTheOuterTransaction() {
        try (Fixture fixture = fixture()) {
            UserAccount original = fixture.account("first");
            fixture.persist(original);
            UserAccount duplicate = fixture.account("duplicate");
            duplicate.setUsername("  " + original.getUsername().toUpperCase(Locale.ROOT) + "  ");
            assertThrows(IllegalArgumentException.class, () -> fixture.persist(duplicate));
            assertNull(duplicate.getId());

            UserAccount second = fixture.account("second");
            fixture.persist(second);
            second.setUsername(original.getUsername().toUpperCase(Locale.ROOT));
            assertThrows(IllegalArgumentException.class, () -> fixture.users.update(second));
            assertEquals(fixture.prefix + "second",
                    fixture.users.findById(second.getId().toString()).orElseThrow().getUsername());
            UserAccount badSeller = fixture.account("bad-seller");
            badSeller.setSellerId("MISSING-" + UUID.randomUUID().toString().substring(0, 20));
            assertThrows(IllegalArgumentException.class, () -> fixture.persist(badSeller));
            assertTrue(fixture.users.findByUsername(badSeller.getUsername()).isEmpty());

            UserAccount historical = fixture.account("historical");
            historical.setId(Long.MAX_VALUE - java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L));
            fixture.persist(historical);
            assertEquals(historical.getId(), fixture.users.findById(historical.getId().toString()).orElseThrow().getId());
            UserAccount duplicateId = fixture.account("duplicate-id");
            duplicateId.setId(historical.getId());
            assertThrows(IllegalArgumentException.class, () -> fixture.persist(duplicateId));

            UserAccount rolledBack = fixture.account("rollback");
            IllegalStateException abort = new IllegalStateException("Rollback del fixture de usuarios");
            assertSame(abort, assertThrows(IllegalStateException.class,
                    () -> new JdbcTransactionManager(fixture.connections).inTransaction(() -> {
                        fixture.persist(rolledBack);
                        assertTrue(fixture.users.findById(rolledBack.getId().toString()).isPresent());
                        throw abort;
                    })));
            assertTrue(fixture.users.findByUsername(rolledBack.getUsername()).isEmpty());
            assertTrue(fixture.users.findById(rolledBack.getId().toString()).isEmpty());
        }
    }

    @Test
    void updateAndDeleteAreRestoredByAnOuterRollback() {
        try (Fixture fixture = fixture()) {
            UserAccount original = fixture.account("rollback-existing");
            fixture.persist(original);
            String renamed = fixture.prefix + "rollback-renamed";
            fixture.rememberName(renamed);
            IllegalStateException abort = new IllegalStateException("Revertir actualización y borrado");
            assertSame(abort, assertThrows(IllegalStateException.class,
                    () -> new JdbcTransactionManager(fixture.connections).inTransaction(() -> {
                        UserAccount changed = fixture.users.findById(original.getId().toString()).orElseThrow();
                        changed.setUsername(renamed);
                        changed.setActive(false);
                        fixture.users.update(changed);
                        assertFalse(fixture.users.findById(original.getId().toString()).orElseThrow().isActive());
                        fixture.users.delete(original.getId().toString());
                        assertTrue(fixture.users.findById(original.getId().toString()).isEmpty());
                        throw abort;
                    })));
            UserAccount restored = fixture.users.findById(original.getId().toString()).orElseThrow();
            assertEquals(original.getUsername(), restored.getUsername());
            assertTrue(restored.isActive());
            assertEquals(original.getPasswordHash(), restored.getPasswordHash());
        }
    }

    @Test
    void generatedIdsSkipHistoricalIdsUsingAnIsolatedTemporaryIdentitySequence() {
        DatabaseConfig config = authorizedConfig();
        Connection[] physicalConnection = new Connection[1];
        JdbcConnectionProvider connections = new JdbcConnectionProvider(config, () -> {
            // Una segunda apertura indicaría que se perdió la conexión transaccional.
            assertNull(physicalConnection[0], "La prueba debe usar una única conexión física.");
            physicalConnection[0] = DriverManager.getConnection(
                    config.getUrl(), config.getUser(), config.getPassword());
            return physicalConnection[0];
        });
        UserRepositoryJdbc users = new UserRepositoryJdbc(connections);
        String hash = PasswordHasher.hash("Temporary-Sequence-Test-Only");
        long temporaryTableOid = new JdbcTransactionManager(connections).inTransaction(() -> {
            // Se valida la base en la misma conexión que ejecutará todas las operaciones.
            verifyDatabase(connections);
            long tableOid = connections.withConnection(connection -> {
                // El coordinador entrega un proxy protector, no la referencia física original.
                // Ambas referencias deben consultar el mismo proceso servidor durante esta transacción.
                int physicalBackendPid;
                try (PreparedStatement statement = physicalConnection[0].prepareStatement("SELECT pg_backend_pid()");
                     ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    physicalBackendPid = rows.getInt(1);
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
                     ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(physicalBackendPid, rows.getInt(1),
                            "El wrapper debe utilizar la misma sesión PostgreSQL que la conexión física.");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SET LOCAL search_path = pg_temp, public")) {
                    statement.execute();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "CREATE TEMPORARY TABLE user_account (LIKE public.user_account INCLUDING ALL)")) {
                    statement.execute();
                }
                // También se exige una identidad temporal para no consumir una secuencia persistente
                // si el esquema instalado difiere del previsto por V001.
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT t.oid, t.relpersistence = 't' AND t.relnamespace = pg_my_temp_schema() "
                        + "AND to_regclass('user_account') = t.oid "
                        + "AND t.oid <> 'public.user_account'::regclass AS isolated_table, "
                        + "a.attidentity IN ('a', 'd') AND s.relpersistence = 't' "
                        + "AND s.relnamespace = pg_my_temp_schema() AS isolated_identity "
                        + "FROM pg_catalog.pg_class t "
                        + "JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attname = 'id' "
                        + "LEFT JOIN pg_catalog.pg_class s ON s.oid = "
                        + "pg_get_serial_sequence('pg_temp.user_account', 'id')::regclass "
                        + "WHERE t.oid = to_regclass('pg_temp.user_account')");
                     ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "Debe existir la tabla temporal esperada.");
                    assertTrue(rows.getBoolean("isolated_table"),
                            "Se aborta: user_account no resuelve exclusivamente a la tabla temporal.");
                    assertTrue(rows.getBoolean("isolated_identity"),
                            "Se aborta: la identidad no pertenece al esquema temporal.");
                    return rows.getLong("oid");
                }
            });
            for (long id = 1; id <= 3; id++) {
                users.save(new UserAccount(id, "historical-" + id, hash, UserRole.WORKER, null));
            }
            UserAccount generated = new UserAccount(null, "generated", hash, UserRole.WORKER, null);
            users.save(generated);
            assertEquals(4L, generated.getId());
            assertEquals(List.of(1L, 2L, 3L, 4L), users.findAll().stream().map(UserAccount::getId).toList());
            assertEquals("historical-1", users.findById("1").orElseThrow().getUsername());
            return tableOid;
        });
        assertDoesNotThrow(() -> assertTrue(physicalConnection[0].isClosed(),
                "La conexión propietaria de la tabla temporal debe estar cerrada."));
        // Otra sesión comprueba el catálogo: no basta con que la tabla deje de ser visible.
        new JdbcConnectionProvider(config).withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_class WHERE oid = ?::oid)")) {
                statement.setLong(1, temporaryTableOid);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertFalse(rows.getBoolean(1), "La tabla temporal debe desaparecer al cerrar su sesión.");
                }
            }
            return null;
        });
    }

    @Test
    void concurrentNormalizedUsernameInsertionsCreateOnlyOneAccount() throws Exception {
        try (Fixture fixture = fixture()) {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            fixture.writers = executor;
            try {
                Future<Boolean> first = executor.submit(() -> insertAfterStart(fixture,
                        fixture.account("concurrent"), start));
                UserAccount other = fixture.account("concurrent");
                other.setUsername(" " + other.getUsername().toUpperCase(Locale.ROOT) + " ");
                Future<Boolean> second = executor.submit(() -> insertAfterStart(fixture, other, start));
                start.countDown();
                assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                        + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
                assertTrue(fixture.users.findByUsername(fixture.prefix + "concurrent").isPresent());
                assertEquals(1, fixture.userIds.size());
            } finally {
                start.countDown();
                stopWriters(executor);
            }
        }
    }

    static void stopWriters(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout de escritores: se omite la limpieza para evitar carreras; "
                            + "los fixtures exclusivos requerirán revisión posterior.");
                }
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera de escritores interrumpida; la limpieza solo se permite si terminaron.");
        }
    }

    private static boolean insertAfterStart(Fixture fixture, UserAccount account, CountDownLatch start)
            throws InterruptedException {
        start.await();
        try {
            fixture.persist(account);
            return true;
        } catch (IllegalArgumentException duplicate) {
            return false;
        }
    }

    private static Fixture fixture() {
        Fixture fixture = new Fixture(authorizedConfig());
        try {
            fixture.createSeller();
            return fixture;
        } catch (RuntimeException | Error failure) {
            try {
                fixture.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static DatabaseConfig authorizedConfig() {
        Assumptions.assumeTrue(Boolean.getBoolean("laexclusiva.test.postgres.users"),
                "Omitida: falta autorización explícita para las integraciones de usuarios.");
        Assumptions.assumeTrue(System.getenv(DatabaseConfig.TEST_URL_ENV) != null,
                "Omitida: falta la URL de la base aislada.");
        return DatabaseConfig.fromTestEnvironment();
    }

    private static void verifyDatabase(JdbcConnectionProvider connections) {
        String database = connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT current_database()");
                 ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new java.sql.SQLException("No se pudo verificar la base.");
                return rows.getString(1);
            }
        });
        if (!"la_exclusiva_test".equals(database)) throw new IllegalStateException("Base de pruebas incorrecta.");
    }

    /** Usa IDs aleatorios propios y nunca borra por rol, por rango de IDs ni por condiciones globales. */
    static final class Fixture implements AutoCloseable {
        private final DatabaseConfig config;
        private final JdbcConnectionProvider connections;
        private final UserRepositoryJdbc users;
        private final List<Long> userIds = Collections.synchronizedList(new ArrayList<>());
        private final Set<String> userNames = new LinkedHashSet<>();
        ExecutorService writers;
        private boolean sellerAttempted;
        private final String prefix = "UJDBC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT) + "-";
        private final String sellerId = "SEL-USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        private final String hash = PasswordHasher.hash("Temporary-Integration-Only");

        private Fixture(DatabaseConfig config) {
            this(config, new JdbcConnectionProvider(config));
        }

        // Inyección solo para probar la seguridad de la limpieza sin abrir PostgreSQL.
        Fixture(DatabaseConfig config, JdbcConnectionProvider connections) {
            this.config = config;
            this.connections = connections;
            users = new UserRepositoryJdbc(connections);
            verifyDatabase(connections);
        }

        void createSeller() {
            SellerRepositoryJdbc sellers = new SellerRepositoryJdbc(connections);
            if (sellers.findById(sellerId).isPresent()) throw new IllegalStateException("El ID de fixture ya existía.");
            sellerAttempted = true;
            sellers.save(new Seller(sellerId, "Vendedor ficticio",
                    "3000000000", "Dirección ficticia"));
        }

        UserAccount account(String suffix) {
            UserAccount account = new UserAccount(null, prefix + suffix, hash, UserRole.WORKER, null);
            account.setCreatedAt(CREATED);
            return account;
        }

        void persist(UserAccount account) {
            rememberName(account.getUsername());
            users.save(account);
            userIds.add(account.getId());
        }

        synchronized void rememberName(String name) {
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            if (!normalized.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("El nombre no pertenece al espacio exclusivo del fixture.");
            }
            if (!userNames.contains(normalized)) {
                if (users.findByUsername(name).isPresent()) {
                    throw new IllegalStateException("El nombre de fixture ya existía: no se autoriza su limpieza.");
                }
                userNames.add(normalized);
            }
        }

        @Override
        public void close() {
            if (writers != null && !writers.isTerminated()) {
                throw new IllegalStateException("Limpieza omitida: aún hay tareas que pueden escribir en los fixtures.");
            }
            List<String> names;
            synchronized (this) { names = new ArrayList<>(userNames); }
            RuntimeException cleanupFailure = null;
            for (String name : names) {
                try {
                    connections.withConnection(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "DELETE FROM user_account WHERE lower(btrim(username)) = lower(btrim(?))")) {
                            statement.setString(1, name);
                            statement.executeUpdate();
                        }
                        return null;
                    });
                } catch (RuntimeException failure) {
                    if (cleanupFailure == null) cleanupFailure = new IllegalStateException("No se limpiaron todos los fixtures de usuarios.");
                }
            }
            if (sellerAttempted) {
                try {
                    connections.withConnection(connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM seller WHERE id = ?")) {
                            statement.setString(1, sellerId);
                            statement.executeUpdate();
                        }
                        return null;
                    });
                } catch (RuntimeException failure) {
                    if (cleanupFailure == null) cleanupFailure = new IllegalStateException("No se pudo limpiar el vendedor del fixture.");
                }
            }
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }
}
