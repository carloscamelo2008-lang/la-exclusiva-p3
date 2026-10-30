package com.laexclusiva.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        if (password == null || storedHash == null) {
            return false;
        }
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 5 || !parts[1].equals("pbkdf2-sha256")) {
                return false;
            }
            
            int iterations = Integer.parseInt(parts[2].replace("i=", ""));
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[4]);
            
            byte[] actualHash = pbkdf2(password.toCharArray(), salt, iterations, expectedHash.length * 8);
            
            return slowEquals(expectedHash, actualHash);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al verificar la contraseña debido a un formato malformado.", e);
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error crítico en el algoritmo de cifrado de contraseñas.", e);
        }
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
