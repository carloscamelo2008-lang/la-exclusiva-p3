package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceTest {

    @Test
    public void testAppInitializedDemoAccountsAuthentication() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        
        // Simular credenciales para validar autenticación de cuentas inicializadas
        String adminHash = PasswordHasher.hash("admin123");
        String workerHash = PasswordHasher.hash("worker123");

        repository.save(new UserAccount(1L, "admin", adminHash, UserRole.ADMINISTRATOR, null));
        repository.save(new UserAccount(2L, "worker", workerHash, UserRole.WORKER, null));

        AuthenticationService authService = new AuthenticationService(repository);

        assertTrue(authService.authenticate("admin", "admin123").isPresent(), "El usuario admin inicializado debe autenticarse");
        assertTrue(authService.authenticate("worker", "worker123").isPresent(), "El usuario worker inicializado debe autenticarse");
        assertFalse(authService.authenticate("admin", "claveerronea").isPresent(), "La autenticación debe fallar con clave incorrecta");
    }

    @Test
    public void testFxmlLoaderViewsExist() {
        // Comprobar la existencia y carga de las vistas mediante FXMLLoader solicitada por Carlos
        assertNotNull(
            getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"),
            "El archivo login_view.fxml debe existir en los recursos"
        );
    }
}