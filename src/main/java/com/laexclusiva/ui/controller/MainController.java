package com.laexclusiva.ui.controller;

import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService; // Ajusta el paquete según corresponda
import com.laexclusiva.model.Permission; // Ajusta el paquete según corresponda
import com.laexclusiva.service.CustomerService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    @FXML private BorderPane rootPane;
    @FXML private Node dashboard;
    @FXML private Label navigationMessage;
    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Button btnSellers;
    @FXML private Button btnAdminUsers;

    private UserSession currentSession;
    private CustomerService customerService;
    private final AuthorizationService authorizationService = new AuthorizationService(); // O inyectado por constructor/setter

    public void setCustomerService(CustomerService customerService) {
        this.customerService = customerService;
    }

    @FXML
    private void handleShowCustomers() {
        if (!authorizationService.hasPermission(currentSession, Permission.VIEW_CUSTOMER)) {
            handleShowDashboard();
            navigationMessage.setText("No tiene permiso para consultar clientes.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/customers_view.fxml"));
            Node customerView = loader.load();
            CustomerController controller = loader.getController();
            controller.showCustomers(customerService.getAllCustomers());
            rootPane.setCenter(customerView);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir la consulta de clientes.", failure);
            navigationMessage.setText("No se pudo consultar la lista de clientes.");
        }
    }

    @FXML
    private void handleShowDashboard() {
        rootPane.setCenter(dashboard);
        navigationMessage.setText("");
    }

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

        boolean canViewSellers = authorizationService.hasPermission(session, Permission.VIEW_SELLERS);
        btnSellers.setVisible(canViewSellers);
        btnSellers.setManaged(canViewSellers);
    }
}
