package com.laexclusiva.ui.controller;

import com.laexclusiva.model.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class MainController {

    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Button btnAdminUsers;

    private UserSession currentSession;

    public void initSession(UserSession session) {
        this.currentSession = session;
        
        // Accedemos a través de getUserAccount() tal como está definido en el modelo
        String username = session.getUserAccount().getUsername();
        String role = session.getUserAccount().getRole().name();

        welcomeLabel.setText("¡Bienvenido, " + username + "!");
        roleLabel.setText("Rol actual: " + role);

        if ("ADMINISTRATOR".equalsIgnoreCase(role)) {
            btnAdminUsers.setVisible(true);
            btnAdminUsers.setManaged(true);
        } else {
            btnAdminUsers.setVisible(false);
            btnAdminUsers.setManaged(false);
        }
    }
}