package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.CustomerService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;

/** Presenta el listado de clientes y permite registrar nuevos según el permiso recibido. */
public class CustomerController {

    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, String> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, String> addressColumn;
    @FXML private HBox registrationPane;
    @FXML private TextField idField;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private Label messageLabel;

    private CustomerService customerService;
    private UserSession session;
    private AuthorizationService authorizationService;

    /** Inyecta la autorización respaldada por la identidad de la aplicación. */
    public void setAuthorizationService(AuthorizationService authorizationService) {
        if (authorizationService == null) throw new IllegalArgumentException("La autorización es obligatoria.");
        authorizationService.requireSessionAuthority();
        this.authorizationService = authorizationService;
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhone()));
        addressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getAddress()));
    }

    /** Reemplaza las filas visibles por los clientes obtenidos del servicio configurado. */
    public void showCustomers(List<Customer> customers) {
        requirePermission(Permission.VIEW_CUSTOMER, "No tiene permiso para consultar clientes.");
        customersTable.getItems().setAll(customers);
    }

    /** Configura el servicio y deriva la visibilidad y autorización del alta de la sesión activa.
     *
     * @param service servicio de clientes
     * @param session sesión autenticada
     * @throws IllegalArgumentException si falta el servicio o la sesión
     */
    public void configure(CustomerService service, UserSession session) {
        requireSessionAuthority();
        if (service == null) throw new IllegalArgumentException("El servicio de clientes es obligatorio.");
        if (session == null) throw new IllegalArgumentException("La sesión es obligatoria.");
        customerService = service;
        this.session = session;
        requirePermission(Permission.VIEW_CUSTOMER, "No tiene permiso para consultar clientes.");
        boolean canRegister = authorizationService.hasPermission(session, Permission.REGISTER_CUSTOMER);
        registrationPane.setVisible(canRegister);
        registrationPane.setManaged(canRegister);
    }

    @FXML
    private void handleRegisterCustomer() {
        try {
            requirePermission(Permission.REGISTER_CUSTOMER, "No tiene permiso para registrar clientes.");
            authorizationService.execute(session, Permission.REGISTER_CUSTOMER, () ->
                    customerService.registerCustomer(idField.getText(), nameField.getText(),
                            phoneField.getText(), addressField.getText()));
            messageLabel.setText("Cliente registrado.");
            idField.clear();
            nameField.clear();
            phoneField.clear();
            addressField.clear();
            showCustomers(authorizationService.execute(session, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers));
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró el cliente: " + safeMessage(failure));
        }
    }

    private void requirePermission(Permission permission, String message) {
        requireSessionAuthority();
        if (!authorizationService.hasPermission(session, permission)) throw new SecurityException(message);
    }

    private void requireSessionAuthority() {
        if (authorizationService == null) throw new SecurityException("No se configuró la autoridad de sesión.");
        authorizationService.requireSessionAuthority();
    }

    private static String safeMessage(Throwable failure) {
        return UiErrorMessages.safeMessage(failure);
    }
}
