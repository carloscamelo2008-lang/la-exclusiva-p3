package com.laexclusiva.ui.controller;

import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.model.UserSession;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Optional;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private AuthenticationService authenticationService;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            errorLabel.setText("Por favor, ingrese usuario y contraseña.");
            return;
        }

        try {
            Optional<UserSession> sessionOpt = authenticationService.createSession(username.trim(), password);
            
            if (sessionOpt.isPresent()) {
                UserSession session = sessionOpt.get();
                
                // Cargar la vista del menú principal
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
                Parent root = loader.load();
                
                // Obtener el controlador del menú y pasarle la sesión activa
                MainController mainController = loader.getController();
                mainController.initSession(session);
                
                // Cambiar la escena en la ventana actual
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.setTitle("La Exclusiva - Menú Principal");
                stage.centerOnScreen();
                
            } else {
                errorLabel.setStyle("-fx-text-fill: red;");
                errorLabel.setText("Credenciales inválidas o cuenta inactiva.");
            }
        } catch (IllegalArgumentException | IOException e) {
            errorLabel.setStyle("-fx-text-fill: red;");
            errorLabel.setText("Error al iniciar sesión o cargar la vista.");
        }
    }
}