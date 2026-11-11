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

    public void initializeDemoAccountsIfRequested(Parameters params) {
        boolean isDemo = params != null && params.getRaw().contains("--demo");
        if (isDemo) {
            String adminPass = System.getenv("LAEXCLUSIVA_DEMO_ADMIN_PASSWORD");
            String workerPass = System.getenv("LAEXCLUSIVA_DEMO_WORKER_PASSWORD");

            if (adminPass != null && !adminPass.isBlank() && workerPass != null && !workerPass.isBlank()) {
                userRepository.save(new UserAccount(1L, "admin", PasswordHasher.hash(adminPass), UserRole.ADMINISTRATOR, null));
                userRepository.save(new UserAccount(2L, "worker", PasswordHasher.hash(workerPass), UserRole.WORKER, null));
            }
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        initializeDemoAccountsIfRequested(getParameters());

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