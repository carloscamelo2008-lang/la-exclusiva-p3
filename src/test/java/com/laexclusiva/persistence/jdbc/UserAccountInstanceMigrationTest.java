package com.laexclusiva.persistence.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Inspección estática; no acredita ejecución del trigger ni backfill en PostgreSQL. */
class UserAccountInstanceMigrationTest {
    @Test
    void backfillAndConstraintsAreTransactionalAndDoNotChangeSecurityFields() throws Exception {
        String sql = Files.readString(Path.of("database/migrations/V106__user_account_instance_identity.sql")).replace("\r\n", "\n");
        assertTrue(sql.contains("BEGIN;")); assertTrue(sql.endsWith("COMMIT;\n"));
        assertTrue(sql.contains("ADD COLUMN account_instance_id UUID"));
        assertTrue(sql.contains("UPDATE public.user_account SET account_instance_id = pg_catalog.gen_random_uuid();"));
        assertTrue(sql.contains("SET NOT NULL")); assertTrue(sql.contains("UNIQUE (account_instance_id)"));
        assertFalse(sql.contains("DELETE FROM")); assertFalse(sql.contains("SET security_version"));
        assertTrue(sql.indexOf("UPDATE public.user_account") < sql.indexOf("CREATE TRIGGER"));
    }

    @Test
    void everyInsertOverridesClientIdentityAndUpdatesRejectReplacement() throws Exception {
        String sql = Files.readString(Path.of("database/migrations/V106__user_account_instance_identity.sql")).replace("\r\n", "\n");
        assertTrue(sql.contains("IF TG_OP = 'INSERT' THEN"));
        assertTrue(sql.contains("NEW.account_instance_id := pg_catalog.gen_random_uuid();"));
        assertTrue(sql.contains("NEW.account_instance_id IS DISTINCT FROM OLD.account_instance_id"));
        assertTrue(sql.contains("ERRCODE = '23514'"));
        assertTrue(sql.contains("BEFORE INSERT OR UPDATE ON public.user_account\nFOR EACH ROW"));
        assertTrue(sql.contains("SET search_path = pg_catalog")); assertTrue(sql.contains("SECURITY INVOKER"));
        assertFalse(sql.contains("UPDATE OF")); assertFalse(sql.contains("WHEN ("));
    }
}
