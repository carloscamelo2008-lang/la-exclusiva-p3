package com.laexclusiva.ui;
import com.laexclusiva.ui.controller.LoginController;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.service.AuthenticationService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Inicializar repositorio y cuentas demo
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        
        userRepository.save(new UserAccount(1L, "admin", "admin123", UserRole.ADMINISTRATOR, "activo"));
        userRepository.save(new UserAccount(2L, "worker", "worker123", UserRole.WORKER, "activo"));

        AuthenticationService authService = new AuthenticationService(userRepository);

        // 2. Cargar la vista de Login
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
        Parent root = loader.load();

        // 3. Inyectar el servicio de autenticación en el controlador del login
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