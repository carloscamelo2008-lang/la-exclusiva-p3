package com.laexclusiva.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.ui.ApplicationServices;
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
