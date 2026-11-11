package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.ui.App;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceTest {

    @BeforeAll
    public static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit ya inicializado previamente
        }
    }

    @Test
    public void testAppDemoAccountsInitializationWithParameters() {
        App app = new App();
        
        Application.Parameters mockParams = new Application.Parameters() {
            @Override
            public List<String> getRaw() { return List.of("--demo"); }
            @Override
            public List<String> getUnnamed() { return List.of("--demo"); }
            @Override
            public java.util.Map<String, String> getNamed() { return java.util.Collections.emptyMap(); }
        };

        String adminPass = System.getenv("LAEXCLUSIVA_DEMO_ADMIN_PASSWORD");
        String workerPass = System.getenv("LAEXCLUSIVA_DEMO_WORKER_PASSWORD");

        if (adminPass != null && workerPass != null && !adminPass.isBlank() && !workerPass.isBlank()) {
            assertDoesNotThrow(() -> app.initializeDemoAccountsIfRequested(mockParams));
            AuthenticationService authService = new AuthenticationService(app.getUserRepository());
            assertTrue(authService.authenticate("admin", adminPass).isPresent(), "Admin demo debe autenticarse");
            assertTrue(authService.authenticate("worker", workerPass).isPresent(), "Worker demo debe autenticarse");
        } else {
            assertThrows(IllegalStateException.class, () -> app.initializeDemoAccountsIfRequested(mockParams),
                    "Debe lanzar excepción si faltan variables de entorno en modo --demo");
        }
    }

    @Test
    public void testNormalAppInitializationDoesNotCreateDemoAccounts() {
        App app = new App();
        Application.Parameters mockParams = new Application.Parameters() {
            @Override
            public List<String> getRaw() { return List.of(); }
            @Override
            public List<String> getUnnamed() { return List.of(); }
            @Override
            public java.util.Map<String, String> getNamed() { return java.util.Collections.emptyMap(); }
        };

        app.initializeDemoAccountsIfRequested(mockParams);
        assertTrue(app.getUserRepository().findAll().isEmpty(), "Arranque normal sin --demo no debe crear cuentas demo");
    }

    @Test
    public void testFxmlViewsLoadingWithFXMLLoader() {
        assertDoesNotThrow(() -> {
            URL loginUrl = getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml");
            assertNotNull(loginUrl, "login_view.fxml debe existir");
            FXMLLoader loginLoader = new FXMLLoader(loginUrl);
            loginLoader.load();
        });

        assertDoesNotThrow(() -> {
            URL menuUrl = getClass().getResource("/com/laexclusiva/ui/fxml/main_menu_view.fxml");
            assertNotNull(menuUrl, "main_menu_view.fxml debe existir");
            FXMLLoader menuLoader = new FXMLLoader(menuUrl);
            menuLoader.load();
        });
    }
}