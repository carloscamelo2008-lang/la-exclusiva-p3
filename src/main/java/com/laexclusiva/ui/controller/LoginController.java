package com.laexclusiva.ui.controller;

import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.model.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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
            // createSession devuelve Optional<UserSession>
            Optional<UserSession> sessionOpt = authenticationService.createSession(username.trim(), password);
            
            if (sessionOpt.isPresent()) {
                errorLabel.setStyle("-fx-text-fill: green;");
                errorLabel.setText("¡Inicio de sesión exitoso!");
            } else {
                errorLabel.setStyle("-fx-text-fill: red;");
                errorLabel.setText("Credenciales inválidas o cuenta inactiva.");
            }
        } catch (IllegalArgumentException e) {
            errorLabel.setStyle("-fx-text-fill: red;");
            errorLabel.setText("Credenciales inválidas o cuenta inactiva.");
        }
    }
}