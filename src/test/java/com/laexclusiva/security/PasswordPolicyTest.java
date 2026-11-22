package com.laexclusiva.security;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import static org.junit.jupiter.api.Assertions.*;

/** Política local sin bases de datos; los textos usados son fixtures ficticios. */
class PasswordPolicyTest {
    @ParameterizedTest
    @ValueSource(ints = {15, 128})
    void acceptsBoundaryLengths(int size) {
        assertDoesNotThrow(() -> PasswordPolicy.validate("é".repeat(size)));
    }

    @ParameterizedTest
    @ValueSource(ints = {14, 129, 10000})
    void rejectsInvalidLengths(int size) {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("é".repeat(size)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"\uD800abcdefghijklmnop", "abcdefghijklmnop\uDC00", "\uD800"})
    void rejectsMissingOrMalformedSecrets(String value) {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"una frase larga y tranquila", "  texto original sin recortar  ", "sololaminusculalarga", "密码"})
    void acceptsPhrasesAndUnicodeWithoutCompositionRules(String value) {
        String secret = value.equals("密码") ? value.repeat(8) : value;
        assertDoesNotThrow(() -> PasswordPolicy.validate(secret));
    }

    @Test
    void countsSupplementaryCharactersAsOneCodePoint() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("😀".repeat(128)));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("😀".repeat(14)));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("😀".repeat(129)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordpassword", "PASSWORDPASSWORD", "contraseña123456", "contrasen\u0303a123456", "laexclusiva2026!", "123456789012345"})
    void rejectsFullBlockedValuesUsingComparisonNormalization(String secret) {
        var failure = assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(secret));
        assertFalse(failure.getMessage().contains(secret));
        assertNull(failure.getCause());
    }

    @Test
    void originalSecretRemainsExactForHashing() {
        String secret = "  frase con n\u0303 original  ";
        PasswordPolicy.validate(secret);
        String hash = PasswordHasher.hash(secret);
        assertTrue(PasswordHasher.verify(secret, hash));
        assertFalse(PasswordHasher.verify(secret.trim(), hash));
        assertFalse(PasswordHasher.verify(java.text.Normalizer.normalize(secret, java.text.Normalizer.Form.NFC), hash));
    }

    @Test
    void loaderRejectsMissingEmptyInvalidAndUnreadableResourcesSafely() {
        for (InputStream input : new InputStream[]{null, bytes(""), bytes("# comentario\n"),
                bytes("\n"), new ByteArrayInputStream(new byte[]{(byte) 0xC3}),
                new InputStream() { public int read() throws IOException { throw new IOException("detalle privado"); } }}) {
            var failure = assertThrows(IllegalStateException.class, () -> PasswordPolicy.loadBlocklist(input));
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("detalle privado"));
        }
    }

    @Test
    void loaderReturnsImmutableNormalizedSet() {
        var values = PasswordPolicy.loadBlocklist(bytes("CONTRASEN\u0303A123456\n"));
        assertTrue(values.contains("contraseña123456"));
        assertThrows(UnsupportedOperationException.class, () -> values.add("another"));
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty", "invalid"})
    void validateFailsClosedOnFirstAndLaterCallsWithBrokenResource(String mode) throws Exception {
        var location = PasswordPolicy.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{location}, ClassLoader.getPlatformClassLoader()) {
            @Override public InputStream getResourceAsStream(String name) {
                if (name.equals("com/laexclusiva/security/password-blocklist.txt")) {
                    return switch (mode) {
                        case "missing" -> null;
                        case "empty" -> bytes("");
                        default -> new ByteArrayInputStream(new byte[]{(byte) 0xC3});
                    };
                }
                return super.getResourceAsStream(name);
            }
        }) {
            var type = Class.forName("com.laexclusiva.security.PasswordPolicy", true, loader);
            assertNotSame(PasswordPolicy.class, type);
            var validate = type.getMethod("validate", String.class);
            for (int attempt = 0; attempt < 2; attempt++) {
                var invocation = assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> validate.invoke(null, "Una frase privada de prueba"));
                assertInstanceOf(IllegalStateException.class, invocation.getCause());
                assertEquals("La política de contraseñas no está disponible.", invocation.getCause().getMessage());
                assertNull(invocation.getCause().getCause());
                assertEquals(0, invocation.getCause().getSuppressed().length);
            }
        }
    }

    @Test
    void rejectsOversizedLineBeforeReadingTheEntireResource() {
        int[] consumed = {0};
        InputStream oversized = new InputStream() {
            @Override public int read() {
                if (++consumed[0] > 20000) throw new AssertionError("Se leyó demasiado recurso");
                return 'x';
            }
        };
        var failure = assertThrows(IllegalStateException.class, () -> PasswordPolicy.loadBlocklist(oversized));
        assertNull(failure.getCause());
        assertTrue(consumed[0] < 20000);
    }

    @Test
    void acceptsBoundedLinesWithWindowsAndUnixEndings() {
        assertEquals(java.util.Set.of("uno", "dos", "tres"),
                PasswordPolicy.loadBlocklist(bytes("UNO\r\nDOS\rTRES\n")));
    }
}
