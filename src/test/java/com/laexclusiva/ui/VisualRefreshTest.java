package com.laexclusiva.ui;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.ui.controller.MainController;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Carga real de recursos y navegación en memoria, sin conexiones externas. */
class VisualRefreshTest {
    @BeforeAll
    static void startToolkit() {
        try { Platform.startup(() -> { }); }
        catch (IllegalStateException alreadyStarted) { /* Toolkit compartido por la suite. */ }
        Platform.setImplicitExit(false);
    }

    @Test
    void allViewsLoadWithTheirControllersAndTheme() throws Exception {
        onFx(() -> {
            for (String view : new String[]{"login", "customers", "sellers", "inventory",
                    "sales", "portfolio", "cash", "purchases"}) {
                FXMLLoader loader = loader(view + "_view.fxml");
                Parent root = loader.load();
                assertNotNull(loader.getController(), view);
                assertFalse(root.getStylesheets().isEmpty(), view);
                new Scene(root, view.equals("login") ? 400 : 1100, view.equals("login") ? 370 : 800);
                root.applyCss();
                root.layout();
                snapshot(root, view);
            }
            return null;
        });
    }

    @Test
    void dashboardUsesExistingNavigationAndCurrentSessionValues() throws Exception {
        onFx(() -> {
            FXMLLoader loader = loader("main_menu_modern.fxml");
            BorderPane root = loader.load();
            MainController controller = loader.getController();
            controller.setApplicationServices(ApplicationServices.create("memory", Map.of()));
            controller.initSession(new UserSession(new UserAccount(1L, "visual-admin", "unused",
                    UserRole.ADMINISTRATOR, null)));
            new Scene(root, 1280, 820);
            root.applyCss(); root.layout();
            assertTrue(((Label) loader.getNamespace().get("welcomeLabel")).getText().contains("visual-admin"));
            assertTrue(((Button) loader.getNamespace().get("btnAdminUsers")).isDisabled());
            snapshot(root, "dashboard");
            Object dashboard = root.getCenter();
            ((Button) loader.getNamespace().get("btnCustomers")).fire();
            assertNotSame(dashboard, root.getCenter());
            assertNotNull(root.getCenter().lookup("#customersTable"));
            assertEquals("", ((Label) loader.getNamespace().get("navigationMessage")).getText());
            ((Button) loader.getNamespace().get("btnDashboard")).fire();
            assertSame(dashboard, root.getCenter());
            for (String id : new String[]{"btnSellers", "btnInventory", "btnSales", "btnCredit", "btnCash"}) {
                ((Button) loader.getNamespace().get(id)).fire();
                assertNotSame(dashboard, root.getCenter(), id);
                assertEquals("", ((Label) loader.getNamespace().get("navigationMessage")).getText(), id);
                ((Button) loader.getNamespace().get("btnDashboard")).fire();
            }
            root.resize(780, 600); root.layout();
            snapshot(root, "dashboard-compact");
            return null;
        });
    }

    private static FXMLLoader loader(String name) {
        return new FXMLLoader(VisualRefreshTest.class.getResource("/com/laexclusiva/ui/fxml/" + name));
    }

    @Test
    void customerFormWrapsAtNarrowWidthWithoutChangingInjectedContainer() throws Exception {
        onFx(() -> {
            FXMLLoader loader = loader("customers_view.fxml");
            Parent root = loader.load();
            new Scene(root, 600, 700);
            root.applyCss(); root.layout();
            javafx.scene.layout.HBox registration = (javafx.scene.layout.HBox) loader.getNamespace().get("registrationPane");
            assertTrue(registration.getHeight() > 60, "El formulario debe distribuirse en varias filas");
            Button register = (Button) loader.getNamespace().get("registerCustomerButton");
            assertNotNull(register.getOnAction());
            assertTrue(register.localToScene(register.getBoundsInLocal()).getMaxX() <= 600);
            snapshot(root, "customers-compact");
            return null;
        });
    }

    private static void snapshot(Parent root, String name) throws Exception {
        WritableImage image = root.snapshot(null, null);
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++)
            for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Path directory = Path.of("target", "ui-review");
        Files.createDirectories(directory);
        ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(45, TimeUnit.SECONDS);
    }
}
