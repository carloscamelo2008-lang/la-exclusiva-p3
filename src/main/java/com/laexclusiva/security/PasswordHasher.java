package com.laexclusiva.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.security.ProviderException;
import java.util.Arrays;

/**
 * Utilidad de seguridad para el cifrado y verificación de contraseñas
 * utilizando el algoritmo PBKDF2WithHmacSHA256 con parámetros estructurados.
 * 
 * @author Daniel Arrieta
 */
public class PasswordHasher {
    private static final Logger LOGGER = Logger.getLogger(PasswordHasher.class.getName());
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede ser nula o vacía.");
        }
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        
        byte[] hashBytes = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = Base64.getEncoder().encodeToString(hashBytes);
        
        return String.format("$pbkdf2-sha256$i=%d$%s$%s", ITERATIONS, saltBase64, hashBase64);
    }

    public static boolean verify(String password, String storedHash) {
        return verify(password, storedHash, PasswordHasher::derive);
    }

    // Inyección acotada para comprobar límites y fallos sin cambiar proveedores globales.
    @FunctionalInterface
    interface Derivation {
        byte[] apply(char[] password, byte[] salt, int iterations, int bits)
                throws NoSuchAlgorithmException, InvalidKeySpecException;
    }

    static boolean verify(String password, String storedHash, Derivation derivation) {
        if (password == null || storedHash == null || storedHash.length() > 255) return false;
        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 5 || !parts[0].isEmpty() || !parts[1].equals("pbkdf2-sha256")
                || !parts[2].matches("i=[1-9][0-9]{0,6}")) return false;
        int iterations = Integer.parseInt(parts[2].substring(2));
        if (iterations < 1000 || iterations > 1000000
                || parts[3].length() < 11 || parts[3].length() > 88
                || parts[4].length() < 22 || parts[4].length() > 88) return false;
        byte[] salt = null;
        byte[] expected = null;
        byte[] actual = null;
        char[] secret = null;
        try {
            try {
                salt = Base64.getDecoder().decode(parts[3]);
                expected = Base64.getDecoder().decode(parts[4]);
            } catch (IllegalArgumentException malformed) {
                return false;
            }
            if (salt.length < 8 || salt.length > 64 || expected.length < 16 || expected.length > 64) return false;
            secret = password.toCharArray();
            actual = derivation.apply(secret, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | ProviderException failure) {
            LOGGER.log(Level.WARNING, "No se pudo completar la verificación criptográfica.");
            return false;
        } finally {
            if (secret != null) Arrays.fill(secret, '\0');
            if (salt != null) Arrays.fill(salt, (byte) 0);
            if (expected != null) Arrays.fill(expected, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            return derive(password, salt, iterations, keyLength);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error crítico en el algoritmo de cifrado de contraseñas.", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
