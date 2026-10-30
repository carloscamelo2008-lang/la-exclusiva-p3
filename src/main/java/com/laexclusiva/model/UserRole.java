package com.laexclusiva.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Enum que define los roles de usuario y sus permisos asociados.
 * 
 * @author Daniel Arrieta
 */
public enum UserRole {
    ADMINISTRATOR(EnumSet.allOf(Permission.class)),
    WORKER(EnumSet.of(Permission.MANAGE_CUSTOMERS, Permission.PROCESS_SALES));

    private final Set<Permission> permissions;

    UserRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
