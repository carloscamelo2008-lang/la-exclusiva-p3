package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.security.PasswordPolicy;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Crea cuentas y cambia rol, actividad, contraseña y vendedor dentro del protocolo administrativo cooperativo.
 * Repositorio, revalidador y frontera deben compartir almacenamiento: en memoria el mismo
 * repositorio monitorizado y en JDBC el mismo proveedor. El constructor no puede verificar
 * ese emparejamiento. No protege escrituras directas ni SQL externo ni integra la aplicación.
 */
public final class UserManagementService {
    private final UserRepository users;
    private final UserAdministrationBoundary boundary;
    private final AdministrativeSessionValidator sessions;
    private final SellerService sellers;
    private final ActiveAdministratorPolicy policy = new ActiveAdministratorPolicy();

    /** Error conocido de validación que el administrador puede corregir en la pantalla. */
    public static final class ValidationException extends IllegalArgumentException {
        /**
         * Crea un error de validación seguro para presentar al operador.
         * @param message explicación breve del dato que debe corregirse, sin detalles técnicos
         */
        public ValidationException(String message) {
            super(message);
        }
    }

    /**
     * @param users repositorio de cuentas compartido con la frontera
     * @param boundary frontera administrativa obligatoria
     * @param authorization mecanismo existente de permisos
     * Este constructor conserva compatibilidad binaria, pero sus operaciones fallan cerrado
     * al no recibir un emisor. Utilizar la sobrecarga con AuthenticationService.
     * @throws IllegalArgumentException si falta una dependencia
     */
    public UserManagementService(UserRepository users, UserAdministrationBoundary boundary,
                                 AuthorizationService authorization) {
        this(users, boundary, authorization, null);
    }

    /**
     * Añade consulta de vendedores existentes sin construir infraestructura adicional.
     * Sin emisor explícito las operaciones administrativas fallan cerrado.
     * @param users repositorio compartido de cuentas
     * @param boundary frontera administrativa de ese almacenamiento
     * @param authorization mecanismo existente de permisos
     * @param sellers consulta de vendedores, opcional solo para operaciones que no verifican existencia
     * @throws IllegalArgumentException si falta repositorio, frontera o autorización
     */
    public UserManagementService(UserRepository users, UserAdministrationBoundary boundary,
                                 AuthorizationService authorization, SellerService sellers) {
        this(users, boundary, authorization, sellers, null);
    }

