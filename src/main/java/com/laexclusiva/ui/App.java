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

public class App extends Application {

    private final UserRepository userRepository = new InMemoryUserRepository();

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

        AuthenticationService authService = new AuthenticationService(userRepository);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
        Parent root = loader.load();

        LoginController loginController = loader.getController();
        loginController.setAuthenticationService(authService);

        primaryStage.setTitle("La Exclusiva - Iniciar Sesión");
        primaryStage.setScene(new Scene(root, 400, 300));
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}