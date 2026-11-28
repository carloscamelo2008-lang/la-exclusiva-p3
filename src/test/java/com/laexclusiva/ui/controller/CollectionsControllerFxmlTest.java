package com.laexclusiva.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.CollectionChannel;
import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.ui.ApplicationServices;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifica la carga FXML y que los datos de contacto se oculten a una sesión trabajadora. */
class CollectionsControllerFxmlTest {
    @TempDir
    Path reportDirectory;

    @BeforeAll
    static void startJavaFx() {
        try { Platform.startup(() -> { }); }
        catch (IllegalStateException alreadyStarted) { /* Otras pruebas ya iniciaron JavaFX. */ }
        Platform.setImplicitExit(false);
    }

    @Test
    void loadsAndLimitsContactControlsToCurrentAdministrator() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String adminPassword = "Collection-Fxml-Admin-2026!";
            services.identity().bootstrapInitialAdministrator("collection-fxml-admin", adminPassword);
            var admin = services.identity().authentication().createSession("collection-fxml-admin", adminPassword).orElseThrow();
            FXMLLoader adminLoader = load();
            ((CollectionsController) adminLoader.getController()).configure(services.collectionAssistant(),
                    services.administrativeAudit(), services.customers().getAllCustomers(), admin,
                    services.authorization());
            var adminPane = (javafx.scene.layout.VBox) adminLoader.getNamespace().get("administrationPane");
            assertTrue(adminPane.isVisible());
            assertTrue(adminPane.isManaged());

            UserAccount workerAccount = new UserAccount(null, "collection-fxml-worker",
                    PasswordHasher.hash("Collection-Fxml-Worker-2026!"), UserRole.WORKER, null);
            services.identity().users().save(workerAccount);
            var worker = services.identity().authentication().createSession("collection-fxml-worker",
                    "Collection-Fxml-Worker-2026!").orElseThrow();
            FXMLLoader workerLoader = load();
            ((CollectionsController) workerLoader.getController()).configure(services.collectionAssistant(),
                    services.administrativeAudit(), services.customers().getAllCustomers(), worker,
                    services.authorization());
            var workerPane = (javafx.scene.layout.VBox) workerLoader.getNamespace().get("administrationPane");
            assertFalse(workerPane.isVisible());
            assertFalse(workerPane.isManaged());
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesPhoneFollowUpWithoutAnEmailControl() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String adminPassword = "Collection-Phone-Admin-2026!";
            services.identity().bootstrapInitialAdministrator("collection-phone-admin", adminPassword);
            var admin = services.identity().authentication().createSession("collection-phone-admin", adminPassword)
                    .orElseThrow();
            services.customers().registerCustomer("collection-phone-only", "Cliente de teléfono",
                    "3000000789", "Dirección ficticia");

            FXMLLoader loader = load();
            CollectionsController controller = loader.getController();
            controller.configure(services.collectionAssistant(), services.administrativeAudit(),
                    services.customers().getAllCustomers(), admin, services.authorization());
            field(controller, "customerCombo", javafx.scene.control.ComboBox.class)
                    .setValue(services.customers().getCustomerById("collection-phone-only"));
            field(controller, "channelCombo", javafx.scene.control.ComboBox.class)
                    .setValue(CollectionChannel.PHONE);
            field(controller, "consentCheck", javafx.scene.control.CheckBox.class).setSelected(true);
            field(controller, "remindersCheck", javafx.scene.control.CheckBox.class).setSelected(true);

            Method save = CollectionsController.class.getDeclaredMethod("handleSaveProfile");
            save.setAccessible(true);
            save.invoke(controller);

