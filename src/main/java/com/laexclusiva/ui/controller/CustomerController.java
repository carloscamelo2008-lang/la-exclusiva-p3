package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Customer;
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

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhone()));
        addressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getAddress()));
    }

    /** Reemplaza las filas visibles por los clientes obtenidos del servicio configurado. */
    public void showCustomers(List<Customer> customers) {
        customersTable.getItems().setAll(customers);
    }

    /** Habilita el alta cuando el rol tiene permiso de registro de clientes. */
    public void configure(CustomerService service, boolean canRegister) {
        if (service == null) throw new IllegalArgumentException("El servicio de clientes es obligatorio.");
        customerService = service;
        registrationPane.setVisible(canRegister);
        registrationPane.setManaged(canRegister);
    }

    @FXML
    private void handleRegisterCustomer() {
        try {
            customerService.registerCustomer(idField.getText(), nameField.getText(),
                    phoneField.getText(), addressField.getText());
            messageLabel.setText("Cliente registrado.");
            idField.clear();
            nameField.clear();
            phoneField.clear();
            addressField.clear();
            showCustomers(customerService.getAllCustomers());
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró el cliente: " + safeMessage(failure));
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Error inesperado." : failure.getMessage();
    }
}
