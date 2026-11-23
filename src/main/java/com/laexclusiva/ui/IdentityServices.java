package com.laexclusiva.ui;

import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.service.AuthenticationService;

/** Compone identidad sin inicializar cuentas ni conectarla a la aplicación. */
public final class IdentityServices {
    private final UserRepository users;
    private final AuthenticationService authentication;

    private IdentityServices(UserRepository users) {
        this.users = users;
        this.authentication = new AuthenticationService(users);
    }

    /**
     * Crea una composición independiente con almacenamiento en memoria inicialmente vacío.
     * @return repositorio y autenticación de esta composición
     */
    public static IdentityServices inMemory() {
        return new IdentityServices(new InMemoryUserRepository());
    }

    /**
     * Compone usuarios JDBC sin abrir conexiones ni crear infraestructura adicional.
     * El llamador conserva la responsabilidad de compartir el proveedor y su coordinador.
     * @param provider proveedor JDBC existente que utilizará el repositorio
     * @return repositorio JDBC y autenticación de esta composición
     * @throws IllegalArgumentException si falta el proveedor
     */
    public static IdentityServices postgres(JdbcConnectionProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("El proveedor JDBC es obligatorio.");
        }
        return new IdentityServices(new UserRepositoryJdbc(provider));
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
}
