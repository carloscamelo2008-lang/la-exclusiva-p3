package com.laexclusiva.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.UserRole;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.repository.SellerRepository;
import com.laexclusiva.service.SellerService;
import com.laexclusiva.service.UserAdministrationBoundary;
import com.laexclusiva.service.UserManagementService;
import com.laexclusiva.testutil.IdentityTestFixture;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Comprueba que la administración no filtre fallos de infraestructura a la interfaz. */
class UserManagementControllerSecurityTest {
    private static final String INTERNAL_MARKER =
            "jdbc:postgresql SQLSTATE=28000 password=secret hash=private-detail";

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // JavaFX ya fue iniciado por otra prueba.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void hidesInternalJdbcDetailsAndKeepsKnownValidationActionable() throws Exception {
        onFxThread(() -> {
            IdentityTestFixture identity = new IdentityTestFixture();
            var admin = identity.issue(UserRole.ADMINISTRATOR);
            FailingBoundary boundary = new FailingBoundary();
            SellerService sellers = new SellerService(new SellerRepository());
            UserManagementService users = new UserManagementService(identity.users(), boundary,
                    identity.authorization(), sellers, identity.authentication());
            FXMLLoader loader = new FXMLLoader(UserManagementControllerSecurityTest.class.getResource(
                    "/com/laexclusiva/ui/fxml/user_management_view.fxml"));
            loader.load();
            ((UserManagementController) loader.getController()).configure(users, sellers, admin.session(),
                    identity.authorization());

            ((TextField) loader.getNamespace().get("usernameField")).setText("nuevo-operador");
            ((PasswordField) loader.getNamespace().get("passwordField")).setText("Secure-Password-2026!");
            boundary.failNext = true;
            button(loader, "Crear cuenta").fire();
            String internal = ((Label) loader.getNamespace().get("messageLabel")).getText();
            assertTrue(internal.contains("No se pudo completar la operación"), internal);
            assertFalse(internal.contains("jdbc"), internal);
            assertFalse(internal.contains("SQLSTATE"), internal);
            assertFalse(internal.contains("secret"), internal);
            assertFalse(internal.contains("private-detail"), internal);
            assertTrue(((PasswordField) loader.getNamespace().get("passwordField")).getText().isEmpty());

            ((TextField) loader.getNamespace().get("usernameField")).setText("nuevo-operador");
            ((PasswordField) loader.getNamespace().get("passwordField")).setText("Secure-Password-2026!");
            boundary.emitUntrustedValidation = true;
            button(loader, "Crear cuenta").fire();
            String untrustedValidation = ((Label) loader.getNamespace().get("messageLabel")).getText();
            assertTrue(untrustedValidation.contains("No se pudo completar la operación"), untrustedValidation);
            assertFalse(untrustedValidation.contains("private-detail"), untrustedValidation);

            ((TextField) loader.getNamespace().get("usernameField")).setText("   ");
            ((PasswordField) loader.getNamespace().get("passwordField")).setText("Secure-Password-2026!");
            button(loader, "Crear cuenta").fire();
            String validation = ((Label) loader.getNamespace().get("messageLabel")).getText();
            assertTrue(validation.contains("nombre de usuario"), validation);
            assertFalse(validation.contains("No se pudo completar la operación"), validation);
            return null;
        });
    }

    private static <T> T onFxThread(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private static Button button(FXMLLoader loader, String text) {
        Parent root = loader.getRoot();
        Parent content = root instanceof javafx.scene.control.ScrollPane pane ? (Parent) pane.getContent() : root;
        return content.lookupAll(".button").stream()
                .map(Button.class::cast)
                .filter(candidate -> text.equals(candidate.getText()))
                .findFirst().orElseThrow();
    }

    private static final class FailingBoundary implements UserAdministrationBoundary {
        private boolean failNext;
        private boolean emitUntrustedValidation;

        @Override
        public <T> T execute(Supplier<T> operation) {
            if (failNext) {
                failNext = false;
                throw new JdbcPersistenceException(INTERNAL_MARKER);
            }
            if (emitUntrustedValidation) {
                emitUntrustedValidation = false;
                throw new UserManagementService.ValidationException(INTERNAL_MARKER);
            }
            return operation.get();
        }
    }
}
