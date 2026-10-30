package com.laexclusiva.model;

import java.util.Objects;

/**
 * Representa una cuenta de usuario dentro del sistema, gestionando sus credenciales,
 * rol de acceso y estado de activaciÃ³n, asegurando la consistencia e integridad de los datos.
 * 
 * @author Daniel Arrieta
 */
public class UserAccount {
    private Long id;
    private String username;
    private String passwordHash;
    private UserRole role;
    private boolean active;

    /**
     * Constructor principal para crear una nueva cuenta de usuario.
     * 
     * @param username Nombre de usuario Ãºnico.
     * @param passwordHash Hash seguro de la contraseÃ±a.
     * @param role Rol asignado al usuario.
     */
    public UserAccount(String username, String passwordHash, UserRole role) {
        setUsername(username);
        setPasswordHash(passwordHash);
        setRole(role);
        this.active = true; // Por defecto activo al registrarse
    }

    /**
     * Establece el ID Ãºnico de la cuenta de usuario.
     * 
     * @param id Identificador numÃ©rico Ãºnico y positivo.
     */
    public void setId(Long id) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("El ID del usuario debe ser un valor positivo.");
        }
        this.id = id;
    }

    public Long getId() { return id; }

    public Long getSellerId() { return id; }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario no puede ser nulo o vacÃ­o.");
        }
        this.username = username.trim();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Actualiza el hash de la contraseÃ±a de forma segura, evitando estados vacÃ­os o nulos.
     * 
     * @param passwordHash Hash cifrado de la contraseÃ±a.
     */
    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("El hash de la contraseÃ±a no puede ser nulo o vacÃ­o.");
        }
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("El rol de usuario no puede ser nulo.");
        }
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAccount that = (UserAccount) o;
        return Objects.equals(id, that.id) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return "UserAccount{id=" + id + ", username='" + username + "', role=" + role + ", active=" + active + '}';
    }
}