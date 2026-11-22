package com.laexclusiva.persistence.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Revisión estática conservadora de V105; no ejecuta SQL ni valida PostgreSQL. */
class ActiveUserSellerMigrationTest {
    @Test
    void migrationContainsOnlyTheApprovedTransactionalPartialUniqueIndex() throws Exception {
        String sql = Files.readString(Path.of("database/migrations/V105__unique_active_user_seller.sql"))
                .replaceAll("\\s+", " ").trim();
        assertEquals("BEGIN; CREATE UNIQUE INDEX uq_user_account_active_seller ON public.user_account (seller_id) "
                + "WHERE active = TRUE AND seller_id IS NOT NULL; COMMIT;", sql);
    }
}
