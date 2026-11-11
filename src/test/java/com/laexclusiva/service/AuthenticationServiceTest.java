package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.ui.App;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceTest {

    @BeforeAll
    public static void initJavaFX() {
        // Inicializa el toolkit de JavaFX en entorno headless para permitir pruebas de FXML
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Si ya estaba inicializado, ignoramos la excepción
        }
    }

    @Test
    public void testAuthenticationWithService() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        String adminHash = PasswordHasher.hash("admin123");
        repository.save(new UserAccount(1L, "admin", adminHash, UserRole.ADMINISTRATOR, null));

        AuthenticationService authService = new AuthenticationService(repository);

        assertTrue(authService.authenticate("admin", "admin123").isPresent(), "El usuario debe autenticarse correctamente");
        assertFalse(authService.authenticate("admin", "wrongpass").isPresent(), "La autenticación debe fallar con contraseña incorrecta");
    }

    @Test
    public void testAppDemoAccountsInitializationMechanism() {
        App app = new App();
        assertTrue(app.getUserRepository().findAll().isEmpty(), "En arranque normal la aplicación no debe inicializar cuentas demo");
    }

    @Test
    public void testFxmlViewsLoadingWithFXMLLoader() {
        assertDoesNotThrow(() -> {
            URL loginUrl = getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml");
            assertNotNull(loginUrl, "El recurso login_view.fxml debe existir");
            FXMLLoader loginLoader = new FXMLLoader(loginUrl);
            loginLoader.load();
        }, "login_view.fxml debe cargarse mediante FXMLLoader sin errores");

        assertDoesNotThrow(() -> {
            URL menuUrl = getClass().getResource("/com/laexclusiva/ui/fxml/main_menu_view.fxml");
            assertNotNull(menuUrl, "El recurso main_menu_view.fxml debe existir");
            FXMLLoader menuLoader = new FXMLLoader(menuUrl);
            menuLoader.load();
        }, "main_menu_view.fxml debe cargarse mediante FXMLLoader sin errores");
    }
}