package com.laexclusiva.model;

import java.time.LocalDateTime;

/**
 * Representa una cuenta de usuario dentro del sistema.
 * 
 * @author Daniel Arrieta
 */
public class UserAccount {

    private Long id;
    private String username;
    private String passwordHash;
    private UserRole role;
    private boolean active;
    private String sellerId;
    private LocalDateTime createdAt;
    private long securityVersion;

    public UserAccount() {
        this.securityVersion = 0;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public UserAccount(Long id, String username, String passwordHash, UserRole role, String sellerId) {
        this.securityVersion = 0;
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.sellerId = sellerId;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** @return versión de seguridad del snapshot de la cuenta, no un permiso de acceso */
    public long getSecurityVersion() {
        return securityVersion;
    }

    /**
     * Asigna el metadato recuperado o confirmado por la persistencia, sin incrementarlo.
     * No debe enlazarse a campos editables de formularios ni utilizarse como autorización.
     * @param securityVersion versión no negativa de la cuenta
     * @throws IllegalArgumentException si la versión es negativa
     */
    public void setSecurityVersion(long securityVersion) {
        if (securityVersion < 0) {
            throw new IllegalArgumentException("La versión de seguridad no puede ser negativa.");
        }
        this.securityVersion = securityVersion;
    }
}
