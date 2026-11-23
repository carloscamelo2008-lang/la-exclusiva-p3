package com.laexclusiva.security;

import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Verificación real acotada y fallos simulados sin modificar proveedores globales. */
class PasswordHasherTest {
    private static String encoded(String iterations, int salt, int key) {
        return "$pbkdf2-sha256$" + iterations + "$" + Base64.getEncoder().encodeToString(new byte[salt])
                + "$" + Base64.getEncoder().encodeToString(new byte[key]);
    }

    @Test
    void currentHashAndIncorrectPassword() {
        String hash = PasswordHasher.hash("Frase de prueba actual");
        assertTrue(hash.startsWith("$pbkdf2-sha256$i=210000$"));
        assertTrue(PasswordHasher.verify("Frase de prueba actual", hash));
        assertFalse(PasswordHasher.verify("otra frase", hash));
    }

    @Test
    void nullArgumentsRemainFalse() {
        assertFalse(PasswordHasher.verify(null, encoded("i=1000", 8, 16)));
        assertFalse(PasswordHasher.verify("test", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"historical", "  espacios iniciales", "espacios finales  ", "               ", "n\u0303 y 😀 sin cambios"})
    void historicalFixtureVerifiesExactTextWithAndWithoutPadding(String secret) throws Exception {
        byte[] salt = new byte[8];
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, 1000, 128);
        byte[] result;
        try { result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
        String hash = "$pbkdf2-sha256$i=1000$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(result);
        assertTrue(PasswordHasher.verify(secret, hash));
        String unpadded = "$pbkdf2-sha256$i=1000$" + Base64.getEncoder().withoutPadding().encodeToString(salt)
                + "$" + Base64.getEncoder().withoutPadding().encodeToString(result);
        assertTrue(PasswordHasher.verify(secret, unpadded));
        assertFalse(PasswordHasher.verify(secret + "x", hash));
    }

    @ParameterizedTest
    @ValueSource(strings = {"i=999", "i=1000001", "i=2147483648", "i=0", "i=-1000", "i=+1000", "i=01000", "i= 1000", "i=1000 ", "i=i=1000", "1000", "i=1x00", ""})
    void rejectsInvalidIterationsBeforeDerivation(String iterations) {
        assertFalse(PasswordHasher.verify("private", encoded(iterations, 8, 16),
                (p, s, i, b) -> { fail("No debe derivar"); return null; }));
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 210000, 1000000})
    void acceptsIterationBoundariesWithoutCostlyDerivation(int iterations) {
        int[] calls = {0};
        assertTrue(PasswordHasher.verify("private", encoded("i=" + iterations, 8, 16), (p, s, i, b) -> {
            calls[0]++; assertEquals(iterations, i); assertEquals(128, b); return new byte[16];
        }));
        assertEquals(1, calls[0]);
    }

    @Test
    void malformedFieldsAndBase64NeverReachDerivation() {
        String valid = encoded("i=1000", 8, 16);
        for (String invalid : List.of("", valid + "$", "prefix" + valid, valid.substring(1),
                valid.replace("pbkdf2-sha256", "PBKDF2-SHA256"), "$pbkdf2-sha256$i=1000$$",
                "$pbkdf2-sha256$i=1000$!!!!!!!!!!!!$!!!!!!!!!!!!!!!!!!!!!!!!",
                valid.substring(0, valid.lastIndexOf('$')), "x".repeat(256))) {
            assertFalse(PasswordHasher.verify("private", invalid,
                    (p, s, i, b) -> { fail("No debe derivar"); return null; }));
        }
    }

