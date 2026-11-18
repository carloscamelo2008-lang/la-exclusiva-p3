package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Seller;
import com.laexclusiva.service.SellerService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;

/** Presenta el listado de vendedores y permite registrar nuevos según el permiso recibido. */
public class SellerController {

    @FXML private TableView<Seller> sellersTable;
    @FXML private TableColumn<Seller, String> idColumn;
    @FXML private TableColumn<Seller, String> nameColumn;
    @FXML private TableColumn<Seller, String> phoneColumn;
    @FXML private TableColumn<Seller, String> addressColumn;
    @FXML private HBox registrationPane;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private Label messageLabel;

    private SellerService sellerService;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhone()));
        addressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getAddress()));
    }

    /** Reemplaza las filas visibles por los vendedores obtenidos del servicio configurado. */
    public void showSellers(List<Seller> sellers) {
        sellersTable.getItems().setAll(sellers);
    }

    /** Habilita el alta de vendedores únicamente para roles autorizados. */
    public void configure(SellerService service, boolean canRegister) {
        if (service == null) throw new IllegalArgumentException("El servicio de vendedores es obligatorio.");
        sellerService = service;
        registrationPane.setVisible(canRegister);
        registrationPane.setManaged(canRegister);
    }

    @FXML
    private void handleRegisterSeller() {
        try {
            sellerService.registerSeller(nameField.getText(), phoneField.getText(), addressField.getText());
            messageLabel.setText("Vendedor registrado.");
            nameField.clear();
            phoneField.clear();
            addressField.clear();
            showSellers(sellerService.getAllSellers());
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró el vendedor: " + safeMessage(failure));
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Error inesperado." : failure.getMessage();
    }
}
