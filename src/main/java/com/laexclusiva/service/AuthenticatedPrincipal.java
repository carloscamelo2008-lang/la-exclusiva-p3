package com.laexclusiva.service;

import com.laexclusiva.model.UserRole;

/** Datos vigentes e inmutables; no incluye cuenta mutable ni credenciales. */
public record AuthenticatedPrincipal(Long userId, UserRole role, String sellerId, long securityVersion) { }