    @Test
    void decodedSizeBoundariesAreCheckedBeforeDerivation() {
        for (int salt : new int[]{7, 8, 64, 65}) {
            for (int key : new int[]{15, 16, 64, 65}) {
                boolean valid = salt >= 8 && salt <= 64 && key >= 16 && key <= 64;
                int[] calls = {0};
                assertEquals(valid, PasswordHasher.verify("private", encoded("i=1000", salt, key), (p,s,i,b) -> {
                    calls[0]++; assertEquals(salt, s.length); assertEquals(key * 8, b); return new byte[key];
                }));
                assertEquals(valid ? 1 : 0, calls[0]);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void accessibleTemporaryArraysAreClearedAfterSuccessAndRecoverableFailure(boolean failure) {
        char[][] chars = {null}; byte[][] salt = {null}; byte[][] actual = {null};
        byte[] saltFixture = new byte[8]; java.util.Arrays.fill(saltFixture, (byte) 5);
        byte[] keyFixture = new byte[16]; java.util.Arrays.fill(keyFixture, (byte) 9);
        String hash = "$pbkdf2-sha256$i=1000$" + Base64.getEncoder().encodeToString(saltFixture)
                + "$" + Base64.getEncoder().encodeToString(keyFixture);
        int[] calls = {0};
        assertEquals(!failure, PasswordHasher.verify("private", hash, (p,s,i,b) -> {
            calls[0]++;
            chars[0] = p; salt[0] = s;
            assertArrayEquals("private".toCharArray(), p);
            assertArrayEquals(saltFixture, s);
            if (failure) throw new java.security.spec.InvalidKeySpecException("Fallo ficticio");
            actual[0] = keyFixture.clone();
            assertEquals(9, actual[0][0]);
            return actual[0];
        }));
        assertEquals(1, calls[0]);
        assertArrayEquals(new char[7], chars[0]); assertArrayEquals(new byte[8], salt[0]);
        if (failure) assertNull(actual[0], "La derivación fallida no devuelve un resultado temporal.");
        else assertArrayEquals(new byte[16], actual[0]);
        assertEquals(5, saltFixture[0], "No se deben confundir los fixtures con los arreglos internos.");
        assertEquals(9, keyFixture[0]);
    }

    @Test
    void recoverableErrorsHaveOnlyFixedDiagnosticsAndProgrammingErrorsPropagate() {
        Logger logger = Logger.getLogger(PasswordHasher.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            public void publish(LogRecord record) { records.add(record); }
            public void flush() { }
            public void close() { }
        };
        boolean parents = logger.getUseParentHandlers(); Level level = logger.getLevel();
        logger.addHandler(handler); logger.setUseParentHandlers(false); logger.setLevel(Level.ALL);
        try {
            String hash = encoded("i=1000", 8, 16);
            for (Exception failure : List.of(new java.security.NoSuchAlgorithmException("private hash salt"),
                    new java.security.spec.InvalidKeySpecException("private hash salt"),
                    new java.security.ProviderException("private hash salt"))) {
                assertFalse(PasswordHasher.verify("private", hash, (p,s,i,b) -> {
                    if (failure instanceof java.security.NoSuchAlgorithmException e) throw e;
                    if (failure instanceof java.security.spec.InvalidKeySpecException e) throw e;
                    throw (java.security.ProviderException) failure;
                }));
            }
            assertEquals(3, records.size());
            for (LogRecord record : records) {
                assertEquals("No se pudo completar la verificación criptográfica.", record.getMessage());
                assertNull(record.getThrown()); assertNull(record.getParameters());
            }
            records.clear();
            assertFalse(PasswordHasher.verify("private", "malformed"));
            assertFalse(PasswordHasher.verify("private", hash));
            assertTrue(records.isEmpty());
            assertThrows(IllegalStateException.class, () -> PasswordHasher.verify("private", hash,
                    (p,s,i,b) -> { throw new IllegalStateException(); }));
            assertThrows(AssertionError.class, () -> PasswordHasher.verify("private", hash,
                    (p,s,i,b) -> { throw new AssertionError(); }));
        } finally {
            logger.removeHandler(handler); logger.setUseParentHandlers(parents); logger.setLevel(level);
        }
    }
}
