package com.laexclusiva.model;

import java.util.Objects;

public class UserAccount {
    private String id;
    private String username;
    private String passwordHash; // Hash con sal (ej. PBKDF2)
    private UserRole role;
    private boolean active;
    private String sellerId; // Relación opcional con el perfil de vendedor

    public UserAccount(String id, String username, String passwordHash, UserRole role, boolean active, String sellerId) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario no puede estar vacío.");
        }
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("El hash de la contraseña es obligatorio.");
        }
        if (role == null) {
            throw new IllegalArgumentException("El rol del usuario es obligatorio.");
        }
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.sellerId = sellerId;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public boolean isActive() { return active; }
    public String getSellerId() { return sellerId; }

    public void setActive(boolean active) { this.active = active; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAccount that = (UserAccount) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
}