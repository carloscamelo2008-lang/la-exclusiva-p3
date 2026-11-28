package com.laexclusiva.persistence.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Verifica barreras del plan preparado; no valida sintaxis SQL, Docker ni ejecución PostgreSQL. */
class IdentityValidationPlanTest {
    @ParameterizedTest
    @ValueSource(strings = {"none", "rollback", "close"})
    void rollbackFixtureNeverHidesDriverCleanupFailure(String failureAt) {
        int[] calls = new int[4]; // operación, rollback, cierre, commit
        java.sql.Connection driver = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "setAutoCommit" -> null;
                        case "rollback" -> {
                            calls[1]++;
                            if (failureAt.equals("rollback")) throw new java.sql.SQLException("Driver fixture detail must stay private");
                            yield null;
                        }
                        case "close" -> {
                            calls[2]++;
                            if (failureAt.equals("close")) throw new java.sql.SQLException("Driver fixture detail must stay private");
                            yield null;
                        }
                        case "commit" -> { calls[3]++; yield null; }
                        default -> throw new AssertionError("Operación JDBC inesperada: " + method.getName());
                    };
                });
        var config = new DatabaseConfig("jdbc:postgresql://localhost:5432/la_exclusiva_test", "fixture", "unused");
        var provider = new JdbcConnectionProvider(config, () -> driver);
        Runnable fixture = () -> AccountInstancePostgresIntegrationTest.runRollbackFixture(
                new JdbcTransactionManager(provider), () -> calls[0]++);
        if (!failureAt.equals("none")) {
            AssertionError error = assertThrows(AssertionError.class, fixture::run);
            assertFalse(error.getMessage().contains("Driver fixture detail"));
        } else {
            fixture.run();
        }
        assertArrayEquals(new int[]{1, 1, 1, 0}, calls);
    }

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
        assertTrue(runner.contains("mvn -o")); assertFalse(runner.contains("\nset -x"));
        assertTrue(runner.contains("grep -q 'Temurin'"));
        assertTrue(asset(".gitattributes").contains("*.sh text eol=lf"));
    }

    @Test
    void mavenCacheIsCopiedIntoTheJobSeedAndTestsRemainOffline() throws Exception {
        String lab = asset("actions-lab.py");
        assertTrue(lab.contains("source = Path.home() / \".m2\" / \"repository\""));
        assertTrue(lab.contains("destination = BASE / \"m2\" / \"repository\""));
        assertTrue(lab.contains("if entry.is_symlink():"));
        assertTrue(lab.contains("shutil.copytree(resolved_source, destination)"));
        assertTrue(lab.indexOf("    seed_maven_repository()") < lab.indexOf("    for cycle in (\"first\", \"repeat\")"));
        assertTrue(asset("compose.yaml").contains("target: /seed-repository\n        read_only: true"));
        String runner = asset("run-java.sh");
        assertTrue(runner.contains("cp -a /seed-repository/. /root/.m2/repository/"));
        assertTrue(runner.contains("mvn -o"));
        assertTrue(lab.contains("executed == expected_suites"));
    }

    @Test
    void customerPrRunsBothJdbcSuitesAndAppliesItsMigrationsInOrder() throws Exception {
        String runner = asset("run-java.sh");
        assertTrue(runner.contains("CustomerCrudPostgresIntegrationTest,CustomerDuplicatePostgresIntegrationTest"));
        String lab = asset("actions-lab.py");
        assertTrue(lab.contains("\"feature/jesus-customers-crud\": \"pr29-customers\""));
        String sql = asset("application.psql");
        int phone = sql.indexOf("\\ir ../../migrations/V110");
        int credit = sql.indexOf("\\ir ../../migrations/V111");
        int actors = sql.indexOf("\\ir ../../migrations/V112");
        int archivedCredit = sql.indexOf("\\ir ../../migrations/V113");
        int productImages = sql.indexOf("\\ir ../../migrations/V114");
        assertTrue(phone >= 0 && phone < credit && credit < actors && actors < archivedCredit
                && archivedCredit < productImages);
    }

    @Test
    void commercialSchemaVerifierCoversPostV113AndV114Contracts() throws Exception {
        String application = asset("application.psql");
        String validation = asset("validate.psql");
        assertTrue(application.contains("legacy_credit_limit=5000.00"));
        assertTrue(application.contains("V113 dejó visible el nombre retirado credit_limit"));
        assertTrue(application.contains("V114 no conservó la fila histórica sin fotografía"));
        assertTrue(validation.contains("\\ir ../../migrations/V113__retire_customer_credit_limit.sql"));
        assertTrue(validation.contains("\\ir ../../migrations/V114__product_image_reference.sql"));
        assertTrue(validation.contains("ck_beer_product_image_filename"));
        assertTrue(validation.contains("repeat('c', 64) || '.gif'"));
    }

    @Test
    void customerCreditRetirementPreservesValuesWithoutDeletingRows() throws Exception {
        String migration = Files.readString(Path.of(
                "database/migrations/V113__retire_customer_credit_limit.sql"));
        assertTrue(migration.contains("RENAME COLUMN credit_limit TO legacy_credit_limit"));
        assertTrue(migration.contains("RENAME CONSTRAINT ck_customer_credit_limit TO ck_customer_legacy_credit_limit"));
        assertTrue(migration.contains("COMMENT ON COLUMN customer.legacy_credit_limit"));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("DROP "));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("DELETE "));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("TRUNCATE"));
    }

    @Test
    void productImageMigrationAddsOnlyAnOptionalValidatedReference() throws Exception {
        String migration = Files.readString(Path.of("database/migrations/V114__product_image_reference.sql"));
        assertTrue(migration.contains("ADD COLUMN image_filename VARCHAR(68)"));
        assertTrue(migration.contains("image_filename IS NULL OR image_filename ~"));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("DROP "));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("DELETE "));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("TRUNCATE"));
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
