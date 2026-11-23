package com.laexclusiva.persistence.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica barreras del plan preparado; no valida sintaxis SQL, Docker ni ejecución PostgreSQL. */
class IdentityValidationPlanTest {
    private static String asset(String name) throws Exception {
        return Files.readString(Path.of("database/validation/identity", name)).replace("\r\n", "\n");
    }

    @Test
    void laboratoryHasNoPersistentDataVolumeOrExternalNetworkAndRequiresImageAndSecret() throws Exception {
        String compose = asset("compose.yaml");
        assertFalse(compose.contains("ports:"));
        assertTrue(compose.contains("listen_addresses=127.0.0.1"));
        assertTrue(compose.contains("network_mode: service:postgres"));
        assertTrue(compose.contains("internal: true")); assertTrue(compose.contains("read_only: true"));
        assertTrue(compose.contains("tmpfs:\n      - /var/lib/postgresql"));
        assertTrue(compose.contains("LAEXCLUSIVA_PG18_IMAGE:?")); assertTrue(compose.contains("pull_policy: never"));
        assertTrue(compose.contains("LAEXCLUSIVA_LAB_SECRET_FILE:?"));
        assertFalse(compose.contains("docker-entrypoint-initdb.d")); assertFalse(compose.contains("network_mode: host"));
        String runner = asset("run-java.sh");
        assertTrue(runner.contains("exec mvn -o")); assertFalse(runner.contains("\nset -x"));
        assertTrue(runner.contains("grep -q 'Temurin'"));
        assertTrue(asset(".gitattributes").contains("*.sh text eol=lf"));
    }

    @Test
    void guardPrecedesEveryMigrationAndHistoricalAccountsPrecedeBackfill() throws Exception {
        String sql = asset("validate.psql");
        assertTrue(sql.indexOf("\\ir guard.psql") < sql.indexOf("\\ir ../../migrations/"));
        assertTrue(sql.contains("Se requiere public vacío"));
        int base = sql.indexOf("\\ir ../../migrations/V001");
        int fixture = sql.indexOf("INSERT INTO public.user_account");
        int version = sql.indexOf("\\ir ../../migrations/V104");
        int seller = sql.indexOf("\\ir ../../migrations/V105");
        int instance = sql.indexOf("\\ir ../../migrations/V106");
        assertTrue(base < fixture && fixture < version && version < seller && seller < instance);
        assertFalse(sql.contains("TRUNCATE")); assertFalse(sql.contains("DROP DATABASE"));
    }

    @Test
    void sqlGuardRequiresDedicatedClusterDatabaseUserAndPostgresVersion() throws Exception {
        String guard = asset("guard.psql");
        assertTrue(guard.contains("\\set ON_ERROR_STOP on"));
        assertTrue(guard.contains("current_database() <> 'la_exclusiva_test'"));
        assertTrue(guard.contains("current_user <> 'identity_validation_owner'"));
        assertTrue(guard.contains("current_setting('cluster_name') <> 'laexclusiva-identity-validation'"));
        assertTrue(guard.contains("/ 10000 <> 18")); assertTrue(guard.contains("'origin'"));
    }

    @Test
    void preparedAssertionsCoverBackfillRollbackAndTargetedUniquenessFailure() throws Exception {
        String sql = asset("validate.psql");
        assertTrue(sql.contains("count(DISTINCT account_instance_id)"));
        assertTrue(sql.contains("ROLLBACK;")); assertTrue(sql.contains("EXCEPTION WHEN check_violation"));
        assertTrue(sql.contains("GET STACKED DIAGNOSTICS index_name=CONSTRAINT_NAME"));
        assertTrue(sql.contains("index_name <> 'uq_user_account_active_seller'"));
        assertTrue(sql.contains("i.account_instance_id IS DISTINCT FROM a.account_instance_id"));
    }

    @Test
    void conservativeIndexPredicateAcceptsOnlyExpectedStructure() {
        assertTrue(AccountInstancePostgresIntegrationTest.expectedSellerPredicate("((active = true) AND (seller_id IS NOT NULL))"));
        assertFalse(AccountInstancePostgresIntegrationTest.expectedSellerPredicate(null));
        assertFalse(AccountInstancePostgresIntegrationTest.expectedSellerPredicate("true"));
        assertFalse(AccountInstancePostgresIntegrationTest.expectedSellerPredicate("active = true OR seller_id IS NOT NULL"));
        assertFalse(AccountInstancePostgresIntegrationTest.expectedSellerPredicate("active = true AND seller_id IS NOT NULL OR true"));
    }
}
