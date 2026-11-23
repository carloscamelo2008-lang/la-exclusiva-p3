package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Seller;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
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
    private UserSession session;
    private AuthorizationService authorizationService = new AuthorizationService();

    /** Inyecta la autorización respaldada por la identidad de la aplicación. */
    public void setAuthorizationService(AuthorizationService authorizationService) {
        if (authorizationService == null) throw new IllegalArgumentException("La autorización es obligatoria.");
        this.authorizationService = authorizationService;
    }

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        phoneColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhone()));
        addressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getAddress()));
    }

    /** Reemplaza las filas visibles por los vendedores obtenidos del servicio configurado. */
    public void showSellers(List<Seller> sellers) {
        requirePermission(Permission.VIEW_SELLERS, "No tiene permiso para consultar vendedores.");
        sellersTable.getItems().setAll(sellers);
    }

    /** Configura el servicio y deriva la visibilidad y autorización del alta de la sesión activa.
     *
     * @param service servicio de vendedores
     * @param session sesión autenticada
     * @throws IllegalArgumentException si falta el servicio o la sesión
     */
    public void configure(SellerService service, UserSession session) {
        if (service == null) throw new IllegalArgumentException("El servicio de vendedores es obligatorio.");
        if (session == null) throw new IllegalArgumentException("La sesión es obligatoria.");
        sellerService = service;
        this.session = session;
        boolean canRegister = authorizationService.hasPermission(session, Permission.REGISTER_SELLERS);
        registrationPane.setVisible(canRegister);
        registrationPane.setManaged(canRegister);
    }

    @FXML
    private void handleRegisterSeller() {
        try {
            requirePermission(Permission.REGISTER_SELLERS, "No tiene permiso para registrar vendedores.");
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

    private void requirePermission(Permission permission, String message) {
        if (!authorizationService.hasPermission(session, permission)) throw new SecurityException(message);
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Error inesperado." : failure.getMessage();
    }
}
