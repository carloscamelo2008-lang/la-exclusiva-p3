package com.laexclusiva.service;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.ui.App;
import com.laexclusiva.ui.controller.MainController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URL;
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
                    () -> assertEquals(expectedVisibility, adminUsers.isManaged(), "Espacio de Gestión de Usuarios")
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
            account.setActive(false);
            customersButton.fire();
            assertSame(dashboard, menu.getCenter());
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

    private void withMainMenu(UserRole role, CustomerService customers,
                              BiConsumer<BorderPane, UserAccount> check) throws Exception {
        FutureTask<Void> checkMenu = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            BorderPane menu = loader.load();
            MainController controller = loader.getController();
            UserAccount account = new UserAccount(1L, "test", "unused", role, null);
            controller.setCustomerService(customers);
            controller.initSession(new UserSession(account));
            check.accept(menu, account);
            return null;
        });
        Platform.runLater(checkMenu);
        checkMenu.get(10, TimeUnit.SECONDS);
    }
}
