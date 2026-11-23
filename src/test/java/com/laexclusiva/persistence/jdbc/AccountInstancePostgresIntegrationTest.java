package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.service.AdministrativeSessionValidator;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.service.AuthorizationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Laboratorio opt-in V104/V105/V106. No aplica migraciones; todos sus fixtures hacen rollback. */
class AccountInstancePostgresIntegrationTest {
    static final String ENABLE = "laexclusiva.test.postgres.accountInstance";

    /** Cada conexión valida el destino y las protecciones antes de permitir fixtures. */
    private static Connection openVerified(DatabaseConfig config) throws SQLException {
        UserSecurityVersionPostgresIntegrationTest.validateLocalUrl(config.getUrl());
        Properties properties = new Properties();
        properties.setProperty("user", config.getUser());
        properties.setProperty("password", config.getPassword());
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        Connection connection = DriverManager.getConnection(config.getUrl(), properties);
        try {
            // Barrera de destino antes de llamar al validador V104, que configura la sesión.
            try (var statement = connection.prepareStatement("""
                    SELECT current_database() = 'la_exclusiva_test'
                      AND current_user = 'identity_validation_owner'
                      AND current_setting('cluster_name') = 'laexclusiva-identity-validation'
                      AND current_setting('server_version_num')::integer / 10000 = 18
                      AND host(inet_server_addr()) IN ('127.0.0.1', '::1')
                      AND inet_server_port() = 5432
                    """)) {
                statement.setQueryTimeout(5);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || !rows.getBoolean(1)) throw new SQLException("Laboratorio no reconocido.");
                }
            }
            UserSecurityVersionPostgresIntegrationTest.verifyConnection(connection);
            try (var statement = connection.prepareStatement("""
                    SELECT a.attnotnull AND a.atttypid='uuid'::regtype
                      AND EXISTS (SELECT FROM pg_attrdef d WHERE d.adrelid=u.oid AND d.adnum=a.attnum
                        AND pg_get_expr(d.adbin,d.adrelid) IN ('gen_random_uuid()', 'pg_catalog.gen_random_uuid()'))
                      AND EXISTS (SELECT FROM pg_constraint c WHERE c.conrelid=u.oid
                        AND c.conname='uq_user_account_instance_id' AND c.contype='u'
                        AND c.convalidated AND c.conkey=ARRAY[a.attnum]::smallint[])
                      AND t.tgtype=23 AND t.tgenabled='O' AND t.tgqual IS NULL
                      AND t.tgattr::text='' AND NOT t.tgisinternal AND t.tgnargs=0
                      AND p.oid=to_regprocedure('public.guard_user_account_instance_identity()')
                      AND NOT p.prosecdef AND p.prorettype='trigger'::regtype
                      AND p.pronargs=0 AND p.prokind='f'
                      AND (SELECT lanname FROM pg_language WHERE oid=p.prolang)='plpgsql'
                      AND p.proconfig=ARRAY['search_path=pg_catalog']::text[],
                      p.prosrc,
                      i.indisunique AND i.indisvalid AND i.indisready
                        AND i.indnkeyatts=1 AND i.indnatts=1 AND i.indkey::text=s.attnum::text,
                      pg_get_expr(i.indpred,i.indrelid)
                    FROM pg_class u
                    JOIN pg_attribute a ON a.attrelid=u.oid AND a.attname='account_instance_id' AND NOT a.attisdropped
                    JOIN pg_attribute s ON s.attrelid=u.oid AND s.attname='seller_id' AND NOT s.attisdropped
                    JOIN pg_trigger t ON t.tgrelid=u.oid AND t.tgname='trg_user_account_instance_identity'
                    JOIN pg_proc p ON p.oid=t.tgfoid
                    JOIN pg_index i ON i.indrelid=u.oid AND i.indexrelid=to_regclass('public.uq_user_account_active_seller')
                    WHERE u.oid=to_regclass('public.user_account')
                    """)) {
                statement.setQueryTimeout(5);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || !rows.getBoolean(1) || !expectedBody().equals(normalize(rows.getString(2)))
                            || !rows.getBoolean(3) || !expectedSellerPredicate(rows.getString(4)) || rows.next()) {
                        throw new SQLException("V105/V106 incompletas; no se permiten fixtures.");
                    }
                }
            }
            return connection;
        } catch (Exception failure) {
            try { connection.close(); } catch (SQLException ignored) { /* Sin secretos del servidor. */ }
            throw new SQLException("No se pudo verificar el laboratorio V104/V105/V106.");
        }
    }

    static boolean expectedSellerPredicate(String expression) {
        return expression != null && expression.replaceAll("[\\s()]", "")
                .toLowerCase(java.util.Locale.ROOT).equals("active=trueandseller_idisnotnull");
    }

    private static String expectedBody() throws Exception {
        String sql = Files.readString(Path.of("database/migrations/V106__user_account_instance_identity.sql"));
        return normalize(sql.split("\\$function\\$", -1)[1]);
    }

    private static String normalize(String value) { return value == null ? "" : value.replace("\r\n", "\n").strip(); }

    private static final class RollbackFixture extends RuntimeException { }

    private static void inRollbackFixture(Consumer<UserRepositoryJdbc> test) {
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLE), "Sin autorización específica para PostgreSQL.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        UserSecurityVersionPostgresIntegrationTest.validateLocalUrl(config.getUrl());
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config, () -> openVerified(config));
        var transactions = new JdbcTransactionManager(provider);
        var users = new UserRepositoryJdbc(provider);
        runRollbackFixture(transactions, () -> test.accept(users));
    }

    /** No confunde la excepción esperada con un rollback o cierre JDBC fallido. */
    static void runRollbackFixture(JdbcTransactionManager transactions, Runnable test) {
        RollbackFixture aborted = assertThrows(RollbackFixture.class, () -> transactions.inTransaction(() -> {
            test.run();
            throw new RollbackFixture();
        }));
        if (aborted.getSuppressed().length != 0) {
            throw new AssertionError("No se pudo acreditar la reversión y cierre del fixture; revisar el laboratorio.");
        }
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void recreatedNumericIdCannotRestoreAnyOldSession(UserRole replacementRole) {
        inRollbackFixture(users -> {
            String name = "iv_java_" + UUID.randomUUID();
            String password = "Synthetic fixture identity only";
            UserAccount original = new UserAccount(null, name, PasswordHasher.hash(password), UserRole.ADMINISTRATOR, null);
            users.save(original);
            var auth = new AuthenticationService(users);
            var old = auth.createSession(name, password).orElseThrow();
            UUID oldIdentity = original.getAccountInstanceId();
            users.delete(original.getId().toString());
            UserAccount replacement = new UserAccount(original.getId(), name, original.getPasswordHash(), replacementRole, null);
            replacement.setAccountInstanceId(oldIdentity);
            users.save(replacement);
            assertNotEquals(oldIdentity, replacement.getAccountInstanceId());
            assertEquals(0, replacement.getSecurityVersion());
            assertThrows(SecurityException.class, () -> auth.sessions().validate(old));
            assertThrows(SecurityException.class, () -> new AdministrativeSessionValidator(users, new AuthorizationService(), auth).validate(old));
            original.setActive(false);
            assertThrows(java.util.ConcurrentModificationException.class, () -> users.update(original));
            var fresh = auth.createSession(name, password).orElseThrow();
            assertEquals(replacement.getAccountInstanceId(), auth.sessions().validate(fresh).accountInstanceId());
            auth.revoke(fresh); auth.revoke(fresh);
            assertThrows(SecurityException.class, () -> auth.sessions().validate(fresh));
        });
    }

    @Test
    void metadataUpdatePreservesHistoricalHashAndInstance() throws Exception {
        String password = "Historical fixture only";
        byte[] salt = new byte[8]; java.util.Arrays.fill(salt, (byte) 7);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 1000, 128);
        String hash;
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try { hash = "$pbkdf2-sha256$i=1000$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(key); }
            finally { java.util.Arrays.fill(key, (byte) 0); }
        } finally { spec.clearPassword(); java.util.Arrays.fill(salt, (byte) 0); }
        inRollbackFixture(users -> {
            UserAccount account = new UserAccount(null, "iv_hash_" + UUID.randomUUID(), hash, UserRole.WORKER, null);
            account.setCreatedAt(java.time.LocalDateTime.of(2020, 1, 2, 3, 4, 5));
            users.save(account);
            UUID identity = account.getAccountInstanceId();
            account.setRole(UserRole.ADMINISTRATOR); account.setActive(false);
            users.update(account);
            UserAccount loaded = users.findById(account.getId().toString()).orElseThrow();
            assertEquals(identity, loaded.getAccountInstanceId()); assertEquals(1, loaded.getSecurityVersion());
            assertEquals(account.getCreatedAt(), loaded.getCreatedAt());
            assertTrue(hash.equals(loaded.getPasswordHash()), "Hash histórico modificado");
            assertTrue(PasswordHasher.verify(password, loaded.getPasswordHash()));
        });
    }
}
