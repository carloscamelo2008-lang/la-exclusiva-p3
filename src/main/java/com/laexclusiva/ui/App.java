package com.laexclusiva.ui;

import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.ui.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

public class App extends Application {

    private ApplicationServices applicationServices;
    private boolean compositionSelectedForStartup;

    public UserRepository getUserRepository() {
        return services().identity().users();
    }

    /** Reutiliza la autoridad compartida para todo el ciclo JavaFX. */
    public AuthenticationService getAuthenticationService() {
        return services().identity().authentication();
    }

    public void initializeDemoAccounts(String adminPass, String workerPass) {
        ApplicationServices selected = services();
        if (selected.postgresMode()) {
            throw new IllegalStateException("Las cuentas demo solo se pueden inicializar en modo memory.");
        }
        try {
            selected.identity().bootstrapDemoAccounts(adminPass, workerPass);
            compositionSelectedForStartup = true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new IllegalStateException("Las contraseñas demo deben cumplir la política de contraseñas.");
        }
    }

    public void initializeInitialAdmin(String adminPass) {
        try {
            services().identity().bootstrapInitialAdministrator("admin", adminPass);
            compositionSelectedForStartup = true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new IllegalStateException("Defina LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD con una contraseña que cumpla la política.");
        }
    }

    public void processInitialization(Parameters params) {
        if (applicationServices == null) {
            applicationServices = ApplicationServices.fromEnvironment();
        }
        boolean isDemo = params != null && params.getRaw().contains("--demo");
        ApplicationServices selected = applicationServices;
        if (isDemo) {
            if (selected.postgresMode()) {
                throw new IllegalStateException("El modo --demo solo está disponible con LAEXCLUSIVA_PERSISTENCE_MODE=memory.");
            }
            String adminPass = System.getenv("LAEXCLUSIVA_DEMO_ADMIN_PASSWORD");
            String workerPass = System.getenv("LAEXCLUSIVA_DEMO_WORKER_PASSWORD");
            initializeDemoAccounts(adminPass, workerPass);
        } else if (selected.identity().users().findAll().isEmpty()) {
            String initialAdminPass = System.getenv("LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD");
            String initialUsername = System.getenv().getOrDefault("LAEXCLUSIVA_INITIAL_ADMIN_USERNAME", "admin");
            try {
                selected.identity().bootstrapInitialAdministrator(initialUsername, initialAdminPass);
            } catch (IllegalArgumentException | IllegalStateException failure) {
                throw new IllegalStateException("Configure un usuario administrador y una contraseña inicial que cumpla la política.");
            }
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        if (!compositionSelectedForStartup) {
            // En PostgreSQL no hay alternativa en memoria: los errores de esquema o conexión fallan cerrados.
            applicationServices = ApplicationServices.fromEnvironment();
            compositionSelectedForStartup = true;
        }
        processInitialization(getParameters());
        showLogin(primaryStage);
    }

    private void showLogin(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
        Parent root = loader.load();

        LoginController loginController = loader.getController();
        loginController.setApplicationServices(applicationServices);
        loginController.setOnLogout(() -> {
            try {
                showLogin(primaryStage);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });

        // Libera la ventana nativa y sus cambios de maximización pendientes antes de mostrar el Login.
        primaryStage.hide();
        primaryStage.setTitle("La Exclusiva - Iniciar Sesión");
        primaryStage.setMaximized(false);
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(370);
        primaryStage.setScene(new Scene(root, 400, 340));
        primaryStage.sizeToScene();
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    /** Crea composición en memoria solo para consumidores directos previos al arranque JavaFX. */
    private ApplicationServices services() {
        if (applicationServices == null) {
            applicationServices = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        }
        return applicationServices;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
