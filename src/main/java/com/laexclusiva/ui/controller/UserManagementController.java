package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.SellerService;
import com.laexclusiva.service.UserManagementService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/** Administra cuentas mediante el servicio que revalida la sesión y serializa cada operación. */
public final class UserManagementController {
    private static final java.util.Set<String> SAFE_VALIDATION_MESSAGES = java.util.Set.of(
            "Seleccione una cuenta.",
            "Seleccione una asignación de vendedor.",
            "El rol es obligatorio.",
            "Los datos de creación son obligatorios.",
            "El nombre de usuario debe tener entre 1 y 100 caracteres válidos.",
            "El nombre de usuario ya se encuentra registrado.",
            "El ID de vendedor debe tener entre 1 y 32 caracteres válidos.",
            "El vendedor no existe.",
            "El ID del objetivo debe ser un entero positivo.",
            "Se requieren un ID positivo y una versión no negativa.",
            "La cuenta objetivo no existe.",
            "La contraseña debe contener entre 15 y 128 caracteres Unicode válidos.",
            "Elija una contraseña menos común o previsible.");

    @FXML private TableView<UserManagementService.UserSummary> usersTable;
    @FXML private TableColumn<UserManagementService.UserSummary, String> idColumn;
    @FXML private TableColumn<UserManagementService.UserSummary, String> usernameColumn;
    @FXML private TableColumn<UserManagementService.UserSummary, String> roleColumn;
    @FXML private TableColumn<UserManagementService.UserSummary, String> activeColumn;
    @FXML private TableColumn<UserManagementService.UserSummary, String> sellerColumn;
    @FXML private TableColumn<UserManagementService.UserSummary, String> versionColumn;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<UserRole> createRoleCombo;
    @FXML private ComboBox<UserRole> targetRoleCombo;
    @FXML private ComboBox<SellerChoice> sellerCombo;
    @FXML private PasswordField replacementPasswordField;
    @FXML private Label messageLabel;

