package com.laexclusiva.service;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;

/**
 * Servicio encargado de la autorización y validación de permisos de usuario o sesión.
 * 
 * @author Daniel Arrieta
 */
public class AuthorizationService {

    public boolean isAdministrator(UserAccount user) {
        return user != null && user.isActive() && user.getRole() == UserRole.ADMINISTRATOR;
    }

    public boolean isAdministrator(UserSession session) {
        return session != null && isAdministrator(session.getUserAccount());
    }

    public boolean hasRole(UserAccount user, UserRole requiredRole) {
        return user != null && user.isActive() && requiredRole != null && user.getRole() == requiredRole;
    }

    public boolean hasRole(UserSession session, UserRole requiredRole) {
        return session != null && hasRole(session.getUserAccount(), requiredRole);
    }

    public boolean hasPermission(UserAccount user, Permission permission) {
        return user != null && user.isActive() && permission != null && user.getRole() != null && user.getRole().hasPermission(permission);
    }

    public boolean hasPermission(UserSession session, Permission permission) {
        return session != null && hasPermission(session.getUserAccount(), permission);
    }
}
