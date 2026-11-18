package com.laexclusiva.ui;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.ui.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import java.io.UncheckedIOException;

public class App extends Application {

    private final UserRepository userRepository = new InMemoryUserRepository();
    private final AuthenticationService authenticationService = new AuthenticationService(userRepository);
    private ApplicationServices applicationServices;

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public void initializeDemoAccounts(String adminPass, String workerPass) {
        if (adminPass == null || adminPass.isBlank() || workerPass == null || workerPass.isBlank()) {
            throw new IllegalStateException("Modo demo activado pero faltan las contraseñas requeridas.");
        }
        userRepository.save(new UserAccount(1L, "admin", PasswordHasher.hash(adminPass), UserRole.ADMINISTRATOR, null));
        userRepository.save(new UserAccount(2L, "worker", PasswordHasher.hash(workerPass), UserRole.WORKER, null));
    }

    public void initializeInitialAdmin(String adminPass) {
        if (adminPass == null || adminPass.isBlank()) {
            throw new IllegalStateException("Arranque normal requiere definir la contraseña inicial del administrador mediante LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD.");
        }
        userRepository.save(new UserAccount(1L, "admin", PasswordHasher.hash(adminPass), UserRole.ADMINISTRATOR, null));
    }

    public void processInitialization(Parameters params) {
        boolean isDemo = params != null && params.getRaw().contains("--demo");
        if (isDemo) {
            String adminPass = System.getenv("LAEXCLUSIVA_DEMO_ADMIN_PASSWORD");
            String workerPass = System.getenv("LAEXCLUSIVA_DEMO_WORKER_PASSWORD");
            initializeDemoAccounts(adminPass, workerPass);
        } else if (userRepository.findAll().isEmpty()) {
            String initialAdminPass = System.getenv("LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD");
            initializeInitialAdmin(initialAdminPass);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        processInitialization(getParameters());
        applicationServices = ApplicationServices.fromEnvironment();
        showLogin(primaryStage);
    }

    private void showLogin(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
        Parent root = loader.load();

        LoginController loginController = loader.getController();
        loginController.setAuthenticationService(authenticationService);
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

    public static void main(String[] args) {
        launch(args);
    }
}
