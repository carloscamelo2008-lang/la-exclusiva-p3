package com.laexclusiva.ui.controller;

import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService; // Ajusta el paquete según corresponda
import com.laexclusiva.model.Permission; // Ajusta el paquete según corresponda
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class MainController {

    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Button btnAdminUsers;

    private UserSession currentSession;
    private final AuthorizationService authorizationService = new AuthorizationService(); // O inyectado por constructor/setter

    public void initSession(UserSession session) {
        this.currentSession = session;
        
        String username = session.getUserAccount().getUsername();
        String role = session.getUserAccount().getRole().name();

        welcomeLabel.setText("¡Bienvenido, " + username + "!");
        roleLabel.setText("Rol actual: " + role);

        // Validación de permisos mediante AuthorizationService (Punto 3 del líder)
        boolean hasPermission = authorizationService.hasPermission(session, Permission.MANAGE_USERS);
        btnAdminUsers.setVisible(hasPermission);
        btnAdminUsers.setManaged(hasPermission);
    }
}