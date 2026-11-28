package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.SellerRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.ui.App;
import com.laexclusiva.ui.ApplicationServices;
import com.laexclusiva.ui.controller.InventoryController;
import com.laexclusiva.ui.controller.MainController;
import com.laexclusiva.ui.controller.SalesController;
import com.laexclusiva.testutil.IdentityTestFixture;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
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
    private IdentityTestFixture currentMenuIdentity;

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "  frase inicial válida", "frase final válida  ", "               ", "old123"})
    void loginControllerPassesTheExactSecretIncludingHistoricalPasswords(String secret) throws Exception {
        onFxThread(() -> {
            var loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
            javafx.scene.Parent root = loader.load();
            // Los skins ScrollPane se construyen al aplicar CSS, como en una ventana real.
            new Scene(root, 400, 370);
            root.applyCss(); root.layout();
            var controller = (com.laexclusiva.ui.controller.LoginController) loader.getController();
            var repository = new com.laexclusiva.repository.InMemoryUserRepository();
            repository.save(new UserAccount(null, "member", com.laexclusiva.security.PasswordHasher.hash(secret),
                    UserRole.WORKER, null));
            String[] captured = new String[2];
            controller.setAuthenticationService(new AuthenticationService(repository) {
                @Override public java.util.Optional<UserSession> createSession(String username, String password) {
                    captured[0] = username;
                    captured[1] = password;
                    assertTrue(authenticate(username, password).isPresent());
                    // No navegar: esta regresión observa la entrada del servicio real de autenticación.
                    return java.util.Optional.empty();
                }
            });
            ((TextField) root.lookup("#usernameField")).setText("  member  ");
            ((PasswordField) root.lookup("#passwordField")).setText(secret);
            ((Button) root.lookup("#loginButton")).fire();
            assertEquals("member", captured[0]);
            assertEquals(secret, captured[1]);
            return null;
        });
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    void loginControllerRejectsMissingPasswordsBeforeCallingService(String secret) throws Exception {
        onFxThread(() -> {
            var loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
            javafx.scene.Parent root = loader.load();
            // Los skins ScrollPane se construyen al aplicar CSS, como en una ventana real.
            new Scene(root, 400, 370);
            root.applyCss(); root.layout();
            var controller = (com.laexclusiva.ui.controller.LoginController) loader.getController();
            controller.setAuthenticationService(new AuthenticationService(null) {
                @Override public java.util.Optional<UserSession> createSession(String username, String password) {
                    fail("No debe consultar autenticación con datos ausentes");
                    return java.util.Optional.empty();
                }
            });
            ((TextField) root.lookup("#usernameField")).setText("member");
            ((PasswordField) root.lookup("#passwordField")).setText(secret);
            ((Button) root.lookup("#loginButton")).fire();
            assertEquals("Por favor, ingrese usuario y contraseña.", ((Label) root.lookup("#errorLabel")).getText());
            return null;
        });
    }

    @Test
    void loginControllerStillRejectsBlankUsername() throws Exception {
        onFxThread(() -> {
            var loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
            javafx.scene.Parent root = loader.load();
            // Los skins ScrollPane se construyen al aplicar CSS, como en una ventana real.
            new Scene(root, 400, 370);
            root.applyCss(); root.layout();
            ((TextField) root.lookup("#usernameField")).setText("   ");
            ((PasswordField) root.lookup("#passwordField")).setText("Una frase permitida");
            ((Button) root.lookup("#loginButton")).fire();
            assertEquals("Por favor, ingrese usuario y contraseña.", ((Label) root.lookup("#errorLabel")).getText());
            return null;
        });
    }

    @Test
    void blockedInitialPasswordLeavesNoAccountAndNoSecretInError() {
        App app = memoryApp();
        var failure = assertThrows(IllegalStateException.class,
                () -> app.initializeInitialAdmin("passwordpassword"));
        assertTrue(app.getUserRepository().findAll().isEmpty());
        assertFalse(failure.getMessage().contains("passwordpassword"));
        assertNull(failure.getCause());
    }

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
    public void testInitialAdminProvisioningSuccess() {
        App app = memoryApp();
        app.initializeInitialAdmin("adminSecret123-Valid-Only");

        AuthenticationService authService = new AuthenticationService(app.getUserRepository());
        assertTrue(authService.authenticate("admin", "adminSecret123-Valid-Only").isPresent(), "El administrador inicial debe poder autenticarse");
    }

    @Test
    public void testInitialAdminProvisioningFailsWithoutPassword() {
        App app = memoryApp();
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

        assertDoesNotThrow(() -> new FXMLLoader(getClass().getResource(
                "/com/laexclusiva/ui/fxml/inventory_view.fxml")).load());
        assertDoesNotThrow(() -> new FXMLLoader(getClass().getResource(
                "/com/laexclusiva/ui/fxml/user_management_view.fxml")).load());
        assertDoesNotThrow(() -> new FXMLLoader(getClass().getResource(
                "/com/laexclusiva/ui/fxml/sales_view.fxml")).load());
        assertDoesNotThrow(() -> new FXMLLoader(getClass().getResource(
                "/com/laexclusiva/ui/fxml/portfolio_view.fxml")).load());
    }

    @Test
    void salePresentationAndPortfolioTextAreHumanReadable() throws Exception {
        FutureTask<Void> checkLabels = new FutureTask<>(() -> {
            FXMLLoader salesLoader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            salesLoader.load();
            @SuppressWarnings("unchecked")
            ComboBox<SalePresentation> presentations = (ComboBox<SalePresentation>)
                    salesLoader.getNamespace().get("presentationCombo");
            assertEquals("Unidad", presentations.getConverter().toString(SalePresentation.UNIT));
            assertEquals("Media canasta", presentations.getConverter().toString(SalePresentation.HALF_BASKET));
            assertEquals("Canasta completa", presentations.getConverter().toString(SalePresentation.FULL_BASKET));

            FXMLLoader portfolioLoader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/portfolio_view.fxml"));
            portfolioLoader.load();
            Label subtitle = (Label) portfolioLoader.getNamespace().get("portfolioSubtitle");
            assertEquals("Consulta de saldos por cliente y registro de abonos a ventas.", subtitle.getText());
            return null;
        });
        Platform.runLater(checkLabels);
        checkLabels.get(10, TimeUnit.SECONDS);
    }

    @Test
    void inventoryScreenRegistersCatalogProductThroughConfiguredService() throws Exception {
        FutureTask<Void> registration = new FutureTask<>(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE,
                    java.util.Map.of());
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/inventory_view.fxml"));
            loader.load();
            InventoryController controller = loader.getController();
            IdentityTestFixture identity = new IdentityTestFixture();
            controller.setAuthorizationService(identity.authorization());
            controller.configure(services.products(), services.inventory(),
                    identity.issue(UserRole.ADMINISTRATOR).session());
            setText(loader, "productIdField", "UI-CATALOG-TEST");
            setText(loader, "productNameField", "Producto de interfaz");
            setText(loader, "volumeField", "330");
            setText(loader, "unitsPerBasketField", "24");
            setText(loader, "unitPriceField", "100");
            setText(loader, "halfBasketPriceField", "1100");
            setText(loader, "fullBasketPriceField", "2100");
            ((Button) loader.getNamespace().get("registerProductButton")).fire();
            assertTrue(services.products().listAll().stream()
                    .anyMatch(product -> product.getId().equals("UI-CATALOG-TEST")));
            return null;
        });
        Platform.runLater(registration);
        registration.get(10, TimeUnit.SECONDS);
    }

    @Test
    void salesScreenRestrictsWorkerToAssignedSeller() throws Exception {
        FutureTask<Void> checkPermissions = new FutureTask<>(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE,
                    java.util.Map.of());
            Seller assignedSeller = services.sellers().registerSeller("Vendedor asignado", "3000000103", "Dirección");
            services.sellers().registerSeller("Otro vendedor", "3000000104", "Dirección");
            IdentityTestFixture identity = new IdentityTestFixture();

            FXMLLoader unassignedLoader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            unassignedLoader.load();
            SalesController unassignedController = unassignedLoader.getController();
            unassignedController.setAuthorizationService(identity.authorization());
            UserSession unassignedWorker = identity.issue(UserRole.WORKER).session();
            unassignedController.configure(services.products(), services.inventory(), services.customers(),
                    services.sellers(), services.sales(), unassignedWorker);
            ComboBox<?> unassignedSellers = (ComboBox<?>) unassignedLoader.getNamespace().get("sellerCombo");
            assertTrue(unassignedSellers.isDisabled());
            assertTrue(unassignedSellers.getItems().isEmpty());

            FXMLLoader assignedLoader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            assignedLoader.load();
            SalesController assignedController = assignedLoader.getController();
            assignedController.setAuthorizationService(identity.authorization());
            UserSession worker = identity.issue("assigned-worker", UserRole.WORKER, assignedSeller.getId()).session();
            assignedController.configure(services.products(), services.inventory(), services.customers(),
                    services.sellers(), services.sales(), worker);
            ComboBox<?> sellerOptions = (ComboBox<?>) assignedLoader.getNamespace().get("sellerCombo");
            assertTrue(sellerOptions.isDisabled());
            assertEquals(1, sellerOptions.getItems().size());
            assertEquals(assignedSeller.getId(), ((Seller) sellerOptions.getItems().get(0)).getId());
            assertEquals(assignedSeller.getId(), ((Seller) sellerOptions.getValue()).getId(),
                    "El vendedor asignado debe quedar seleccionado automáticamente.");

            FXMLLoader unknownLoader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            unknownLoader.load();
            SalesController unknownController = unknownLoader.getController();
            unknownController.setAuthorizationService(identity.authorization());
            UserSession unknownWorker = identity.issue("unknown-worker", UserRole.WORKER, "SELLER-NOT-REGISTERED").session();
            unknownController.configure(services.products(), services.inventory(), services.customers(),
                    services.sellers(), services.sales(), unknownWorker);
            ComboBox<?> unknownSellers = (ComboBox<?>) unknownLoader.getNamespace().get("sellerCombo");
            assertTrue(unknownSellers.isDisabled());
            assertTrue(unknownSellers.getItems().isEmpty());
            assertTrue(((Label) unknownLoader.getNamespace().get("messageLabel")).getText()
                    .contains("no existe"));

            FXMLLoader adminLoader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            adminLoader.load();
            SalesController adminController = adminLoader.getController();
            adminController.setAuthorizationService(identity.authorization());
            adminController.configure(services.products(), services.inventory(), services.customers(),
                    services.sellers(), services.sales(),
                    identity.issue(UserRole.ADMINISTRATOR).session());
            ComboBox<?> adminSellers = (ComboBox<?>) adminLoader.getNamespace().get("sellerCombo");
            assertFalse(adminSellers.isDisabled());
            assertEquals(2, adminSellers.getItems().size(), "El administrador conserva la selección de todos los vendedores.");
            return null;
        });
        Platform.runLater(checkPermissions);
        checkPermissions.get(10, TimeUnit.SECONDS);
    }

    private static void setText(FXMLLoader loader, String fieldId, String value) {
        ((TextField) loader.getNamespace().get(fieldId)).setText(value);
    }

    @ParameterizedTest
    @CsvSource({"ADMINISTRATOR, true", "WORKER, false"})
    void testMainMenuButtonPermissions(UserRole role, boolean expectedVisibility) throws Exception {
        FutureTask<Void> checkMenu = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            loader.load();
            MainController controller = loader.getController();
            IdentityTestFixture identity = new IdentityTestFixture();
            controller.setAuthorizationService(identity.authorization());
            UserSession session = identity.issue(role).session();
            controller.initSession(session);

            Button sellers = (Button) loader.getNamespace().get("btnSellers");
            Button adminUsers = (Button) loader.getNamespace().get("btnAdminUsers");
            assertAll(
                    () -> assertEquals(expectedVisibility, sellers.isVisible(), "Visibilidad de Vendedores"),
                    () -> assertEquals(expectedVisibility, sellers.isManaged(), "Espacio de Vendedores"),
                    () -> assertEquals(expectedVisibility, adminUsers.isVisible(), "Visibilidad de Gestión de Usuarios"),
                    () -> assertEquals(expectedVisibility, adminUsers.isManaged(), "Espacio de Gestión de Usuarios"),
                    () -> assertFalse(adminUsers.isDisabled(), "La gestión se protege en la operación, no se deja deshabilitada")
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
            currentMenuIdentity.update(account);
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
            currentMenuIdentity.update(account);
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
            currentMenuIdentity.update(account);
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

            menu.applyCss(); menu.layout();
            Node portfolioContent = scrollContent(menu.getCenter());
            TableView<?> table = (TableView<?>) portfolioContent.lookup("#portfolioTable");
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
            menu.applyCss(); menu.layout();
            assertNotNull(scrollContent(menu.getCenter()).lookup("#portfolioTable"));
        });
    }

    @Test
    void testPortfolioShowsEmptyStateWhenNoBalancesArePositive() throws Exception {
        CustomerService customers = new CustomerService(new CustomerRepository());
        customers.registerCustomer("C-01", "Ana Pérez", "3001234567", "Calle 10");
        withMainMenu(UserRole.WORKER, customers, (menu, account) -> {
            ((Button) menu.getLeft().lookup("#btnCredit")).fire();
            menu.applyCss(); menu.layout();
            TableView<?> table = (TableView<?>) scrollContent(menu.getCenter()).lookup("#portfolioTable");
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
            currentMenuIdentity.update(account);
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
            currentMenuIdentity.update(account);
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
            new Scene(menu, 1000, 800);
            menu.applyCss();
            menu.layout();
            MainController controller = loader.getController();
            IdentityTestFixture identity = new IdentityTestFixture();
            var issued = identity.issue(UserRole.ADMINISTRATOR);
            UserAccount account = issued.account();
            UserSession session = issued.session();
            CustomerService customers = new CustomerService(new CustomerRepository() {
                @Override
                public List<Customer> findAll() {
                    return fail("El controlador anterior no debe consultar después de cerrar sesión.");
                }
            });
            controller.setCustomerService(customers);
            controller.setAuthorizationService(identity.authorization());
            controller.initSession(session);
            boolean[] returnedToLogin = {false};
            controller.setOnLogout(() -> returnedToLogin[0] = true);
            ((Button) menu.lookup("#btnLogout")).fire();
            assertTrue(returnedToLogin[0]);
            assertNotNull(menu.lookup("#sessionClosedMessage"),
                    "Si el callback no reemplaza la vista, el menú debe seguir indicando sesión cerrada.");
            assertFalse(session.isActive());
            assertTrue(account.isActive());
            AuthorizationService authorization = identity.authorization();
            assertFalse(authorization.isAdministrator(session));
            assertFalse(authorization.hasRole(session, UserRole.ADMINISTRATOR));
            assertFalse(authorization.hasPermission(session, com.laexclusiva.model.Permission.VIEW_CUSTOMER));
            ((Button) menu.lookup("#btnCustomers")).fire();
            assertTrue(((Button) menu.lookup("#btnCustomers")).isDisabled());
            assertThrows(SecurityException.class, () -> authorization.execute(session,
                    com.laexclusiva.model.Permission.VIEW_CUSTOMER,
                    () -> fail("Una sesión revocada no debe consultar clientes.")));
            assertThrows(SecurityException.class, () -> controller.initSession(session));
            return null;
        });
    }

    @Test
    void testLogoutFailureStillRevokesTheSession() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            BorderPane menu = loader.load();
            new Scene(menu, 1000, 800);
            menu.applyCss();
            menu.layout();
            MainController controller = loader.getController();
            IdentityTestFixture identity = new IdentityTestFixture();
            UserSession session = identity.issue(UserRole.ADMINISTRATOR).session();
            AuthorizationService authorization = identity.authorization();
            controller.setAuthorizationService(authorization);
            controller.initSession(session);
            controller.setOnLogout(() -> { throw new IllegalStateException("Carga no disponible."); });
            ((Button) menu.lookup("#btnLogout")).fire();
            assertFalse(session.isActive());
            assertTrue(((Button) menu.lookup("#btnCustomers")).isDisabled());
            assertNotNull(menu.lookup("#sessionClosedMessage"));
            Button retry = (Button) menu.lookup("#retryLoginButton");
            assertNotNull(retry);
            assertThrows(SecurityException.class, () -> authorization.execute(session,
                    com.laexclusiva.model.Permission.VIEW_CUSTOMER,
                    () -> fail("Una sesión revocada no puede recuperar permisos.")));
            boolean[] retried = {false};
            controller.setOnLogout(() -> retried[0] = true);
            retry.fire();
            assertTrue(retried[0]);
            assertFalse(authorization.hasPermission(session, com.laexclusiva.model.Permission.VIEW_CUSTOMER));
            return null;
        });
    }

    @Test
    void applicationOperationsRejectAFormerAdministratorAndLogoutRevokesTheIssuedSession() throws Exception {
        onFxThread(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE,
                    java.util.Map.of());
            String password = "Current-Identity-Admin-Test-2026";
            UserAccount account = new UserAccount(null, "admin", com.laexclusiva.security.PasswordHasher.hash(password),
                    UserRole.ADMINISTRATOR, null);
            services.identity().users().save(account);
            UserSession session = services.identity().authentication().createSession("admin", password).orElseThrow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            BorderPane menu = loader.load();
            new Scene(menu, 1000, 800);
            menu.applyCss();
            menu.layout();
            MainController controller = loader.getController();
            controller.setApplicationServices(services);
            controller.setOnLogout(() -> { });
            controller.initSession(session);

            UserAccount persisted = services.identity().users().findById(account.getId().toString()).orElseThrow();
            persisted.setRole(UserRole.WORKER);
            services.identity().users().update(persisted);
            assertEquals(UserRole.ADMINISTRATOR, session.getUserAccount().getRole(),
                    "El snapshot antiguo aún afirma administrador, pero no debe autorizar la acción.");
            ((Button) menu.lookup("#btnAdminUsers")).fire();
            assertEquals("No tiene permiso para administrar usuarios.",
                    ((Label) menu.getBottom()).getText());
            assertNull(menu.lookup("#usersTable"), "No debe cargarse la pantalla administrativa con el rol anterior.");

            ((Button) menu.lookup("#btnLogout")).fire();
            assertFalse(session.isValid());
            assertThrows(SecurityException.class,
                    () -> services.identity().sessions().validate(session));
            return null;
        });
    }

    @Test
    void testAppCanLoginLogoutAndChangeUserInTheSameWindow() throws Exception {
        onFxThread(() -> {
            App app = memoryAppWithTestAccounts();
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
                enterLogin(stage, "admin", "Admin-Test-2026-Valid-Only");
                BorderPane adminMenu = (BorderPane) stage.getScene().getRoot();
                assertTrue(adminMenu.lookup("#btnSellers").isVisible());
                assertFalse(adminMenu.lookup("#btnAdminUsers").isDisabled());
                assertTrue(adminMenu.lookup("#btnPurchases").isVisible());
                ((Button) adminMenu.lookup("#btnPurchases")).fire();
                assertActiveNavigation(adminMenu, "btnPurchases");
                assertNotNull(scrollContent(adminMenu.getCenter()).lookup("#ordersTable"));
                assertFalse(scrollContent(adminMenu.getCenter()).lookup("#supplierEditor").isDisabled());
                ((Button) adminMenu.lookup("#btnAdminUsers")).fire();
                adminMenu.applyCss(); adminMenu.layout();
                assertNotNull(adminMenu.lookup("#usersTable"), "La pantalla administrativa debe cargar la tabla de usuarios");
                ((Button) adminMenu.lookup("#btnCustomers")).fire();
                stage.setMaximized(true);
                ((Button) adminMenu.lookup("#btnLogout")).fire();
                assertTrue(stage.isShowing());
                assertFalse(stage.isMaximized());
                assertNotSame(loginScene, stage.getScene());
                assertTrue(((TextField) stage.getScene().lookup("#usernameField")).getText().isEmpty());
                assertTrue(((PasswordField) stage.getScene().lookup("#passwordField")).getText().isEmpty());
                enterLogin(stage, "worker", "Worker-Test-2026-Valid-Only");
                BorderPane workerMenu = (BorderPane) stage.getScene().getRoot();
                assertNotSame(adminMenu, workerMenu);
                assertFalse(workerMenu.lookup("#btnSellers").isVisible());
                assertFalse(workerMenu.lookup("#btnSellers").isManaged());
                assertFalse(workerMenu.lookup("#btnAdminUsers").isVisible());
                assertEquals("¡Bienvenido, worker!", ((Label) workerMenu.lookup("#welcomeLabel")).getText());
                assertTrue(workerMenu.lookup("#btnPurchases").isVisible());
                ((Button) workerMenu.lookup("#btnPurchases")).fire();
                assertActiveNavigation(workerMenu, "btnPurchases");
                assertNotNull(scrollContent(workerMenu.getCenter()).lookup("#ordersTable"));
                assertTrue(scrollContent(workerMenu.getCenter()).lookup("#supplierEditor").isDisabled());
                assertTrue(scrollContent(workerMenu.getCenter()).lookup("#orderEditor").isDisabled());
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
        App app = memoryAppWithTestAccounts();
        AuthenticationService auth = new AuthenticationService(app.getUserRepository());
        UserSession first = auth.createSession("admin", "Admin-Test-2026-Valid-Only").orElseThrow();
        first.invalidate();
        UserSession second = auth.createSession("admin", "Admin-Test-2026-Valid-Only").orElseThrow();
        assertNotSame(first, second);
        assertFalse(first.isActive());
        assertTrue(second.isActive());
    }

    private static void enterLogin(Stage stage, String username, String password) {
        ((TextField) stage.getScene().lookup("#usernameField")).setText(username);
        ((PasswordField) stage.getScene().lookup("#passwordField")).setText(password);
        ((Button) stage.getScene().lookup("#loginButton")).fire();
    }

    private static App memoryApp() {
        return new App(ApplicationServices.create(ApplicationServices.MEMORY_MODE, java.util.Map.of()));
    }

    private static App memoryAppWithTestAccounts() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, java.util.Map.of());
        services.identity().bootstrapInitialAdministrator("admin", "Admin-Test-2026-Valid-Only");
        services.identity().users().save(new UserAccount(null, "worker",
                PasswordHasher.hash("Worker-Test-2026-Valid-Only"), UserRole.WORKER, null));
        return new App(services);
    }

    private static void assertActiveNavigation(BorderPane menu, String selectedId) {
        PseudoClass active = PseudoClass.getPseudoClass("active");
        for (String id : List.of("btnDashboard", "btnCustomers", "btnSellers", "btnCredit", "btnInventory", "btnPurchases", "btnSales", "btnCash", "btnAdminUsers")) {
            assertEquals(id.equals(selectedId), menu.lookup("#" + id).getPseudoClassStates().contains(active), id);
        }
    }

    private static Node scrollContent(Node view) {
        return view instanceof ScrollPane scrollPane ? scrollPane.getContent() : view;
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
            new Scene(menu, 1000, 800);
            menu.applyCss();
            menu.layout();
            MainController controller = loader.getController();
            currentMenuIdentity = new IdentityTestFixture();
            var issued = currentMenuIdentity.issue(role);
            UserAccount account = issued.account();
            controller.setCustomerService(customers);
            controller.setSellerService(sellers);
            controller.setAuthorizationService(currentMenuIdentity.authorization());
            controller.initSession(issued.session());
            check.accept(menu, account);
            return null;
        });
        Platform.runLater(checkMenu);
        checkMenu.get(10, TimeUnit.SECONDS);
    }
}
