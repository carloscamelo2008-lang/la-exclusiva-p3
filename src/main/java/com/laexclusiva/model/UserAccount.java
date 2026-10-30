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

    public UserAccount() {
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public UserAccount(Long id, String username, String passwordHash, UserRole role, String sellerId) {
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
}
