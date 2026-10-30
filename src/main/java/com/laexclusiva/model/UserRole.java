package com.laexclusiva.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Enum que define los roles de usuario y sus permisos asociados.
 * 
 * @author Daniel Arrieta
 */
public enum UserRole {
    ADMINISTRATOR(EnumSet.allOf(Permission.class)),
    WORKER(EnumSet.of(
        Permission.VIEW_CATALOG,
        Permission.VIEW_INVENTORY,
        Permission.REGISTER_SALE,
        Permission.REGISTER_CUSTOMER,
        Permission.VIEW_CUSTOMER,
        Permission.VIEW_SALES,
        Permission.VIEW_PORTFOLIO,
        Permission.REGISTER_PAYMENT
    ));

    private final Set<Permission> permissions;

    UserRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
