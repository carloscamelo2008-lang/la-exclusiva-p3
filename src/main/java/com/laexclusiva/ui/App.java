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
import javafx.scene.control.Alert;

/** Punto de entrada JavaFX que selecciona una única composición de persistencia. */
public class App extends Application {

    private ApplicationServices applicationServices;
    private boolean compositionSelectedForStartup;

    /** Constructor reservado para el lanzador JavaFX, que selecciona persistencia desde el entorno. */
    public App() { }

    /**
     * Inyecta una composición explícita para recorridos controlados y pruebas.
     *
     * @param applicationServices composición compartida con controladores y servicios
     * @throws IllegalArgumentException si la composición es nula
     */
    public App(ApplicationServices applicationServices) {
        if (applicationServices == null) throw new IllegalArgumentException("La composición es obligatoria.");
        this.applicationServices = applicationServices;
        this.compositionSelectedForStartup = true;
    }

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
        try {
            if (!compositionSelectedForStartup) {
                // La configuración normal usa PostgreSQL; memoria solo se elige explícitamente.
                applicationServices = ApplicationServices.fromEnvironment();
                compositionSelectedForStartup = true;
            }
            processInitialization(getParameters());
            showLogin(primaryStage);
        } catch (RuntimeException startupFailure) {
            showStartupFailure(primaryStage, startupFailure);
        }
    }

    private static void showStartupFailure(Stage stage, RuntimeException failure) {
        String message = failure instanceof IllegalArgumentException
                ? "No se pudo iniciar La Exclusiva. Revisa LAEXCLUSIVA_PERSISTENCE_MODE, la URL y las credenciales de PostgreSQL, y que las migraciones requeridas estén aplicadas."
                : "No se pudo conectar con PostgreSQL o validar su esquema. Comprueba que el servicio esté activo y que la base local esté preparada. No se modificó el esquema automáticamente.";
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("No se pudo iniciar La Exclusiva");
        alert.setHeaderText("Revisa la configuración comercial");
        alert.setContentText(message);
        alert.showAndWait();
        stage.close();
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

    /** Impide acceder a repositorios antes de que el arranque seleccione su composición. */
    private ApplicationServices services() {
        if (applicationServices == null) throw new IllegalStateException(
                "La composición de persistencia todavía no fue seleccionada.");
        return applicationServices;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
