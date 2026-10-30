package com.laexclusiva.service;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;

public class AuthorizationService {

    public void requirePermission(UserSession session, Permission permission) {
        if (session == null) {
            throw new SecurityException("Acceso denegado: No hay una sesión activa.");
        }

        if (!hasPermission(session.getRole(), permission)) {
            throw new SecurityException("Acceso denegado: El usuario no cuenta con el permiso requerido para esta operación.");
        }
    }

    public boolean hasPermission(UserRole role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }

        // El ADMINISTRATOR tiene acceso a todo lo actual
        if (role == UserRole.ADMINISTRATOR) {
            return true;
        }

        // Permisos específicos para el WORKER (Trabajador)
        if (role == UserRole.WORKER) {
            switch (permission) {
                case VIEW_CATALOG:
                case REGISTER_SALE:
                case REGISTER_CLIENT:
                case VIEW_CLIENTS:
                case VIEW_SALES:
                case REGISTER_PAYMENT:
                    return true;
                case MANAGE_STOCK:
                case MANAGE_USERS:
                case MANAGE_ORDERS:
                case MANAGE_CASH:
                case VIEW_REPORTS:
                default:
                    return false;
            }
        }

        return false;
    }
}