    private UserManagementService users;
    private SellerService sellers;
    private UserSession session;
    private AuthorizationService authorization;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> text(cell.getValue().id()));
        usernameColumn.setCellValueFactory(cell -> text(cell.getValue().username()));
        roleColumn.setCellValueFactory(cell -> text(cell.getValue().role().name()));
        activeColumn.setCellValueFactory(cell -> text(cell.getValue().active() ? "Activa" : "Inactiva"));
        sellerColumn.setCellValueFactory(cell -> text(cell.getValue().sellerId() == null
                ? "Sin asignar" : cell.getValue().sellerId()));
        versionColumn.setCellValueFactory(cell -> text(cell.getValue().securityVersion()));
        createRoleCombo.getItems().setAll(UserRole.values());
        targetRoleCombo.getItems().setAll(UserRole.values());
        createRoleCombo.setValue(UserRole.WORKER);
        StringConverter<UserRole> roleConverter = new StringConverter<>() {
            @Override public String toString(UserRole role) {
                return role == null ? "" : role == UserRole.ADMINISTRATOR ? "Administrador" : "Trabajador";
            }
            @Override public UserRole fromString(String value) { return null; }
        };
        createRoleCombo.setConverter(roleConverter);
        targetRoleCombo.setConverter(roleConverter);
        sellerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(SellerChoice value) { return value == null ? "" : value.label(); }
            @Override public SellerChoice fromString(String value) { return null; }
        });
    }

    /**
     * Configura el controlador con las instancias compartidas de la aplicación.
     *
     * @param users servicio de administración, autoridad final para lecturas y escrituras
     * @param sellers catálogo usado para asignar vendedores
     * @param session sesión emitida por la composición activa
     * @param authorization autorizador que consulta el estado actual de la cuenta
     */
    public void configure(UserManagementService users, SellerService sellers, UserSession session,
                          AuthorizationService authorization) {
        if (users == null || sellers == null || session == null || authorization == null) {
            throw new IllegalArgumentException("La administración requiere servicios, autorización y sesión.");
        }
        authorization.requireSessionAuthority();
        this.users = users;
        this.sellers = sellers;
        this.session = session;
        this.authorization = authorization;
        authorization.currentPrincipal(session);
        sellerCombo.getItems().setAll(new SellerChoice(null, "Sin asignar"));
        authorization.execute(session, Permission.VIEW_SELLERS, sellers::getAllSellers).stream()
                .map(seller -> new SellerChoice(seller.getId(), seller.getName()))
                .forEach(sellerCombo.getItems()::add);
        sellerCombo.getSelectionModel().selectFirst();
        refreshUsers();
    }

    @FXML
    private void handleRefresh() {
        refreshUsers();
    }

    @FXML
    private void handleCreateUser() {
        String password = passwordField.getText();
        try {
            requireAdministrator();
            users.createUser(session, new UserManagementService.CreateUserRequest(
                    usernameField.getText(), password, createRoleCombo.getValue(), true));
            usernameField.clear();
            messageLabel.setText("Cuenta creada.");
            refreshAfterWrite("Cuenta creada.");
        } catch (RuntimeException failure) {
            messageLabel.setText("No se creó la cuenta: " + safeMessage(failure));
        } finally {
            passwordField.clear();
        }
    }

    @FXML
    private void handleChangeRole() {
        var selected = selectedUser();
        try {
            requireAdministrator();
            if (selected == null) throw new UserManagementService.ValidationException("Seleccione una cuenta.");
            users.changeRole(session, selected.id().toString(), selected.securityVersion(), targetRoleCombo.getValue());
            refreshAfterWrite("Rol actualizado.");
        } catch (RuntimeException failure) {
            messageLabel.setText("No se cambió el rol: " + safeMessage(failure));
        }
    }

    @FXML
    private void handleToggleActive() {
        var selected = selectedUser();
        try {
            requireAdministrator();
            if (selected == null) throw new UserManagementService.ValidationException("Seleccione una cuenta.");
            users.setActive(session, selected.id().toString(), selected.securityVersion(), !selected.active());
            refreshAfterWrite(selected.active() ? "Cuenta desactivada." : "Cuenta activada.");
        } catch (RuntimeException failure) {
            messageLabel.setText("No cambió el estado de la cuenta: " + safeMessage(failure));
        }
    }

    @FXML
    private void handleChangePassword() {
        String password = replacementPasswordField.getText();
        var selected = selectedUser();
        try {
            requireAdministrator();
            if (selected == null) throw new UserManagementService.ValidationException("Seleccione una cuenta.");
            users.changePassword(session, selected.id().toString(), selected.securityVersion(), password);
            refreshAfterWrite("Contraseña actualizada. Las sesiones anteriores deberán volver a iniciar sesión.");
        } catch (RuntimeException failure) {
            messageLabel.setText("No se cambió la contraseña: " + safeMessage(failure));
        } finally {
            replacementPasswordField.clear();
        }
    }

    @FXML
    private void handleAssignSeller() {
        var selected = selectedUser();
        try {
            requireAdministrator();
            if (selected == null) throw new UserManagementService.ValidationException("Seleccione una cuenta.");
            SellerChoice choice = sellerCombo.getValue();
            if (choice == null) throw new UserManagementService.ValidationException(
                    "Seleccione una asignación de vendedor.");
            users.assignSeller(session, selected.id().toString(), selected.securityVersion(), choice.id());
            refreshAfterWrite("Vendedor actualizado.");
        } catch (RuntimeException failure) {
            messageLabel.setText("No se asignó el vendedor: " + safeMessage(failure));
        }
    }

    private void requireAdministrator() {
        if (!authorization.hasPermission(session, Permission.MANAGE_USERS)) {
            throw new SecurityException("La sesión ya no permite administrar usuarios.");
        }
    }

    private void refreshUsers() {
        try {
            requireAdministrator();
            usersTable.getItems().setAll(users.listUsers(session));
            messageLabel.setText("Cuentas actualizadas.");
        } catch (RuntimeException failure) {
            usersTable.getItems().clear();
            messageLabel.setText("No se pudieron consultar las cuentas: " + safeMessage(failure));
        }
    }

    private void refreshAfterWrite(String success) {
        try {
            usersTable.getItems().setAll(users.listUsers(session));
            messageLabel.setText(success);
        } catch (RuntimeException failure) {
            usersTable.getItems().clear();
            messageLabel.setText(success + " No se actualizó la tabla; use Actualizar cuentas. "
                    + safeMessage(failure));
        }
    }

    private UserManagementService.UserSummary selectedUser() {
        return usersTable.getSelectionModel().getSelectedItem();
    }

    private static javafx.beans.property.ReadOnlyStringWrapper text(Object value) {
        return new javafx.beans.property.ReadOnlyStringWrapper(String.valueOf(value));
    }

    private static String safeMessage(Throwable failure) {
        if (failure instanceof UserManagementService.ValidationException validation) {
            return SAFE_VALIDATION_MESSAGES.contains(validation.getMessage())
                    ? validation.getMessage() : "No se pudo completar la operación. Inténtelo más tarde o contacte al administrador.";
        }
        if (failure instanceof SecurityException) {
            return "La sesión ya no está autorizada para administrar usuarios.";
        }
        if (failure instanceof java.util.ConcurrentModificationException) {
            return "La cuenta cambió. Actualice la lista e inténtelo de nuevo.";
        }
        if (failure instanceof IllegalStateException) {
            String knownValidation = switch (failure.getMessage() == null ? "" : failure.getMessage()) {
                case "No se puede retirar al último administrador activo." ->
                        "Debe conservar al menos un administrador activo.";
                case "El vendedor ya está asignado a otra cuenta activa." ->
                        "El vendedor ya está asignado a otra cuenta activa.";
                default -> null;
            };
            if (knownValidation != null) return knownValidation;
        }
        return "No se pudo completar la operación. Inténtelo más tarde o contacte al administrador.";
    }

    private record SellerChoice(String id, String label) { }
}
