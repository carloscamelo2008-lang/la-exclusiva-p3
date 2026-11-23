package com.laexclusiva.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** Preferencias actuales de contacto y compromiso; se almacenan separadas del cliente histórico. */
public record CollectionProfile(String customerId, String email, CollectionChannel authorizedChannel,
                                boolean collectionConsent, LocalDateTime consentRecordedAt,
                                boolean remindersEnabled, LocalDate commitmentDate,
                                BigDecimal observedBalance, LocalDateTime observedAt) {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Valida coherencia del consentimiento sin modificar los datos personales existentes. */
    public CollectionProfile {
        if (customerId == null || customerId.isBlank() || authorizedChannel == null) {
            throw new IllegalArgumentException("El cliente y el canal de contacto son obligatorios.");
        }
        if (email != null && (email.isBlank() || email.length() > 254 || !EMAIL.matcher(email.trim()).matches())) {
            throw new IllegalArgumentException("El correo electrónico no tiene un formato válido.");
        }
        if (authorizedChannel == CollectionChannel.EMAIL && (email == null || email.isBlank())) {
            throw new IllegalArgumentException("Se requiere un correo para autorizar el canal email.");
        }
        if (collectionConsent != (consentRecordedAt != null)
                || collectionConsent == (authorizedChannel == CollectionChannel.NONE)
                || (!collectionConsent && remindersEnabled)) {
            throw new IllegalArgumentException("El canal, la autorización y su fecha deben ser coherentes.");
        }
        if (observedBalance == null != (observedAt == null)
                || (observedBalance != null && observedBalance.signum() < 0)) {
            throw new IllegalArgumentException("La observación de saldo debe incluir importe y fecha coherentes.");
        }
        email = email == null ? null : email.trim();
    }

    /** Perfil compatible para un cliente existente que todavía no autorizó comunicaciones. */
    public static CollectionProfile empty(String customerId) {
        return new CollectionProfile(customerId, null, CollectionChannel.NONE, false,
                null, false, null, null, null);
    }
}
