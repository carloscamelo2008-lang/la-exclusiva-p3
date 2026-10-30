package com.laexclusiva.model;

public enum Permission {
    VIEW_CATALOG,
    REGISTER_SALE,
    REGISTER_CLIENT,
    VIEW_CLIENTS,
    VIEW_SALES,
    REGISTER_PAYMENT,
    MANAGE_STOCK,       // Restringido al Administrador
    MANAGE_USERS,       // Restringido al Administrador
    MANAGE_ORDERS,      // Futuro
    MANAGE_CASH,        // Futuro
    VIEW_REPORTS        // Futuro
}