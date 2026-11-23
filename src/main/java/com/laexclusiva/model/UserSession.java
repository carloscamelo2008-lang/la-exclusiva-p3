package com.laexclusiva.model;

import java.time.LocalDateTime;

/**
 * Representa la sesión activa de un usuario en el sistema.
 * 
 * @author Daniel Arrieta
 */
public class UserSession {

    private final UserAccount userAccount;
    private final LocalDateTime loginTime;
    private final Long authenticatedUserId;
    private final long authenticatedSecurityVersion;
    private final java.util.UUID authenticatedAccountInstanceId;
    private volatile boolean valid = true;

    /**
     * Captura identidad y versión sin autenticar ni consultar el repositorio.
     * Conserva la cuenta mutable por compatibilidad; sus cambios no renuevan la captura.
     * El llamador no debe modificar simultáneamente la cuenta durante la construcción.
     * @param userAccount cuenta con identificador persistido positivo y versión no negativa
     * @throws IllegalArgumentException por cuenta nula, ID inválido o versión negativa
     */
    public UserSession(UserAccount userAccount) {
        if (userAccount == null) {
            throw new IllegalArgumentException("La cuenta de usuario no puede ser nula.");
        }
        Long userId = userAccount.getId();
        long securityVersion = userAccount.getSecurityVersion();
        if (userId == null || userId <= 0 || securityVersion < 0) {
            throw new IllegalArgumentException("La sesión requiere un ID positivo y una versión de seguridad no negativa.");
        }
        this.authenticatedUserId = userId;
        this.authenticatedSecurityVersion = securityVersion;
        this.authenticatedAccountInstanceId = userAccount.getAccountInstanceId();
        this.userAccount = userAccount;
        this.loginTime = LocalDateTime.now();
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    /** @return ID capturado al construir la sesión, independiente de la cuenta mutable */
    public Long getAuthenticatedUserId() {
        return authenticatedUserId;
    }

    /**
     * @return versión capturada al construir la sesión; no se renueva ni valida su vigencia persistida
     */
    public long getAuthenticatedSecurityVersion() {
        return authenticatedSecurityVersion;
    }

    /** @return UUID capturado; las sesiones sintéticas anteriores pueden carecer de él */
    public java.util.UUID getAuthenticatedAccountInstanceId() { return authenticatedAccountInstanceId; }

    /** Indica únicamente si la sesión no ha sido invalidada; no valida la cuenta persistida. */
    public boolean isValid() {
        return valid;
    }

    /** Indica si la sesión sigue vigente y su cuenta está activa. */
    public boolean isActive() {
        return valid && userAccount.isActive();
    }

    /** Invalida esta sesión sin desactivar la cuenta ni afectar otras sesiones. */
    public void invalidate() {
        valid = false;
    }
}
