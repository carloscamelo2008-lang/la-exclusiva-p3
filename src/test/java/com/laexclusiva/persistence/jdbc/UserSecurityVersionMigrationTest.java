package com.laexclusiva.persistence.jdbc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Controles estáticos del alcance de V104; no ejecutan ni validan SQL en PostgreSQL. */
class UserSecurityVersionMigrationTest {
    private String migration() throws IOException {
        return Files.readString(Path.of("database/migrations/V104__user_security_version.sql"));
    }

    @Test
    void schemaChangesTargetOnlyTheExistingUserTableWithoutDestructiveCommands() throws IOException {
        String sql = migration();
        var targets = Pattern.compile("(?im)^\\s*ALTER\\s+TABLE\\s+([\\w.]+)").matcher(sql);
        assertEquals(List.of("public.user_account"),
                targets.results().map(result -> result.group(1)).toList());
        assertFalse(Pattern.compile("(?im)^\\s*(DROP|TRUNCATE|INSERT|UPDATE|DELETE|GRANT|REVOKE)\\b")
                .matcher(sql).find(), "V104 no debe introducir limpieza, DML ni cambios de permisos.");
        assertFalse(Pattern.compile("(?im)^\\s*CREATE\\s+(TABLE|DATABASE|SEQUENCE)\\b")
                .matcher(sql).find(), "V104 no debe crear almacenamiento ajeno al versionado.");
    }

    @Test
    void triggerCannotSkipAnUpdateThatOnlyChangesTheVersionColumn() throws IOException {
        String sql = migration();
        assertTrue(Pattern.compile("(?is)CREATE\\s+TRIGGER\\s+\\w+\\s+BEFORE\\s+INSERT\\s+OR\\s+UPDATE"
                + "\\s+ON\\s+public\\.user_account\\s+FOR\\s+EACH\\s+ROW")
                .matcher(sql).find(), "El trigger debe cubrir todos los UPDATE, no solo UPDATE OF campos de seguridad.");
    }

    @Test
    void triggerFunctionPinsTheTrustedSearchPathWithoutElevatingPrivileges() throws IOException {
        String sql = migration();
        assertTrue(Pattern.compile("(?im)^SET\\s+search_path\\s*=\\s*pg_catalog\\s*$").matcher(sql).find());
        assertTrue(Pattern.compile("(?im)^SECURITY\\s+INVOKER\\s*$").matcher(sql).find());
        assertFalse(Pattern.compile("(?i)SECURITY\\s+DEFINER").matcher(sql).find());
    }
}
