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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private Label messageLabel;
    @FXML private Button registerCustomerButton, editCustomerButton, cancelEditButton, deleteCustomerButton;
    private String editingId;

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
        idColumn.setSortType(TableColumn.SortType.ASCENDING);
        customersTable.getSortOrder().setAll(List.of(idColumn));
    }

    /** Reemplaza las filas visibles por los clientes obtenidos del servicio configurado. */
    public void showCustomers(List<Customer> customers) {
        requirePermission(Permission.VIEW_CUSTOMER, "No tiene permiso para consultar clientes.");
        customersTable.getItems().setAll(customers);
        customersTable.sort();
    }

    /** Configura el servicio y ajusta las acciones disponibles según los permisos vigentes.
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
        registrationPane.setVisible(canRegister || authorizationService.hasPermission(session, Permission.EDIT_CUSTOMER));
        registrationPane.setManaged(registrationPane.isVisible());
        showAction(editCustomerButton, Permission.EDIT_CUSTOMER);
        showAction(deleteCustomerButton, Permission.DELETE_CUSTOMER);
        resetForm();
    }

    @FXML
    private void handleRegisterCustomer() {
        boolean editing = editingId != null;
        String confirmation;
        try {
            if (editing) {
                authorizationService.execute(session, Permission.EDIT_CUSTOMER, () ->
                        customerService.updateCustomer(editingId, nameField.getText(), phoneField.getText(),
                                addressField.getText()));
                confirmation = "Cliente actualizado.";
            } else {
                requirePermission(Permission.REGISTER_CUSTOMER, "No tiene permiso para registrar clientes.");
                if (!confirmSimilarNames()) return;
                Customer created = authorizationService.execute(session, Permission.REGISTER_CUSTOMER,
                        () -> customerService.registerCustomer(nameField.getText(), phoneField.getText(),
                                addressField.getText()));
                confirmation = "Cliente registrado. Identificador: " + created.getId() + ".";
            }
        } catch (RuntimeException failure) {
            messageLabel.setText((editing ? "No se actualizó el cliente: " : "No se registró el cliente: ")
                    + safeMessage(failure));
            return;
        }
        resetForm();
        refreshAfterWrite(confirmation);
    }

    private boolean confirmSimilarNames() {
        var matches = authorizationService.execute(session, Permission.VIEW_CUSTOMER,
                () -> customerService.findSimilarNames(nameField.getText()));
        if (matches.isEmpty()) return true;

        String names = matches.stream().limit(5).map(customer -> customer.getName() + " (" + customer.getId() + ")")
                .collect(java.util.stream.Collectors.joining("\n"));
        Alert warning = new Alert(Alert.AlertType.CONFIRMATION,
                "Ya hay clientes con ese nombre:\n" + names + "\n\n¿Desea registrar otro cliente?",
                ButtonType.CANCEL, ButtonType.OK);
        warning.setTitle("Posibles clientes duplicados");
        warning.setHeaderText(null);
        if (customersTable.getScene() != null) warning.initOwner(customersTable.getScene().getWindow());
        return warning.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @FXML
    private void handleEditCustomer() {
        try {
            requirePermission(Permission.EDIT_CUSTOMER, "No tiene permiso para editar clientes.");
            Customer customer = selectedCustomer();
            editingId = customer.getId();
            nameField.setText(customer.getName());
            phoneField.setText(customer.getPhone());
            addressField.setText(customer.getAddress());
            registerCustomerButton.setText("Guardar cambios");
            registerCustomerButton.setDisable(false);
            cancelEditButton.setVisible(true);
            cancelEditButton.setManaged(true);
            nameField.requestFocus();
        } catch (RuntimeException failure) {
            messageLabel.setText(safeMessage(failure));
        }
    }

    @FXML
    private void handleCancelEdit() {
        resetForm();
    }

    @FXML
    private void handleDeleteCustomer() {
        String id;
        try {
            requirePermission(Permission.DELETE_CUSTOMER, "No tiene permiso para eliminar clientes.");
            Customer customer = selectedCustomer();
            id = customer.getId();
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "¿Eliminar al cliente " + customer.getName() + "?", ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Eliminar cliente");
            confirmation.setHeaderText(null);
            if (customersTable.getScene() != null) confirmation.initOwner(customersTable.getScene().getWindow());
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            authorizationService.execute(session, Permission.DELETE_CUSTOMER, () -> customerService.deleteCustomer(id));
        } catch (RuntimeException failure) {
            messageLabel.setText("No se eliminó el cliente: " + safeMessage(failure));
            return;
        }
        if (id.equals(editingId)) resetForm();
        refreshAfterWrite("Cliente eliminado.");
    }

    private Customer selectedCustomer() {
        Customer customer = customersTable.getSelectionModel().getSelectedItem();
        if (customer == null) throw new IllegalArgumentException("Seleccione un cliente de la tabla.");
        return customer;
    }

    private void showAction(Button button, Permission permission) {
        boolean allowed = authorizationService.hasPermission(session, permission);
        button.setVisible(allowed);
        button.setManaged(allowed);
    }

    private void resetForm() {
        editingId = null;
        nameField.clear();
        phoneField.clear();
        addressField.clear();
        registerCustomerButton.setText("Registrar cliente");
        registerCustomerButton.setDisable(!authorizationService.hasPermission(session, Permission.REGISTER_CUSTOMER));
        cancelEditButton.setVisible(false);
        cancelEditButton.setManaged(false);
    }

    private void refreshAfterWrite(String confirmation) {
        try {
            showCustomers(authorizationService.execute(session, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers));
            messageLabel.setText(confirmation);
        } catch (RuntimeException failure) {
            messageLabel.setText(confirmation + " No se pudo actualizar la lista.");
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
