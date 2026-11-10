package com.laexclusiva.ui;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher; // Importante: Usar el PasswordHasher oficial
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.ui.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        
        // Uso directo del PasswordHasher oficial del equipo
        String adminPasswordHash = PasswordHasher.hash("admin123");
        String workerPasswordHash = PasswordHasher.hash("worker123");

        userRepository.save(new UserAccount(1L, "admin", adminPasswordHash, UserRole.ADMINISTRATOR, null));
        userRepository.save(new UserAccount(2L, "worker", workerPasswordHash, UserRole.WORKER, null));

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