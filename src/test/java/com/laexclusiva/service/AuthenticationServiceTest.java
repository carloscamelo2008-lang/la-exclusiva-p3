package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.ui.App;
import com.laexclusiva.ui.controller.MainController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URL;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceTest {

    @BeforeAll
    public static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit de JavaFX ya activo
        }
    }

    @Test
    public void testAppDemoAccountsInitializationMechanism() {
        App app = new App();
        app.initializeDemoAccounts("passAdmin123", "passWorker123");

        AuthenticationService authService = new AuthenticationService(app.getUserRepository());
        assertTrue(authService.authenticate("admin", "passAdmin123").isPresent(), "Admin demo debe autenticarse correctamente");
        assertTrue(authService.authenticate("worker", "passWorker123").isPresent(), "Worker demo debe autenticarse correctamente");
    }

    @Test
    public void testAppDemoAccountsInitializationFailsWithoutPasswords() {
        App app = new App();
        assertThrows(IllegalStateException.class, () -> app.initializeDemoAccounts(null, "passWorker123"),
                "Debe fallar si falta la clave del administrador demo");
    }

    @Test
    public void testInitialAdminProvisioningSuccess() {
        App app = new App();
        app.initializeInitialAdmin("adminSecret123");

        AuthenticationService authService = new AuthenticationService(app.getUserRepository());
        assertTrue(authService.authenticate("admin", "adminSecret123").isPresent(), "El administrador inicial debe poder autenticarse");
    }

    @Test
    public void testInitialAdminProvisioningFailsWithoutPassword() {
        App app = new App();
        assertThrows(IllegalStateException.class, () -> app.initializeInitialAdmin(""),
                "Debe exigir LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD en el arranque normal");
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

    @ParameterizedTest
    @CsvSource({"ADMINISTRATOR, true", "WORKER, false"})
    void testMainMenuButtonPermissions(UserRole role, boolean expectedVisibility) throws Exception {
        FutureTask<Void> checkMenu = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            loader.load();
            MainController controller = loader.getController();
            UserSession session = new UserSession(new UserAccount(1L, "test", "unused", role, null));
            controller.initSession(session);

            Button sellers = (Button) loader.getNamespace().get("btnSellers");
            Button adminUsers = (Button) loader.getNamespace().get("btnAdminUsers");
            assertAll(
                    () -> assertEquals(expectedVisibility, sellers.isVisible(), "Visibilidad de Vendedores"),
                    () -> assertEquals(expectedVisibility, sellers.isManaged(), "Espacio de Vendedores"),
                    () -> assertEquals(expectedVisibility, adminUsers.isVisible(), "Visibilidad de Gestión de Usuarios"),
                    () -> assertEquals(expectedVisibility, adminUsers.isManaged(), "Espacio de Gestión de Usuarios")
            );
            return null;
        });
        Platform.runLater(checkMenu);
        checkMenu.get(10, TimeUnit.SECONDS);
    }
}