    /** Compone administración estricta con el emisor del mismo repositorio. */
    public UserManagementService(UserRepository users, UserAdministrationBoundary boundary,
                                 AuthorizationService authorization, SellerService sellers,
                                 AuthenticationService authentication) {
        if (users == null || boundary == null || authorization == null) {
            throw new IllegalArgumentException("Repositorio, frontera y autorización son obligatorios.");
        }
        this.users = users;
        this.boundary = boundary;
        this.sessions = new AdministrativeSessionValidator(users, authorization, authentication);
        this.sellers = sellers;
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
            if (role == null) throw new ValidationException("El rol es obligatorio.");
            return update(userId, expectedVersion, proposed -> proposed.setRole(role));
        });
    }

    /**
     * Activa o desactiva una cuenta bajo las mismas validaciones que el cambio de rol.
     * No renueva sesiones. Un error se propaga sin reintentos ni lecturas posteriores al UPDATE.
     * En memoria no hay rollback general; se valida todo antes de la única escritura final.
     * En JDBC un fallo de commit/cierre puede dejar resultado incierto: consultar antes de repetir.
     * Si queda activa con vendedor, exige exclusividad incluso en no-op; no consulta su existencia histórica.
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
            return update(userId, expectedVersion, proposed -> proposed.setActive(active), true);
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
            if (request == null) throw new ValidationException("Los datos de creación son obligatorios.");
            String username = request.username();
            if (username == null || username.isBlank() || username.length() > 100 || username.indexOf('\0') >= 0) {
                throw new ValidationException("El nombre de usuario debe tener entre 1 y 100 caracteres válidos.");
            }
            validatePassword(request.password());
            if (request.role() == null) throw new ValidationException("El rol es obligatorio.");
            UserAccount user = new UserAccount(null, username, PasswordHasher.hash(request.password()), request.role(), null);
            user.setActive(request.active());
            try {
                users.save(user);
            } catch (IllegalArgumentException duplicate) {
                if ("El nombre de usuario ya se encuentra registrado.".equals(duplicate.getMessage())
                        || "Ya existe un usuario con el ID o nombre indicado.".equals(duplicate.getMessage())) {
                    throw new ValidationException("El nombre de usuario ya se encuentra registrado.");
                }
                throw duplicate;
            }
            return new UserChangeResult(user.getId(), user.getRole(), user.isActive(), user.getSecurityVersion());
        });
    }

    private UserChangeResult update(String userId, long expectedVersion, Consumer<UserAccount> change) {
        return update(userId, expectedVersion, change, false);
    }

    private UserChangeResult update(String userId, long expectedVersion, Consumer<UserAccount> change,
                                    boolean checkSellerExclusivity) {
        UserAccount current = loadExpected(userId, expectedVersion);
        UserAccount proposed = copy(current);
        change.accept(proposed);
        List<UserAccount> accounts = users.findAll();
        if (checkSellerExclusivity) validateSellerExclusivity(proposed, accounts);
        long administrators = accounts.stream()
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
     * @param newPassword nueva contraseña que cumple la política centralizada
     * @return estado resultante sin credenciales ni hash
     * @throws SecurityException si el actor no está autorizado
     * @throws IllegalArgumentException por datos inválidos u objetivo inexistente
     * @throws ConcurrentModificationException si la versión del objetivo no coincide
     */
    public UserChangeResult changePassword(UserSession actor, String userId, long expectedVersion, String newPassword) {
        return boundary.execute(() -> {
            sessions.validate(actor);
            validatePassword(newPassword);
            UserAccount proposed = copy(loadExpected(userId, expectedVersion));
            proposed.setPasswordHash(PasswordHasher.hash(newPassword));
            users.update(proposed);
            return new UserChangeResult(proposed.getId(), proposed.getRole(), proposed.isActive(), proposed.getSecurityVersion());
        });
    }

    /**
     * Asigna un vendedor existente o desvincula con null, sin alterar otros campos.
     * Comprueba exclusividad entre cuentas activas de cualquier rol; las inactivas pueden compartirlo.
     * Revalida y consulta dentro de la misma frontera. La comparación de IDs es exacta.
     * No corrige duplicados históricos ni protege escritores que omitan el protocolo; no hay
     * índice único PostgreSQL para esta regla. Ventas aún requiere su integración de revalidación.
     * @param actor sesión administrativa vigente
     * @param userId ID positivo del objetivo
     * @param expectedVersion versión esperada del objetivo
     * @param sellerId ID exacto del vendedor o null para desvincular
     * @return estado seguro, sin escribir cuando la asignación no cambia
     * @throws SecurityException si el actor no está autorizado
     * @throws IllegalArgumentException por parámetros inválidos, usuario o vendedor inexistente
     * @throws ConcurrentModificationException si la cuenta objetivo cambió
     * @throws IllegalStateException si falta la consulta o el vendedor ya pertenece a otra cuenta activa
     */
    public UserChangeResult assignSeller(UserSession actor, String userId, long expectedVersion, String sellerId) {
        return boundary.execute(() -> {
            sessions.validate(actor);
            UserAccount current = loadExpected(userId, expectedVersion);
            if (sellerId != null) {
                if (sellerId.isBlank() || sellerId.length() > 32 || sellerId.indexOf('\0') >= 0) {
                    throw new ValidationException("El ID de vendedor debe tener entre 1 y 32 caracteres válidos.");
                }
                if (sellers == null) throw new IllegalStateException("La consulta de vendedores no está configurada.");
                var seller = sellers.getSellerById(sellerId)
                        .orElseThrow(() -> new ValidationException("El vendedor no existe."));
                if (!sellerId.equals(seller.getId())) throw new IllegalStateException("La identidad del vendedor es inconsistente.");
            }
            UserAccount proposed = copy(current);
            proposed.setSellerId(sellerId);
            if (proposed.isActive() && sellerId != null) validateSellerExclusivity(proposed, users.findAll());
            if (!Objects.equals(current.getSellerId(), sellerId)) users.update(proposed);
            return new UserChangeResult(proposed.getId(), proposed.getRole(), proposed.isActive(), proposed.getSecurityVersion());
        });
    }

    /**
     * Consulta cuentas bajo la misma autoridad y frontera que las mutaciones administrativas.
     * El resultado omite hashes, contraseñas y otros datos de autenticación.
     *
     * @param actor sesión administrativa vigente
     * @return resumen seguro ordenado por ID
     * @throws SecurityException si la sesión no es auténtica o no conserva MANAGE_USERS
     */
    public List<UserSummary> listUsers(UserSession actor) {
        return boundary.execute(() -> {
            sessions.validate(actor);
            return users.findAll().stream()
                    .map(user -> new UserSummary(user.getId(), user.getUsername(), user.getRole(),
                            user.isActive(), user.getSellerId(), user.getSecurityVersion()))
                    .toList();
        });
    }

    private static void validateSellerExclusivity(UserAccount proposed, List<UserAccount> accounts) {
        if (!proposed.isActive() || proposed.getSellerId() == null) return;
        boolean occupied = accounts.stream().anyMatch(other -> other.isActive()
                && !Objects.equals(other.getId(), proposed.getId())
                && proposed.getSellerId().equals(other.getSellerId()));
        if (occupied) throw new IllegalStateException("El vendedor ya está asignado a otra cuenta activa.");
    }

    private UserAccount loadExpected(String userId, long expectedVersion) {
        Long id;
        try {
            id = userId == null ? null : Long.valueOf(userId.trim());
        } catch (NumberFormatException invalid) {
            throw new ValidationException("El ID del objetivo debe ser un entero positivo.");
        }
        if (id == null || id <= 0 || expectedVersion < 0) {
            throw new ValidationException("Se requieren un ID positivo y una versión no negativa.");
        }
        UserAccount current = users.findById(id.toString())
                .orElseThrow(() -> new ValidationException("La cuenta objetivo no existe."));
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
        if (user.getAccountInstanceId() != null) result.setAccountInstanceId(user.getAccountInstanceId());
        return result;
    }

    private static void validatePassword(String password) {
        try {
            PasswordPolicy.validate(password);
        } catch (IllegalArgumentException invalid) {
            throw new ValidationException(invalid.getMessage());
        }
    }

    /** Resultado inmutable sin cuenta mutable, contraseña ni hash. */
    public record UserChangeResult(Long id, UserRole role, boolean active, long securityVersion) {}

    /** Vista segura de una cuenta para tablas administrativas; nunca contiene credenciales. */
    public record UserSummary(Long id, String username, UserRole role, boolean active,
                              String sellerId, long securityVersion) {}

    /** Entrada inmutable de creación. No registrar el request ni sus accesores de credenciales. */
    public record CreateUserRequest(String username, String password, UserRole role, boolean active) {
        /** Omite todos los datos de entrada para evitar exposición accidental de credenciales. */
        @Override public String toString() {
            return "CreateUserRequest[datos protegidos]";
        }
    }
}
