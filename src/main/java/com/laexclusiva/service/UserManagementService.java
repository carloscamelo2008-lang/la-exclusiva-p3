package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.security.PasswordHasher;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

/**
 * Crea cuentas y cambia rol, actividad y contraseña dentro del protocolo administrativo cooperativo.
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

    /**
     * Crea una cuenta sin vendedor, con ID asignado por el repositorio y versión inicial cero.
     * Revalida antes de procesar credenciales. PBKDF2 se ejecuta dentro de la frontera y puede
     * prolongar el bloqueo; no se promete borrar de memoria las contraseñas almacenadas en String.
     * No comprueba duplicados previamente, no relee tras guardar ni reintenta resultados inciertos.
     * @param actor sesión administrativa vigente; no permite bootstrap sin administrador
     * @param request datos de creación, sin metadatos persistentes controlados por el cliente
     * @return resultado sin credenciales; provisional si participa en una transacción exterior
     * @throws SecurityException si el actor no está autorizado
     * @throws IllegalArgumentException por datos inválidos o duplicados informados por el repositorio
     */
    public UserChangeResult createUser(UserSession actor, CreateUserRequest request) {
        return boundary.execute(() -> {
            sessions.validate(actor);
            if (request == null) throw new IllegalArgumentException("Los datos de creación son obligatorios.");
            String username = request.username();
            if (username == null || username.isBlank() || username.length() > 100 || username.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("El nombre de usuario debe tener entre 1 y 100 caracteres válidos.");
            }
            if (request.password() == null || request.password().isEmpty()) {
                throw new IllegalArgumentException("La contraseña no puede ser nula o vacía.");
            }
            if (request.role() == null) throw new IllegalArgumentException("El rol es obligatorio.");
            UserAccount user = new UserAccount(null, username, PasswordHasher.hash(request.password()), request.role(), null);
            user.setActive(request.active());
            users.save(user);
            return new UserChangeResult(user.getId(), user.getRole(), user.isActive(), user.getSecurityVersion());
        });
    }

    private UserChangeResult update(String userId, long expectedVersion, Consumer<UserAccount> change) {
        UserAccount current = loadExpected(userId, expectedVersion);
        UserAccount proposed = copy(current);
        change.accept(proposed);
        long administrators = users.findAll().stream()
                .filter(user -> user.isActive() && user.getRole() == UserRole.ADMINISTRATOR).count();
        policy.validateChange(current, proposed, administrators);
        users.update(proposed);
        return new UserChangeResult(proposed.getId(), proposed.getRole(), proposed.isActive(), proposed.getSecurityVersion());
    }

    /**
     * Restablece administrativamente una contraseña, incluida la propia, sin pedir la anterior.
     * Revalida antes de procesar la contraseña y conserva los campos ajenos en una copia.
     * Acepta el mismo texto: el salt aleatorio normalmente cambia el hash y la versión.
     * PBKDF2 prolonga la frontera; no se garantiza el borrado seguro de contraseñas String.
     * No renueva sesiones: las anteriores se rechazan en su siguiente revalidación, sin cancelar
     * operaciones iniciadas ni proteger consumidores que omitan esa comprobación.
     * No reintenta fallos ni relee tras escribir; un resultado JDBC puede ser provisional o incierto.
     * @param actor sesión administrativa vigente
     * @param userId ID positivo del objetivo
     * @param expectedVersion versión esperada del objetivo
     * @param newPassword nueva contraseña no nula ni vacía, sin reglas adicionales de complejidad
     * @return estado resultante sin credenciales ni hash
     * @throws SecurityException si el actor no está autorizado
     * @throws IllegalArgumentException por datos inválidos u objetivo inexistente
     * @throws ConcurrentModificationException si la versión del objetivo no coincide
     */
    public UserChangeResult changePassword(UserSession actor, String userId, long expectedVersion, String newPassword) {
        return boundary.execute(() -> {
            sessions.validate(actor);
            if (newPassword == null || newPassword.isEmpty()) {
                throw new IllegalArgumentException("La contraseña no puede ser nula o vacía.");
            }
            UserAccount proposed = copy(loadExpected(userId, expectedVersion));
            proposed.setPasswordHash(PasswordHasher.hash(newPassword));
            users.update(proposed);
            return new UserChangeResult(proposed.getId(), proposed.getRole(), proposed.isActive(), proposed.getSecurityVersion());
        });
    }

    private UserAccount loadExpected(String userId, long expectedVersion) {
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
        return current;
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

    /** Entrada inmutable de creación. No registrar el request ni sus accesores de credenciales. */
    public record CreateUserRequest(String username, String password, UserRole role, boolean active) {
        /** Omite todos los datos de entrada para evitar exposición accidental de credenciales. */
        @Override public String toString() {
            return "CreateUserRequest[datos protegidos]";
        }
    }
}
