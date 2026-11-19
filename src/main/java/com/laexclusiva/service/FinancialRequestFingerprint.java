package com.laexclusiva.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huellas estables para detectar que una clave financiera se reutiliza con otra solicitud. */
final class FinancialRequestFingerprint {
    private FinancialRequestFingerprint() { }

    static String calculate(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (String value : values) {
            String normalized = value == null ? "" : value;
            canonical.append(normalized.length()).append(':').append(normalized);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("La plataforma no ofrece SHA-256.", impossible);
        }
    }
}
