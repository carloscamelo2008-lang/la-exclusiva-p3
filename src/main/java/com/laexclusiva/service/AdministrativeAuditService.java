package com.laexclusiva.service;

import com.laexclusiva.model.AdministrativeAuditEvent;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.AdministrativeAuditRepository;
import java.time.LocalDate;
import java.util.List;

/** Consulta auditoría únicamente después de validar el permiso administrativo vigente. */
public final class AdministrativeAuditService {
    private final AdministrativeAuditRepository repository;
    private final AuthorizationService authorization;

    /** @param repository almacenamiento compartido de eventos
     *  @param authorization autoridad vinculada a la sesión actual
     */
    public AdministrativeAuditService(AdministrativeAuditRepository repository, AuthorizationService authorization) {
        if (repository == null || authorization == null) throw new IllegalArgumentException("Las dependencias son obligatorias.");
        authorization.requireSessionAuthority();
        this.repository = repository;
        this.authorization = authorization;
    }

    /** Consulta por rango después de revalidar rol, cuenta y sesión revocable. */
    public List<AdministrativeAuditEvent> list(UserSession actor, LocalDate from, LocalDate through, int limit) {
        return authorization.execute(actor, Permission.VIEW_ADMIN_AUDIT,
                () -> repository.find(from, through, limit));
    }
}
