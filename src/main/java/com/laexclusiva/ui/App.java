package com.laexclusiva.ui;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.ui.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class App extends Application {

    // Método auxiliar seguro para generar el hash PBKDF2 que espera tu AuthenticationService
    private static String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar hash de contraseña", e);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        
        // Generación de cuentas con contraseña hasheada y sellerId como null (Puntos 1 y 2 del líder)
        String adminHash = hashPassword("admin123");
        String workerHash = hashPassword("worker123");

        userRepository.save(new UserAccount(1L, "admin", adminHash, UserRole.ADMINISTRATOR, null));
        userRepository.save(new UserAccount(2L, "worker", workerHash, UserRole.WORKER, null));

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