            assertNull(services.collectionAssistant().profile(admin, "collection-phone-only").email());
            assertNull(loader.getNamespace().get("emailField"));
            return null;
        });
    }

    @Test
    void preparesInternalPhoneFollowUpWithoutEmailActions() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String adminPassword = "Collection-Review-Admin-2026!";
            services.identity().bootstrapInitialAdministrator("collection-review-admin", adminPassword);
            var admin = services.identity().authentication().createSession("collection-review-admin", adminPassword)
                    .orElseThrow();
            services.customers().registerCustomer("collection-review-customer", "Cliente ficticio revisión",
                    "3000000788", "Dirección ficticia");
            services.customers().addDebtToCustomer("collection-review-customer", new BigDecimal("12000.00"));
            services.collectionAssistant().saveProfile(admin, "collection-review-customer", null,
                    CollectionChannel.PHONE, true, true, null, null);
            var reminder = services.collectionAssistant().prepareWeeklyReminders(admin).pendingReview().get(0);
            assertEquals(CollectionChannel.PHONE, reminder.channel());
            FXMLLoader adminLoader = load();
            CollectionsController adminController = adminLoader.getController();
            adminController.configure(services.collectionAssistant(), services.administrativeAudit(),
                    services.customers().getAllCustomers(), admin, services.authorization());
            var controls = ((Parent) adminLoader.getRoot()).lookupAll(".button").stream()
                    .map(node -> ((javafx.scene.control.Button) node).getText()).toList();
            assertFalse(controls.stream().anyMatch(text -> text.toLowerCase().contains("correo")
                    || text.toLowerCase().contains("enviar")));
            assertNull(adminLoader.getNamespace().get("emailField"));
            var persisted = services.collectionAssistant().listReminders(admin, null).get(0);
            assertEquals(reminder.id(), persisted.id());
            assertEquals(CustomerCollectionReminder.Status.PENDING_REVIEW, persisted.status());
            assertEquals(0, persisted.attemptCount(), "El seguimiento no intenta entregar correo.");
            return null;
        });
    }

    @Test
    void manualRefreshConfirmsSuccessEvenAfterAnEarlierMessage() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String password = "Collection-Refresh-Admin-2026!";
            services.identity().bootstrapInitialAdministrator("collection-refresh-admin", password);
            var session = services.identity().authentication().createSession("collection-refresh-admin", password)
                    .orElseThrow();
            FXMLLoader loader = load();
            ((CollectionsController) loader.getController()).configure(services.collectionAssistant(),
                    services.administrativeAudit(), services.customers().getAllCustomers(), session,
                    services.authorization());
            var message = (javafx.scene.control.Label) loader.getNamespace().get("messageLabel");
            message.setText("Se guardaron preferencias en la consulta anterior.");

            refreshAlertsButton(loader).fire();

            assertEquals("Tablero actualizado.", message.getText());
            assertTrue(((javafx.scene.control.Label) loader.getNamespace().get("metricsLabel"))
                    .getText().startsWith("Compromisos vencidos:"));
            return null;
        });
    }

    @Test
    void manualRefreshShowsQueryFailureInsteadOfAFalseSuccess() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String password = "Collection-Refresh-Failure-2026!";
            services.identity().bootstrapInitialAdministrator("collection-refresh-failure", password);
            var session = services.identity().authentication().createSession("collection-refresh-failure", password)
                    .orElseThrow();
            FXMLLoader loader = load();
            ((CollectionsController) loader.getController()).configure(services.collectionAssistant(),
                    services.administrativeAudit(), services.customers().getAllCustomers(), session,
                    services.authorization());
            services.identity().authentication().revoke(session);
            var message = (javafx.scene.control.Label) loader.getNamespace().get("messageLabel");
            message.setText("Mensaje anterior.");

            refreshAlertsButton(loader).fire();

            assertTrue(message.getText().startsWith("No se pudo actualizar el tablero."));
            assertFalse(message.getText().contains("Tablero actualizado."));
            assertEquals("Indicadores no disponibles.",
                    ((javafx.scene.control.Label) loader.getNamespace().get("metricsLabel")).getText());
            assertTrue(((javafx.scene.control.TableView<?>) loader.getNamespace().get("recommendationsTable"))
                    .getItems().isEmpty());
            return null;
        });
    }

    @Test
    void savesStatementIntoConfiguredReportsFolderAndShowsExactPath() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String adminPassword = "Collection-Pdf-Admin-2026!";
            services.identity().bootstrapInitialAdministrator("collection-pdf-admin", adminPassword);
            var admin = services.identity().authentication().createSession("collection-pdf-admin", adminPassword)
                    .orElseThrow();
            services.customers().registerCustomer("collection-pdf-customer", "Cliente PDF",
                    "3000000790", "Dirección ficticia");
            services.customers().addDebtToCustomer("collection-pdf-customer", new BigDecimal("12000.00"));
            FXMLLoader loader = load();
            CollectionsController controller = loader.getController();
            controller.configure(services.collectionAssistant(), services.administrativeAudit(),
                    services.customers().getAllCustomers(), admin, services.authorization());
            controller.setReportStore(new com.laexclusiva.service.AccountStatementReportStore(reportDirectory));
            field(controller, "customerCombo", javafx.scene.control.ComboBox.class).getSelectionModel().selectFirst();
            var generate = (javafx.scene.control.Button) loader.getNamespace().get("generateStatementButton");

            generate.fire();

            try (var files = java.nio.file.Files.list(reportDirectory)) {
                Path pdf = files.filter(path -> path.toString().endsWith(".pdf")).findFirst().orElseThrow();
                byte[] bytes = java.nio.file.Files.readAllBytes(pdf);
                assertTrue(bytes.length > 100);
                assertEquals("%PDF-", new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
                assertTrue(((javafx.scene.control.Label) loader.getNamespace().get("messageLabel"))
                        .getText().contains(pdf.toString()));
            }
            return null;
        });
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static FXMLLoader load() throws Exception {
        FXMLLoader loader = new FXMLLoader(CollectionsControllerFxmlTest.class.getResource(
                "/com/laexclusiva/ui/fxml/collections_view.fxml"));
        Parent root = loader.load();
        assertTrue(root.getChildrenUnmodifiable().size() > 0);
        return loader;
    }

    private static javafx.scene.control.Button refreshAlertsButton(FXMLLoader loader) {
        return (javafx.scene.control.Button) loader.getNamespace().get("refreshAlertsButton");
    }

    private static <T> T onFxThread(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
