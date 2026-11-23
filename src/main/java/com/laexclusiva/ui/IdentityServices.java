package com.laexclusiva.ui;

import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcUserAdministrationBoundary;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.security.PasswordPolicy;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.InMemoryUserAdministrationBoundary;
import com.laexclusiva.service.SellerService;
import com.laexclusiva.service.UserAdministrationBoundary;
import com.laexclusiva.service.UserManagementService;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;

/** Compone repositorio, autenticación, autorización y administración para una aplicación. */
public final class IdentityServices {
    private final UserRepository users;
    private final AuthenticationService authentication;
    private final AuthorizationService authorization;
    private final UserManagementService userManagement;
    private final UserAdministrationBoundary administrationBoundary;

    private IdentityServices(UserRepository users, UserAdministrationBoundary administrationBoundary,
                             SellerService sellers) {
        this.users = users;
        this.authentication = new AuthenticationService(users);
        this.authorization = new AuthorizationService(authentication.sessions());
        this.administrationBoundary = administrationBoundary;
        this.userManagement = new UserManagementService(users, administrationBoundary, authorization,
                sellers, authentication);
    }

    /**
     * Crea una composición independiente con almacenamiento en memoria inicialmente vacío.
     * @return repositorio y autenticación de esta composición
     */
    public static IdentityServices inMemory() {
        return inMemory(null);
    }

    /** Compone identidad en memoria con consulta de vendedores para la administración de cuentas. */
    public static IdentityServices inMemory(SellerService sellers) {
        InMemoryUserRepository users = new InMemoryUserRepository();
        return new IdentityServices(users, new InMemoryUserAdministrationBoundary(users), sellers);
    }

    /**
     * Compone usuarios JDBC sin abrir conexiones ni crear infraestructura adicional.
     * El llamador conserva la responsabilidad de compartir el proveedor y su coordinador.
     * @param provider proveedor JDBC existente que utilizará el repositorio
     * @return repositorio JDBC y autenticación de esta composición
     * @throws IllegalArgumentException si falta el proveedor
     */
    public static IdentityServices postgres(JdbcConnectionProvider provider) {
        return postgres(provider, null);
    }

    /** Compone usuarios, autorización y administración sobre el proveedor JDBC compartido. */
    public static IdentityServices postgres(JdbcConnectionProvider provider, SellerService sellers) {
        if (provider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        return new IdentityServices(new UserRepositoryJdbc(provider),
                new JdbcUserAdministrationBoundary(provider), sellers);
    }

    /** @return la misma instancia de repositorio durante la vida de la composición */
    public UserRepository users() {
        return users;
    }

    /** @return la misma instancia de autenticación durante la vida de la composición */
    public AuthenticationService authentication() {
        return authentication;
    }

    /** @return validador de la misma autoridad que autentica esta composición */
    public com.laexclusiva.service.AuthenticatedSessionValidator sessions() { return authentication.sessions(); }

    /** @return autorizador vinculado al emisor y al repositorio de esta composición */
    public AuthorizationService authorization() { return authorization; }

    /** @return servicio de administración protegido por el límite de identidad compartido */
    public UserManagementService userManagement() { return userManagement; }

    /**
     * Crea el administrador inicial solo cuando no existe ninguna cuenta. La verificación y el alta
     * se ejecutan bajo el mismo límite administrativo, que en PostgreSQL toma el bloqueo de identidad.
     *
     * @param username nombre inicial, normalizado por el repositorio
     * @param password contraseña original que debe cumplir la política centralizada
     * @return {@code true} si se creó la cuenta; {@code false} si ya había cuentas
     * @throws IllegalArgumentException si los datos no cumplen sus contratos
     */
    public boolean bootstrapInitialAdministrator(String username, String password) {
        return administrationBoundary.execute(() -> {
            if (!users.findAll().isEmpty()) return false;
            validateBootstrapUsername(username);
            PasswordPolicy.validate(password);
            users.save(new UserAccount(null, username.trim(), PasswordHasher.hash(password),
                    UserRole.ADMINISTRATOR, null));
            return true;
        });
    }

    /**
     * Crea las cuentas demo solo en un repositorio vacío y valida ambas contraseñas antes de escribir.
     * Si la segunda inserción falla, compensa únicamente la primera cuenta creada por esta llamada.
     *
     * @param adminPassword contraseña de la cuenta administradora demo
     * @param workerPassword contraseña de la cuenta trabajadora demo
     * @return {@code true} si se crearon ambas cuentas; {@code false} si el repositorio no estaba vacío
     */
    public boolean bootstrapDemoAccounts(String adminPassword, String workerPassword) {
        return administrationBoundary.execute(() -> {
            if (!users.findAll().isEmpty()) return false;
            PasswordPolicy.validate(adminPassword);
            PasswordPolicy.validate(workerPassword);
            UserAccount admin = new UserAccount(null, "admin", PasswordHasher.hash(adminPassword),
                    UserRole.ADMINISTRATOR, null);
            users.save(admin);
            try {
                users.save(new UserAccount(null, "worker", PasswordHasher.hash(workerPassword),
                        UserRole.WORKER, null));
                return true;
            } catch (RuntimeException | Error failure) {
                if (admin.getId() != null) {
                    try {
                        users.delete(admin.getId().toString());
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        });
    }

    private static void validateBootstrapUsername(String username) {
        if (username == null || username.isBlank() || username.length() > 100
                || username.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("El usuario administrador inicial debe tener entre 1 y 100 caracteres válidos.");
        }
    }
}
