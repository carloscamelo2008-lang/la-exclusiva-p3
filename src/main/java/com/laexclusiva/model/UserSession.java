package com.laexclusiva.model;

public class UserSession {
    private final String userId;
    private final String username;
    private final UserRole role;
    private final String sellerId;

    public UserSession(String userId, String username, UserRole role, String sellerId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID de usuario es obligatorio para la sesión.");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario es obligatorio para la sesión.");
        }
        if (role == null) {
            throw new IllegalArgumentException("El rol es obligatorio para la sesión.");
        }
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.sellerId = sellerId;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
    public String getSellerId() { return sellerId; }
}