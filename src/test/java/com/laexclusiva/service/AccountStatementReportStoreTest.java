package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comprueba el guardado local de estados de cuenta sin reemplazo de archivos. */
class AccountStatementReportStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void configurableFolderCreatesUniqueReportsAndKeepsPriorBytes() throws Exception {
        Path configuredDirectory = temporaryDirectory.resolve("reportes");
        var store = AccountStatementReportStore.fromEnvironment(Map.of(
                AccountStatementReportStore.DIRECTORY_ENV, configuredDirectory.toString()));
        byte[] original = "%PDF-1.7\nfixture de cuenta\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        Path first = store.save("CLI/Cliente 1", LocalDate.of(2026, 11, 28), original);
        Path second = store.save("CLI/Cliente 1", LocalDate.of(2026, 11, 28),
                "%PDF-1.7\nsegundo estado\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertEquals(configuredDirectory.toAbsolutePath().normalize(), first.getParent());
        assertTrue(first.getFileName().toString().startsWith("estado-cuenta-CLI_Cliente_1-"));
        assertFalse(first.equals(second));
        assertEquals("%PDF-1.7\nfixture de cuenta\n",
                Files.readString(first, java.nio.charset.StandardCharsets.US_ASCII));
        try (var files = Files.list(configuredDirectory)) {
            assertEquals(2, files.filter(path -> path.toString().endsWith(".pdf")).count());
        }
        try (var files = Files.list(configuredDirectory)) {
            assertEquals(0, files.filter(path -> path.toString().endsWith(".tmp")).count());
        }
    }

    @Test
    void rejectsMissingOrNonPdfContentBeforeCreatingAnything() {
        var store = new AccountStatementReportStore(temporaryDirectory.resolve("unused"));

        assertThrows(IllegalArgumentException.class,
                () -> store.save("CLI-1", LocalDate.now(), new byte[]{1, 2, 3}));
        assertFalse(Files.exists(temporaryDirectory.resolve("unused")));
    }
}
