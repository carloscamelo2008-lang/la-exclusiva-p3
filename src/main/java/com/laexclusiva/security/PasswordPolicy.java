package com.laexclusiva.security;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Política para secretos nuevos; no transforma el texto utilizado por el hasher. */
public final class PasswordPolicy {
    private PasswordPolicy() { }

    private static final class Blocklist {
        private static final Set<String> WORDS = loadBlocklist(PasswordPolicy.class.getResourceAsStream(
                "/com/laexclusiva/security/password-blocklist.txt"));
    }

    /**
     * Valida entre 15 y 128 puntos de código y la lista local de bloqueo.
     * @param password secreto original, sin recortar ni normalizar
     * @throws IllegalArgumentException si no cumple la política
     * @throws IllegalStateException si la lista no está disponible
     */
    public static void validate(String password) {
        if (password == null || password.length() > 256 || !wellFormed(password)) {
            throw new IllegalArgumentException("La contraseña debe contener entre 15 y 128 caracteres Unicode válidos.");
        }
        int size = password.codePointCount(0, password.length());
        if (size < 15 || size > 128) {
            throw new IllegalArgumentException("La contraseña debe contener entre 15 y 128 caracteres Unicode válidos.");
        }
        Set<String> blocked;
        try {
            blocked = Blocklist.WORDS;
        } catch (ExceptionInInitializerError | NoClassDefFoundError failure) {
            throw new IllegalStateException("La política de contraseñas no está disponible.");
        }
        if (blocked.contains(key(password))) {
            throw new IllegalArgumentException("Elija una contraseña menos común o previsible.");
        }
    }

    // Punto de prueba del cargador, sin exponer una API pública adicional.
    static Set<String> loadBlocklist(InputStream input) {
        if (input == null) throw new IllegalStateException("La lista de bloqueo no está disponible.");
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            Set<String> words = new HashSet<>();
            String line;
            int lines = 0;
            while ((line = readBoundedLine(reader)) != null) {
                if (++lines > 10000 || line.length() > 256 || !wellFormed(line)) {
                    throw new IllegalStateException("La lista de bloqueo es inválida.");
                }
                if (line.startsWith("#")) continue;
                if (line.isEmpty()) throw new IllegalStateException("La lista de bloqueo es inválida.");
                words.add(key(line));
            }
            if (words.isEmpty()) throw new IllegalStateException("La lista de bloqueo está vacía.");
            return Set.copyOf(words);
        } catch (Exception failure) {
            throw new IllegalStateException("No se pudo cargar la lista de bloqueo.");
        }
    }

    private static String readBoundedLine(BufferedReader reader) throws java.io.IOException {
        StringBuilder line = new StringBuilder(256);
        int unit;
        while ((unit = reader.read()) != -1) {
            if (unit == '\n') return line.toString();
            if (unit == '\r') {
                reader.mark(1);
                if (reader.read() != '\n') reader.reset();
                return line.toString();
            }
            if (line.length() == 256) throw new IllegalStateException("La lista de bloqueo es inválida.");
            line.append((char) unit);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static String key(String value) {
        return Normalizer.normalize(Normalizer.normalize(value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    private static boolean wellFormed(String value) {
        for (int i = 0; i < value.length(); i++) {
            char unit = value.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(unit)) return false;
        }
        return true;
    }
}
