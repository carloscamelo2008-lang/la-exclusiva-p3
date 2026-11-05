package com.laexclusiva.ui;

import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.repository.UserRepository;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.ui.controller.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private AuthenticationService authenticationService;

    @Override
    public void init() {
        UserRepository userRepository = new InMemoryUserRepository();
        // El servicio de autenticación solo recibe el repositorio según la implementación actual
        this.authenticationService = new AuthenticationService(userRepository);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
        Parent root = loader.load();

        LoginController controller = loader.getController();
        controller.setAuthenticationService(authenticationService);

        primaryStage.setTitle("La Exclusiva - Inicio de Sesión");
        primaryStage.setScene(new Scene(root, 400, 350));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}