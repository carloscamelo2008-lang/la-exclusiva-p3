package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.SellerRepository;
import com.laexclusiva.ui.App;
import com.laexclusiva.ui.controller.MainController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URL;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceTest {

    @BeforeAll
    public static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit de JavaFX ya activo
        }
        Platform.setImplicitExit(false);
    }

    @Test
    public void testAppDemoAccountsInitializationMechanism() {
        App app = new App();
        app.initializeDemoAccounts("passAdmin123", "passWorker123");

        AuthenticationService authService = new AuthenticationService(app.getUserRepository());
        assertTrue(authService.authenticate("admin", "passAdmin123").isPresent(), "Admin demo debe autenticarse correctamente");
        assertTrue(authService.authenticate("worker", "passWorker123").isPresent(), "Worker demo debe autenticarse correctamente");
    }

    @Test
    public void testAppDemoAccountsInitializationFailsWithoutPasswords() {
        App app = new App();
        assertThrows(IllegalStateException.class, () -> app.initializeDemoAccounts(null, "passWorker123"),
                "Debe fallar si falta la clave del administrador demo");
    }

    @Test
    public void testInitialAdminProvisioningSuccess() {
        App app = new App();
        app.initializeInitialAdmin("adminSecret123");

        AuthenticationService authService = new AuthenticationService(app.getUserRepository());
        assertTrue(authService.authenticate("admin", "adminSecret123").isPresent(), "El administrador inicial debe poder autenticarse");
    }

    @Test
    public void testInitialAdminProvisioningFailsWithoutPassword() {
        App app = new App();
        assertThrows(IllegalStateException.class, () -> app.initializeInitialAdmin(""),
                "Debe exigir LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD en el arranque normal");
    }

    @Test
    public void testFxmlViewsLoadingWithFXMLLoader() {
        assertDoesNotThrow(() -> {
            URL loginUrl = getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml");
            assertNotNull(loginUrl, "login_view.fxml debe existir");
            FXMLLoader loginLoader = new FXMLLoader(loginUrl);
            loginLoader.load();
        });

        assertDoesNotThrow(() -> {
            URL menuUrl = getClass().getResource("/com/laexclusiva/ui/fxml/main_menu_view.fxml");
            assertNotNull(menuUrl, "main_menu_view.fxml debe existir");
            FXMLLoader menuLoader = new FXMLLoader(menuUrl);
            menuLoader.load();
        });
    }

    @ParameterizedTest
    @CsvSource({"ADMINISTRATOR, true", "WORKER, false"})
    void testMainMenuButtonPermissions(UserRole role, boolean expectedVisibility) throws Exception {
        FutureTask<Void> checkMenu = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            loader.load();
            MainController controller = loader.getController();
            UserSession session = new UserSession(new UserAccount(1L, "test", "unused", role, null));
            controller.initSession(session);

            Button sellers = (Button) loader.getNamespace().get("btnSellers");
            Button adminUsers = (Button) loader.getNamespace().get("btnAdminUsers");
            assertAll(
                    () -> assertEquals(expectedVisibility, sellers.isVisible(), "Visibilidad de Vendedores"),
                    () -> assertEquals(expectedVisibility, sellers.isManaged(), "Espacio de Vendedores"),
                    () -> assertEquals(expectedVisibility, adminUsers.isVisible(), "Visibilidad de Gestión de Usuarios"),
                    () -> assertEquals(expectedVisibility, adminUsers.isManaged(), "Espacio de Gestión de Usuarios"),
                    () -> assertTrue(adminUsers.isDisabled(), "Gestión de Usuarios sigue pendiente")
            );
            return null;
        });
        Platform.runLater(checkMenu);
        checkMenu.get(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void testCustomerNavigationPreservesMenuAndSession(UserRole role) throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository());
        customers.registerCustomer("C-01", "Ana Pérez", "3001234567", "Calle 10");

        withMainMenu(role, customers, (menu, account) -> {
            Node dashboard = menu.getCenter();
            Node sidebar = menu.getLeft();
            Button customersButton = (Button) sidebar.lookup("#btnCustomers");
            Button dashboardButton = (Button) sidebar.lookup("#btnDashboard");

            customersButton.fire();
            TableView<?> table = (TableView<?>) menu.getCenter().lookup("#customersTable");
            assertNotNull(table);
            assertEquals(1, table.getItems().size());
            assertEquals(List.of("C-01", "Ana Pérez", "3001234567", "Calle 10"),
                    table.getColumns().stream().map(column -> column.getCellData(0)).toList());
            assertSame(sidebar, menu.getLeft());

            dashboardButton.fire();
            assertSame(dashboard, menu.getCenter());
            assertSame(sidebar, menu.getLeft());

            customersButton.fire();
            assertNotNull(menu.getCenter().lookup("#customersTable"));
            Node customerView = menu.getCenter();
            account.setActive(false);
            customersButton.fire();
            assertSame(customerView, menu.getCenter());
        });
    }

    @Test
    void testCustomerNavigationShowsEmptyList() throws Exception {
        withMainMenu(UserRole.WORKER, new CustomerService(new CustomerRepository()), (menu, account) -> {
            ((Button) menu.getLeft().lookup("#btnCustomers")).fire();
            TableView<?> table = (TableView<?>) menu.getCenter().lookup("#customersTable");
            assertNotNull(table);
            assertTrue(table.getItems().isEmpty());
            assertEquals("No hay clientes registrados.", ((Label) table.getPlaceholder()).getText());
        });
    }

    @Test
    void testInactiveSessionDoesNotQueryCustomers() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository() {
            @Override
            public List<Customer> findAll() {
                fail("Una sesión inactiva no debe consultar el repositorio.");
                return List.of();
            }
        });
        withMainMenu(UserRole.ADMINISTRATOR, customers, (menu, account) -> {
            account.setActive(false);
            Node dashboard = menu.getCenter();
            ((Button) menu.getLeft().lookup("#btnCustomers")).fire();
            assertSame(dashboard, menu.getCenter());
            assertEquals("No tiene permiso para consultar clientes.", ((Label) menu.getBottom()).getText());
        });
    }

    @Test
    void testCustomerQueryFailureKeepsDashboardUsable() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository() {
            @Override
            public List<Customer> findAll() {
                throw new IllegalStateException("Fallo simulado de consulta.");
            }
        });
        withMainMenu(UserRole.WORKER, customers, (menu, account) -> {
            Node dashboard = menu.getCenter();
            ((Button) menu.getLeft().lookup("#btnCustomers")).fire();
            assertSame(dashboard, menu.getCenter());
            Label message = (Label) menu.getBottom();
            assertEquals("No se pudo consultar la lista de clientes.", message.getText());

            ((Button) menu.getLeft().lookup("#btnDashboard")).fire();
            assertTrue(message.getText().isEmpty());
        });
    }

    @Test
    void testAdministratorCanListSellersAndReturnToDashboard() throws Exception {
        SellerService sellers = new SellerService(new SellerRepository());
        Seller seller = sellers.registerSeller("Ana Pérez", "3001234567", "Calle 10");

        withMainMenu(UserRole.ADMINISTRATOR, null, sellers, (menu, account) -> {
            Node dashboard = menu.getCenter();
            Node sidebar = menu.getLeft();
            Button sellersButton = (Button) sidebar.lookup("#btnSellers");
            assertTrue(sellersButton.isVisible());
            sellersButton.fire();

            TableView<?> table = (TableView<?>) menu.getCenter().lookup("#sellersTable");
            assertNotNull(table);
            assertFalse(table.isEditable());
            assertEquals(1, table.getItems().size());
            assertEquals(List.of(seller.getId(), "Ana Pérez", "3001234567", "Calle 10"),
                    table.getColumns().stream().map(column -> column.getCellData(0)).toList());
            assertSame(sidebar, menu.getLeft());

            ((Button) sidebar.lookup("#btnDashboard")).fire();
            assertSame(dashboard, menu.getCenter());
            assertSame(sidebar, menu.getLeft());
            sellersButton.fire();
            assertNotNull(menu.getCenter().lookup("#sellersTable"));
        });
    }

    @ParameterizedTest
    @CsvSource({"WORKER, true", "ADMINISTRATOR, false"})
    void testUnauthorizedSessionDoesNotQuerySellers(UserRole role, boolean active) throws Exception {
        SellerService sellers = new SellerService(new SellerRepository() {
            @Override
            public List<Seller> findAll() {
                return fail("Una sesión sin permiso no debe consultar vendedores.");
            }
        });

        withMainMenu(role, null, sellers, (menu, account) -> {
            account.setActive(active);
            Node dashboard = menu.getCenter();
            Button sellersButton = (Button) menu.getLeft().lookup("#btnSellers");
            sellersButton.fire();
            assertSame(dashboard, menu.getCenter());
            assertEquals("No tiene permiso para consultar vendedores.", ((Label) menu.getBottom()).getText());
        });
    }

    @Test
    void testSellerNavigationShowsEmptyList() throws Exception {
        SellerService sellers = new SellerService(new SellerRepository());
        withMainMenu(UserRole.ADMINISTRATOR, null, sellers, (menu, account) -> {
            ((Button) menu.getLeft().lookup("#btnSellers")).fire();
            TableView<?> table = (TableView<?>) menu.getCenter().lookup("#sellersTable");
            assertNotNull(table);
            assertTrue(table.getItems().isEmpty());
            assertEquals("No hay vendedores registrados", ((Label) table.getPlaceholder()).getText());
        });
    }

    @Test
    void testSellerQueryFailureKeepsCustomerNavigationUsable() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository());
        SellerService sellers = new SellerService(new SellerRepository() {
            @Override
            public List<Seller> findAll() {
                throw new IllegalStateException("Fallo simulado de consulta de vendedores.");
            }
        });
        withMainMenu(UserRole.ADMINISTRATOR, customers, sellers, (menu, account) -> {
            Node dashboard = menu.getCenter();
            Node sidebar = menu.getLeft();
            Button customersButton = (Button) sidebar.lookup("#btnCustomers");
            customersButton.fire();
            Node customersView = menu.getCenter();
            assertNotNull(customersView.lookup("#customersTable"));

            ((Button) sidebar.lookup("#btnSellers")).fire();
            assertSame(customersView, menu.getCenter());
            Label message = (Label) menu.getBottom();
            assertEquals("No se pudo consultar la lista de vendedores.", message.getText());

            ((Button) sidebar.lookup("#btnDashboard")).fire();
            assertSame(dashboard, menu.getCenter());
            assertTrue(message.getText().isEmpty());
            customersButton.fire();
            assertNotNull(menu.getCenter().lookup("#customersTable"));
        });
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void testPortfolioShowsOnlyPositiveBalancesAndPreservesSession(UserRole role) throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository());
        customers.registerCustomer("C-01", "Ana Pérez", "3001234567", "Calle 10");
        customers.registerCustomer("C-02", "Luis Rojas", "3001234568", "Calle 11");
        BigDecimal balance = new BigDecimal("12345.67");
        customers.addDebtToCustomer("C-01", balance);

        withMainMenu(role, customers, (menu, account) -> {
            Node dashboard = menu.getCenter();
            Node sidebar = menu.getLeft();
            Button portfolioButton = (Button) sidebar.lookup("#btnCredit");
            portfolioButton.fire();

            TableView<?> table = (TableView<?>) menu.getCenter().lookup("#portfolioTable");
            assertNotNull(table);
            assertFalse(table.isEditable());
            assertEquals(1, table.getItems().size());
            assertEquals(List.of("C-01", "Ana Pérez", balance),
                    table.getColumns().stream().map(column -> column.getCellData(0)).toList());
            assertSame(sidebar, menu.getLeft());

            ((Button) sidebar.lookup("#btnDashboard")).fire();
            assertSame(dashboard, menu.getCenter());
            assertSame(sidebar, menu.getLeft());
            portfolioButton.fire();
            assertNotNull(menu.getCenter().lookup("#portfolioTable"));
        });
    }

    @Test
    void testPortfolioShowsEmptyStateWhenNoBalancesArePositive() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository());
        customers.registerCustomer("C-01", "Ana Pérez", "3001234567", "Calle 10");
        withMainMenu(UserRole.WORKER, customers, (menu, account) -> {
            ((Button) menu.getLeft().lookup("#btnCredit")).fire();
            TableView<?> table = (TableView<?>) menu.getCenter().lookup("#portfolioTable");
            assertNotNull(table);
            assertTrue(table.getItems().isEmpty());
            assertEquals("No hay clientes con saldo pendiente", ((Label) table.getPlaceholder()).getText());
        });
    }

    @Test
    void testInactiveSessionDoesNotQueryPortfolio() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository() {
            @Override
            public List<Customer> findAll() {
                return fail("Una sesión inactiva no debe consultar la cartera.");
            }
        });
        withMainMenu(UserRole.ADMINISTRATOR, customers, (menu, account) -> {
            account.setActive(false);
            Node dashboard = menu.getCenter();
            ((Button) menu.getLeft().lookup("#btnCredit")).fire();
            assertSame(dashboard, menu.getCenter());
            assertEquals("No tiene permiso para consultar la cartera.", ((Label) menu.getBottom()).getText());
        });
    }

    @Test
    void testPortfolioQueryFailureKeepsCurrentPanelAndMenuUsable() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository() {
            @Override
            public List<Customer> findAll() {
                throw new IllegalStateException("Fallo simulado de consulta de cartera.");
            }
        });
        SellerService sellers = new SellerService(new SellerRepository());
        withMainMenu(UserRole.ADMINISTRATOR, customers, sellers, (menu, account) -> {
            Node dashboard = menu.getCenter();
            Node sidebar = menu.getLeft();
            ((Button) sidebar.lookup("#btnSellers")).fire();
            Node sellersView = menu.getCenter();
            assertNotNull(sellersView.lookup("#sellersTable"));

            ((Button) sidebar.lookup("#btnCredit")).fire();
            assertSame(sellersView, menu.getCenter());
            assertSame(sidebar, menu.getLeft());
            Label message = (Label) menu.getBottom();
            assertEquals("No se pudo consultar la cartera.", message.getText());

            ((Button) sidebar.lookup("#btnDashboard")).fire();
            assertSame(dashboard, menu.getCenter());
            assertTrue(message.getText().isEmpty());
        });
    }

    @Test
    void testNavigationHighlightChangesOnlyAfterSuccessfulNavigation() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository());
        SellerService sellers = new SellerService(new SellerRepository());
        withMainMenu(UserRole.ADMINISTRATOR, customers, sellers, (menu, account) -> {
            assertActiveNavigation(menu, "btnDashboard");
            ((Button) menu.lookup("#btnCustomers")).fire();
            assertActiveNavigation(menu, "btnCustomers");
            ((Button) menu.lookup("#btnSellers")).fire();
            assertActiveNavigation(menu, "btnSellers");
            ((Button) menu.lookup("#btnCredit")).fire();
            assertActiveNavigation(menu, "btnCredit");
            ((Button) menu.lookup("#btnDashboard")).fire();
            assertActiveNavigation(menu, "btnDashboard");
        });
    }

    @Test
    void testQueryFailuresAndDeniedActionsPreserveSelectionAndPanel() throws Exception {
        CustomerService failingCustomers = new CustomerService(new CustomerRepository() {
            @Override
            public List<Customer> findAll() {
                throw new IllegalStateException("Consulta no disponible.");
            }
        });
        withMainMenu(UserRole.ADMINISTRATOR, failingCustomers,
                new SellerService(new SellerRepository()), (menu, account) -> {
            ((Button) menu.lookup("#btnSellers")).fire();
            Node sellersView = menu.getCenter();
            ((Button) menu.lookup("#btnCustomers")).fire();
            assertSame(sellersView, menu.getCenter());
            assertActiveNavigation(menu, "btnSellers");
            ((Button) menu.lookup("#btnCredit")).fire();
            assertSame(sellersView, menu.getCenter());
            assertActiveNavigation(menu, "btnSellers");
            account.setActive(false);
            ((Button) menu.lookup("#btnDashboard")).fire();
            assertSame(sellersView, menu.getCenter());
            assertActiveNavigation(menu, "btnSellers");
        });
    }

    @Test
    void testSellerFailurePreservesCustomerSelection() throws Exception {
        SellerService failingSellers = new SellerService(new SellerRepository() {
            @Override
            public List<Seller> findAll() {
                throw new IllegalStateException("Consulta no disponible.");
            }
        });
        withMainMenu(UserRole.ADMINISTRATOR, new CustomerService(new CustomerRepository()),
                failingSellers, (menu, account) -> {
            ((Button) menu.lookup("#btnCustomers")).fire();
            Node customerView = menu.getCenter();
            ((Button) menu.lookup("#btnSellers")).fire();
            assertSame(customerView, menu.getCenter());
            assertActiveNavigation(menu, "btnCustomers");
        });
    }

    @Test
    void testLogoutInvalidatesSessionAndBlocksOldController() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            BorderPane menu = loader.load();
            MainController controller = loader.getController();
            UserAccount account = new UserAccount(1L, "admin", "unused", UserRole.ADMINISTRATOR, null);
            UserSession session = new UserSession(account);
            CustomerService customers = new CustomerService(new CustomerRepository() {
                @Override
                public List<Customer> findAll() {
                    return fail("El controlador anterior no debe consultar después de cerrar sesión.");
                }
            });
            controller.setCustomerService(customers);
            controller.initSession(session);
            boolean[] returnedToLogin = {false};
            controller.setOnLogout(() -> returnedToLogin[0] = true);
            ((Button) menu.lookup("#btnLogout")).fire();
            assertTrue(returnedToLogin[0]);
            assertFalse(session.isActive());
            assertTrue(account.isActive());
            AuthorizationService authorization = new AuthorizationService();
            assertFalse(authorization.isAdministrator(session));
            assertFalse(authorization.hasRole(session, UserRole.ADMINISTRATOR));
            assertFalse(authorization.hasPermission(session, com.laexclusiva.model.Permission.VIEW_CUSTOMER));
            ((Button) menu.lookup("#btnCustomers")).fire();
            assertEquals("No tiene permiso para consultar clientes.", ((Label) menu.getBottom()).getText());
            assertThrows(IllegalArgumentException.class, () -> controller.initSession(session));
            return null;
        });
    }

    @Test
    void testLogoutFailurePreservesActiveSession() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            BorderPane menu = loader.load();
            MainController controller = loader.getController();
            UserSession session = new UserSession(new UserAccount(
                    1L, "admin", "unused", UserRole.ADMINISTRATOR, null));
            controller.initSession(session);
            controller.setOnLogout(() -> { throw new IllegalStateException("Carga no disponible."); });
            ((Button) menu.lookup("#btnLogout")).fire();
            assertTrue(session.isActive());
            assertActiveNavigation(menu, "btnDashboard");
            assertEquals("No se pudo volver al inicio de sesión. Intente de nuevo.",
                    ((Label) menu.getBottom()).getText());
            return null;
        });
    }

    @Test
    void testAppCanLoginLogoutAndChangeUserInTheSameWindow() throws Exception {
        onFxThread(() -> {
            App app = new App();
            app.initializeDemoAccounts("Admin-Test-2026", "Worker-Test-2026");
            Stage stage = new Stage();
            try {
                app.start(stage);
                Scene loginScene = stage.getScene();
                ((Button) loginScene.lookup("#loginButton")).fire();
                assertEquals("Por favor, ingrese usuario y contraseña.",
                        ((Label) loginScene.lookup("#errorLabel")).getText());
                enterLogin(stage, "admin", "incorrecta");
                assertSame(loginScene, stage.getScene());
                assertEquals("Credenciales inválidas o cuenta inactiva.",
                        ((Label) loginScene.lookup("#errorLabel")).getText());
                enterLogin(stage, "admin", "Admin-Test-2026");
                BorderPane adminMenu = (BorderPane) stage.getScene().getRoot();
                assertTrue(adminMenu.lookup("#btnSellers").isVisible());
                assertTrue(adminMenu.lookup("#btnAdminUsers").isDisabled());
                ((Button) adminMenu.lookup("#btnCustomers")).fire();
                stage.setMaximized(true);
                ((Button) adminMenu.lookup("#btnLogout")).fire();
                assertTrue(stage.isShowing());
                assertFalse(stage.isMaximized());
                assertNotSame(loginScene, stage.getScene());
                assertTrue(((TextField) stage.getScene().lookup("#usernameField")).getText().isEmpty());
                assertTrue(((PasswordField) stage.getScene().lookup("#passwordField")).getText().isEmpty());
                enterLogin(stage, "worker", "Worker-Test-2026");
                BorderPane workerMenu = (BorderPane) stage.getScene().getRoot();
                assertNotSame(adminMenu, workerMenu);
                assertFalse(workerMenu.lookup("#btnSellers").isVisible());
                assertFalse(workerMenu.lookup("#btnSellers").isManaged());
                assertFalse(workerMenu.lookup("#btnAdminUsers").isVisible());
                assertEquals("¡Bienvenido, worker!", ((Label) workerMenu.lookup("#welcomeLabel")).getText());
                ((Button) workerMenu.lookup("#btnCustomers")).fire();
                assertActiveNavigation(workerMenu, "btnCustomers");
                ((Button) workerMenu.lookup("#btnCredit")).fire();
                assertActiveNavigation(workerMenu, "btnCredit");
                ((Button) workerMenu.lookup("#btnDashboard")).fire();
                assertActiveNavigation(workerMenu, "btnDashboard");
                assertEquals("¡Bienvenido, worker!", ((Label) workerMenu.lookup("#welcomeLabel")).getText());
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void testReauthenticationCreatesANewSessionAfterInvalidation() {
        App app = new App();
        app.initializeDemoAccounts("Admin-Test-2026", "Worker-Test-2026");
        AuthenticationService auth = new AuthenticationService(app.getUserRepository());
        UserSession first = auth.createSession("admin", "Admin-Test-2026").orElseThrow();
        first.invalidate();
        UserSession second = auth.createSession("admin", "Admin-Test-2026").orElseThrow();
        assertNotSame(first, second);
        assertFalse(first.isActive());
        assertTrue(second.isActive());
    }

    private static void enterLogin(Stage stage, String username, String password) {
        ((TextField) stage.getScene().lookup("#usernameField")).setText(username);
        ((PasswordField) stage.getScene().lookup("#passwordField")).setText(password);
        ((Button) stage.getScene().lookup("#loginButton")).fire();
    }

    private static void assertActiveNavigation(BorderPane menu, String selectedId) {
        PseudoClass active = PseudoClass.getPseudoClass("active");
        for (String id : List.of("btnDashboard", "btnCustomers", "btnSellers", "btnCredit")) {
            assertEquals(id.equals(selectedId), menu.lookup("#" + id).getPseudoClassStates().contains(active), id);
        }
    }

    private static void onFxThread(java.util.concurrent.Callable<Void> check) throws Exception {
        FutureTask<Void> task = new FutureTask<>(check);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    private void withMainMenu(UserRole role, CustomerService customers,
                              BiConsumer<BorderPane, UserAccount> check) throws Exception {
        withMainMenu(role, customers, null, check);
    }

    private void withMainMenu(UserRole role, CustomerService customers, SellerService sellers,
                              BiConsumer<BorderPane, UserAccount> check) throws Exception {
        FutureTask<Void> checkMenu = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            BorderPane menu = loader.load();
            MainController controller = loader.getController();
            UserAccount account = new UserAccount(1L, "test", "unused", role, null);
            controller.setCustomerService(customers);
            controller.setSellerService(sellers);
            controller.initSession(new UserSession(account));
            check.accept(menu, account);
            return null;
        });
        Platform.runLater(checkMenu);
        checkMenu.get(10, TimeUnit.SECONDS);
    }
}
