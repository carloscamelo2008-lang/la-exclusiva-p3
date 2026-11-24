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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifica la carga FXML y que los datos de contacto se oculten a una sesión trabajadora. */
class CollectionsControllerFxmlTest {
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
    void savingPhoneConsentClearsEmailFromProfileAndForm() throws Exception {
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
            field(controller, "emailField", javafx.scene.control.TextField.class)
                    .setText("stale@example.test");
            field(controller, "channelCombo", javafx.scene.control.ComboBox.class)
                    .setValue(CollectionChannel.PHONE);
            field(controller, "consentCheck", javafx.scene.control.CheckBox.class).setSelected(true);
            field(controller, "remindersCheck", javafx.scene.control.CheckBox.class).setSelected(true);

            Method save = CollectionsController.class.getDeclaredMethod("handleSaveProfile");
            save.setAccessible(true);
            save.invoke(controller);

            assertNull(services.collectionAssistant().profile(admin, "collection-phone-only").email());
            assertEquals("", field(controller, "emailField", javafx.scene.control.TextField.class).getText());
            return null;
        });
    }

    @Test
    void approvedReminderPreviewFlowsThroughControllerAndRejectsWorkerConfirmation() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String adminPassword = "Collection-Review-Admin-2026!";
            services.identity().bootstrapInitialAdministrator("collection-review-admin", adminPassword);
            var admin = services.identity().authentication().createSession("collection-review-admin", adminPassword)
                    .orElseThrow();
            services.customers().registerCustomer("collection-review-customer", "Cliente ficticio revisión",
                    "3000000788", "Dirección ficticia");
            services.customers().addDebtToCustomer("collection-review-customer", new BigDecimal("12000.00"));
            services.collectionAssistant().saveProfile(admin, "collection-review-customer", "review@example.test",
                    CollectionChannel.EMAIL, true, true, null, null);
            var reminder = services.collectionAssistant().prepareWeeklyReminders(admin).pendingReview().get(0);
            var preview = services.collectionAssistant().previewReminder(admin, reminder.id());

            UserAccount workerAccount = new UserAccount(null, "collection-review-worker",
                    PasswordHasher.hash("Collection-Review-Worker-2026!"), UserRole.WORKER, null);
            services.identity().users().save(workerAccount);
            var worker = services.identity().authentication().createSession("collection-review-worker",
                    "Collection-Review-Worker-2026!").orElseThrow();
            FXMLLoader workerLoader = load();
            CollectionsController workerController = workerLoader.getController();
            workerController.configure(services.collectionAssistant(), services.administrativeAudit(),
                    services.customers().getAllCustomers(), worker, services.authorization());
            assertThrows(SecurityException.class,
                    () -> workerController.deliverConfirmedPreview(reminder.id(), preview.previewToken()));

            FXMLLoader adminLoader = load();
            CollectionsController adminController = adminLoader.getController();
            adminController.configure(services.collectionAssistant(), services.administrativeAudit(),
                    services.customers().getAllCustomers(), admin, services.authorization());
            var result = adminController.deliverConfirmedPreview(reminder.id(), preview.previewToken());
            assertEquals(com.laexclusiva.model.CustomerCollectionReminder.ResultCode.SIMULATED, result.resultCode());
            assertEquals(CustomerCollectionReminder.Status.PENDING_REVIEW, result.status());
            var persisted = services.collectionAssistant().listReminders(admin, null).get(0);
            assertEquals(reminder.id(), persisted.id());
            assertEquals(0, persisted.attemptCount(), "El modo memoria solo simula la entrega y no reclama envío SMTP.");
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

    private static <T> T onFxThread(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
