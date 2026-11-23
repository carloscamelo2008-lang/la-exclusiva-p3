package com.laexclusiva.ui;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.model.Permission;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.ui.controller.MainController;
import com.laexclusiva.ui.controller.LoginController;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.paint.Color;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Carga real de recursos y navegación en memoria, sin conexiones externas. */
class VisualRefreshTest {
    private static final String PASSWORD = "Visual-Review-Password-2026";
    private static final String HASH = PasswordHasher.hash(PASSWORD);
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
            ApplicationServices services = ApplicationServices.create("memory", Map.of());
            UserSession session = issue(services, UserRole.ADMINISTRATOR);
            controller.setApplicationServices(services);
            controller.initSession(session);
            new Scene(root, 1280, 820);
            root.applyCss(); root.layout();
            assertTrue(((Label) loader.getNamespace().get("welcomeLabel")).getText().contains("visual-admin"));
            assertFalse(button(loader, "btnAdminUsers").isDisabled());
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
            button(loader, "btnAdminUsers").fire();
            assertNotNull(root.getCenter().lookup("#usersTable"));
            button(loader, "btnDashboard").fire();
            root.resize(780, 600); root.layout();
            snapshot(root, "dashboard-compact");
            return null;
        });
    }

    @Test
    void quickActionsMatchRealPermissionsAndNavigationStateForEachRole() throws Exception {
        onFx(() -> {
            for (UserRole role : UserRole.values()) {
                ApplicationServices services = ApplicationServices.create("memory", Map.of());
                UserSession session = issue(services, role);
                FXMLLoader loader = loader("main_menu_modern.fxml");
                BorderPane root = loader.load();
                MainController controller = loader.getController();
                controller.setApplicationServices(services);
                controller.initSession(session);
                new Scene(root, 1000, 700); root.applyCss(); root.layout();
                Map<String, Permission> permissions = Map.of("Customers", Permission.VIEW_CUSTOMER,
                        "Credit", Permission.VIEW_PORTFOLIO, "Inventory", Permission.VIEW_INVENTORY,
                        "Sellers", Permission.VIEW_SELLERS, "Cash", Permission.VIEW_CASH,
                        "Sales", Permission.REGISTER_SALE);
                for (var entry : permissions.entrySet()) {
                    Button quick = button(loader, "quick" + entry.getKey());
                    assertEquals(services.authorization().hasPermission(session, entry.getValue()), quick.isVisible(), role + ": " + entry.getKey());
                    assertEquals(quick.isVisible(), quick.isManaged());
                    assertFalse(quick.isDisabled());
                }
                assertEquals(role == UserRole.ADMINISTRATOR, button(loader, "btnAdminUsers").isVisible());
                for (String suffix : new String[]{"Customers", "Credit", "Inventory"}) {
                    button(loader, "quick" + suffix).fire();
                    assertEquals("", ((Label) loader.getNamespace().get("navigationMessage")).getText());
                    assertNotSame(loader.getNamespace().get("dashboard"), root.getCenter());
                    button(loader, "btnDashboard").fire();
                }
                button(loader, "btnCustomers").setVisible(false);
                button(loader, "btnCustomers").setManaged(false);
                assertFalse(button(loader, "quickCustomers").isVisible());
                assertFalse(button(loader, "quickCustomers").isManaged());
                controller.setOnLogout(() -> { });
                button(loader, "btnLogout").fire();
                assertThrows(SecurityException.class, () -> services.identity().sessions().validate(session));
                for (String suffix : permissions.keySet()) assertTrue(button(loader, "quick" + suffix).isDisabled());
                assertNotNull(root.lookup("#sessionClosedMessage"));
            }
            return null;
        });
    }

    @Test
    void revokedSessionCannotUseQuickCustomerNavigation() throws Exception {
        onFx(() -> {
            ApplicationServices services = ApplicationServices.create("memory", Map.of());
            UserSession session = issue(services, UserRole.ADMINISTRATOR);
            FXMLLoader loader = loader("main_menu_modern.fxml");
            BorderPane root = loader.load();
            MainController controller = loader.getController();
            controller.setApplicationServices(services);
            assertThrows(SecurityException.class, () -> controller.initSession(new UserSession(session.getUserAccount())));
            controller.initSession(session);
            Object dashboard = root.getCenter();
            services.identity().authentication().revoke(session);
            button(loader, "quickCustomers").fire();
            assertSame(dashboard, root.getCenter());
            assertEquals("No tiene permiso para consultar clientes.", ((Label) loader.getNamespace().get("navigationMessage")).getText());
            return null;
        });
    }

    @Test
    void modernDashboardKeepsLogoutRecoveryAndCannotReactivateClosedSession() throws Exception {
        onFx(() -> {
            ApplicationServices services = ApplicationServices.create("memory", Map.of());
            UserSession session = issue(services, UserRole.ADMINISTRATOR);
            FXMLLoader loader = loader("main_menu_modern.fxml");
            BorderPane root = loader.load();
            MainController controller = loader.getController();
            controller.setApplicationServices(services); controller.initSession(session);
            controller.setOnLogout(() -> { throw new IllegalStateException("Fallo visual simulado"); });
            button(loader, "btnLogout").fire();
            assertFalse(session.isValid());
            assertNotNull(root.lookup("#retryLoginButton"));
            assertTrue(button(loader, "quickCredit").isDisabled());
            assertThrows(SecurityException.class, () -> controller.initSession(session));
            return null;
        });
    }

    @Test
    void primaryAndReviewDashboardUseTheSameVerifiedMarkup() throws Exception {
        try (var primary = getClass().getResourceAsStream("/com/laexclusiva/ui/fxml/main_menu_view.fxml");
             var review = getClass().getResourceAsStream("/com/laexclusiva/ui/fxml/main_menu_modern.fxml")) {
            assertNotNull(primary); assertNotNull(review);
            assertArrayEquals(primary.readAllBytes(), review.readAllBytes());
        }
    }

    @Test
    void loginValidationAndAuthenticationErrorsRemainReadableWithoutControllerChanges() throws Exception {
        onFx(() -> {
            ApplicationServices services = ApplicationServices.create("memory", Map.of());
            issue(services, UserRole.ADMINISTRATOR);
            FXMLLoader loader = loader("login_view.fxml");
            Parent root = loader.load();
            ((LoginController) loader.getController()).setApplicationServices(services);
            new Scene(root, 400, 370); root.applyCss(); root.layout();
            Label error = (Label) loader.getNamespace().get("errorLabel");
            assertFalse(error.isVisible());
            button(loader, "loginButton").fire();
            assertEquals("Por favor, ingrese usuario y contraseña.", error.getText());
            assertReadable(root, error);
            ((TextField) loader.getNamespace().get("usernameField")).setText("visual-admin");
            ((PasswordField) loader.getNamespace().get("passwordField")).setText("incorrecta");
            button(loader, "loginButton").fire();
            assertEquals("Credenciales inválidas o cuenta inactiva.", error.getText());
            assertReadable(root, error);
            assertEquals(Color.RED, error.getTextFill(), "El inline heredado se conserva y se contrasta con el fondo");
            assertEquals("", ((PasswordField) loader.getNamespace().get("passwordField")).getText());
            snapshot(root, "login-error");
            ((LoginController) loader.getController()).setAuthenticationService(
                    new com.laexclusiva.service.AuthenticationService(services.identity().users()) {
                        @Override public java.util.Optional<UserSession> createSession(String username, String password) {
                            throw new IllegalStateException("Fallo de prueba sin datos sensibles");
                        }
                    });
            ((PasswordField) loader.getNamespace().get("passwordField")).setText(PASSWORD);
            button(loader, "loginButton").fire();
            assertEquals("No fue posible iniciar sesión. Verifique sus credenciales o consulte al administrador.", error.getText());
            assertReadable(root, error);
            return null;
        });
    }

    @Test
    void compactFinancialViewsScrollToAllFormsAndTables() throws Exception {
        onFx(() -> {
            for (String name : new String[]{"inventory", "portfolio"}) {
                FXMLLoader loader = loader(name + "_view.fxml");
                ScrollPane scroll = loader.load();
                new Scene(scroll, 600, 420); scroll.applyCss(); scroll.layout();
                assertTrue(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight());
                assertTrue(scroll.isFitToWidth());
                assertNotNull(loader.getNamespace().get(name.equals("inventory") ? "movementTable" : "generalDebtMovementsTable"));
                scroll.setVvalue(scroll.getVmax()); scroll.layout();
                assertEquals(scroll.getVmax(), scroll.getVvalue());
                snapshot(scroll, name + "-compact");
            }
            return null;
        });
    }

    private static UserSession issue(ApplicationServices services, UserRole role) {
        String name = role == UserRole.ADMINISTRATOR ? "visual-admin" : "visual-worker";
        services.identity().users().save(new UserAccount(null, name, HASH, role, null));
        return services.identity().authentication().createSession(name, PASSWORD).orElseThrow();
    }

    private static Button button(FXMLLoader loader, String id) { return (Button) loader.getNamespace().get(id); }

    private static void assertReadable(Parent root, Label error) {
        root.applyCss(); root.layout();
        assertTrue(error.isVisible()); assertTrue(error.isManaged());
        Color foreground = (Color) error.getTextFill();
        Color background = (Color) error.getBackground().getFills().get(0).getFill();
        double a = luminance(foreground), b = luminance(background);
        assertTrue((Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5, "Contraste mínimo de texto normal");
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }
    private static double linear(double value) { return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4); }

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
