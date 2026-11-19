package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.UserRepository;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

/**
 * Cambia exclusivamente rol y actividad dentro del protocolo administrativo cooperativo.
 * Repositorio, revalidador y frontera deben compartir almacenamiento: en memoria el mismo
 * repositorio monitorizado y en JDBC el mismo proveedor. El constructor no puede verificar
 * ese emparejamiento. No protege escrituras directas ni SQL externo ni integra la aplicación.
 */
public final class UserManagementService {
    private final UserRepository users;
    private final UserAdministrationBoundary boundary;
    private final AdministrativeSessionValidator sessions;
    private final ActiveAdministratorPolicy policy = new ActiveAdministratorPolicy();

    /**
     * @param users repositorio de cuentas compartido con la frontera
     * @param boundary frontera administrativa obligatoria
     * @param authorization mecanismo existente de permisos
     * @throws IllegalArgumentException si falta una dependencia
     */
    public UserManagementService(UserRepository users, UserAdministrationBoundary boundary,
                                 AuthorizationService authorization) {
        if (users == null || boundary == null || authorization == null) {
            throw new IllegalArgumentException("Repositorio, frontera y autorización son obligatorios.");
        }
        this.users = users;
        this.boundary = boundary;
        this.sessions = new AdministrativeSessionValidator(users, authorization);
    }

    /**
     * Cambia el rol después de revalidar al actor y proteger al último administrador.
     * @param actor sesión del administrador solicitante
     * @param userId ID positivo del objetivo, expresado como texto
     * @param expectedVersion versión del objetivo que conoce el llamador
     * @param role rol solicitado, no nulo
     * @return estado resultante sin credenciales; provisional en una transacción exterior
     * @throws SecurityException si el actor no está autorizado o su sesión no está vigente
     * @throws IllegalArgumentException por argumentos inválidos u objetivo inexistente
     * @throws ConcurrentModificationException si la versión del objetivo no coincide
     * @throws IllegalStateException si se intenta retirar al último administrador activo
     */
    public UserChangeResult changeRole(UserSession actor, String userId, long expectedVersion, UserRole role) {
        return boundary.execute(() -> {
            sessions.validate(actor);
            if (role == null) throw new IllegalArgumentException("El rol es obligatorio.");
            return update(userId, expectedVersion, proposed -> proposed.setRole(role));
        });
    }

    /**
     * Activa o desactiva una cuenta bajo las mismas validaciones que el cambio de rol.
     * No renueva sesiones. Un error se propaga sin reintentos ni lecturas posteriores al UPDATE.
     * En memoria no hay rollback general; se valida todo antes de la única escritura final.
     * En JDBC un fallo de commit/cierre puede dejar resultado incierto: consultar antes de repetir.
     * @param actor sesión del administrador solicitante
     * @param userId ID positivo del objetivo, expresado como texto
     * @param expectedVersion versión del objetivo que conoce el llamador
     * @param active estado solicitado
     * @return estado resultante sin credenciales; provisional en una transacción exterior
     * @throws SecurityException si el actor no está autorizado o su sesión no está vigente
     * @throws IllegalArgumentException por argumentos inválidos u objetivo inexistente
     * @throws ConcurrentModificationException si la versión del objetivo no coincide
     * @throws IllegalStateException si se intenta retirar al último administrador activo
     */
    public UserChangeResult setActive(UserSession actor, String userId, long expectedVersion, boolean active) {
        return boundary.execute(() -> {
            sessions.validate(actor);
            return update(userId, expectedVersion, proposed -> proposed.setActive(active));
        });
    }

    private UserChangeResult update(String userId, long expectedVersion, Consumer<UserAccount> change) {
        Long id;
        try {
            id = userId == null ? null : Long.valueOf(userId.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("El ID del objetivo debe ser un entero positivo.");
        }
        if (id == null || id <= 0 || expectedVersion < 0) {
            throw new IllegalArgumentException("Se requieren un ID positivo y una versión no negativa.");
        }
        UserAccount current = users.findById(id.toString())
                .orElseThrow(() -> new IllegalArgumentException("La cuenta objetivo no existe."));
        if (!id.equals(current.getId())) throw new IllegalStateException("La identidad de la cuenta objetivo es inconsistente.");
        if (current.getSecurityVersion() != expectedVersion) {
            throw new ConcurrentModificationException("La cuenta cambió; vuelva a cargarla antes de actualizar.");
        }
        UserAccount proposed = copy(current);
        change.accept(proposed);
        long administrators = users.findAll().stream()
                .filter(user -> user.isActive() && user.getRole() == UserRole.ADMINISTRATOR).count();
        policy.validateChange(current, proposed, administrators);
        users.update(proposed);
        return new UserChangeResult(proposed.getId(), proposed.getRole(), proposed.isActive(), proposed.getSecurityVersion());
    }

    private static UserAccount copy(UserAccount user) {
        UserAccount result = new UserAccount(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(), user.getSellerId());
        result.setActive(user.isActive());
        result.setCreatedAt(user.getCreatedAt());
        result.setSecurityVersion(user.getSecurityVersion());
        return result;
    }

    /** Resultado inmutable sin cuenta mutable, contraseña ni hash. */
    public record UserChangeResult(Long id, UserRole role, boolean active, long securityVersion) {}
}
