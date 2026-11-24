package com.laexclusiva.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.persistence.jdbc.DatabaseConfig;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.service.GoodsReceiptService;
import com.laexclusiva.service.PurchaseOrderService;
import com.laexclusiva.service.UserManagementService;
import com.laexclusiva.ui.App;
import com.laexclusiva.ui.ApplicationServices;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Comprueba desde escenas JavaFX reales la identidad PostgreSQL y el rol trabajador. */
class IdentityJavaFxPostgresIntegrationTest {
    private static final String ENABLE = "laexclusiva.test.postgres.javaFxIdentity";

    @BeforeAll
    static void startJavaFx() {
        if (!Boolean.getBoolean(ENABLE)) return;
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Otro conjunto JavaFX ya inició el toolkit.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void postgresLoginSurvivesApplicationReconstructionAndWorkerUsesOnlyGrantedFlows() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLE), "Requiere el laboratorio PostgreSQL efímero y JavaFX.");
        DatabaseConfig config = labConfig();
        Map<String, String> environment = System.getenv();
        String initialUsername = environment.get("LAEXCLUSIVA_INITIAL_ADMIN_USERNAME");
        String initialPassword = environment.get("LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD");
        assertEquals("qa-pr26-admin", initialUsername, "El fixture usa el usuario QA solicitado.");
        assertNotNull(initialPassword);
        assertFalse(initialPassword.isBlank());
        assertTrue(emptyLab(config), "El escenario JavaFX requiere un laboratorio aislado sin filas previas.");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String workerUsername = "qa-pr26-worker-" + suffix;
        String workerPassword = "P3-QA-Worker-" + UUID.randomUUID() + "!b8";
        String customerId = "FXCUS" + suffix;
        String productId = "FXPROD" + suffix;
        String supplierName = "Proveedor QA " + suffix;
        String sellerId = null;
        Long supplierId = null;
        Long adminId = null;
        Long workerId = null;
        Stage firstStage = fx(Stage::new);
        Stage restartedStage = fx(Stage::new);
        try {
            App firstApplication = new App();
            fx(() -> {
                firstApplication.start(firstStage);
                return null;
            });
            assertInstanceOf(UserRepositoryJdbc.class, firstApplication.getUserRepository());
            var originalAdmin = firstApplication.getUserRepository().findByUsername(initialUsername).orElseThrow();
            adminId = originalAdmin.getId();
            assertEquals(1, firstApplication.getUserRepository().findAll().size());

            fx(() -> {
                enterLogin(firstStage, initialUsername, initialPassword);
                assertInstanceOf(BorderPane.class, firstStage.getScene().getRoot());
                assertTrue(firstStage.getScene().getRoot().lookup("#btnAdminUsers").isVisible());
                return null;
            });

            ApplicationServices fixtureServices = application(config);
            var adminSession = fixtureServices.identity().authentication()
                    .createSession(initialUsername, initialPassword).orElseThrow();
            var seller = fixtureServices.sellers().registerSeller("Vendedor QA " + suffix,
                    "70" + suffix.substring(0, 8), "Dirección ficticia de laboratorio");
            sellerId = seller.getId();
            var worker = fixtureServices.identity().userManagement().createUser(adminSession,
                    new UserManagementService.CreateUserRequest(workerUsername, workerPassword,
                            UserRole.WORKER, true));
            workerId = worker.id();
            var workerAccount = fixtureServices.identity().users().findById(workerId.toString()).orElseThrow();
            fixtureServices.identity().userManagement().assignSeller(adminSession, workerId.toString(),
                    workerAccount.getSecurityVersion(), sellerId);
            var workerSession = fixtureServices.identity().authentication()
                    .createSession(workerUsername, workerPassword).orElseThrow();
            assertTrue(fixtureServices.authorization().hasPermission(workerSession, Permission.REGISTER_SALE));
            assertTrue(fixtureServices.authorization().hasPermission(workerSession, Permission.REGISTER_PAYMENT));
            assertFalse(fixtureServices.authorization().hasPermission(workerSession, Permission.MANAGE_USERS));

            BeerProduct product = product(productId);
            fixtureServices.products().register(product);
            fixtureServices.inventory().addStock(productId, 0, 5, "Entrada ficticia " + suffix);
            fixtureServices.customers().registerCustomer(customerId, "Cliente QA " + suffix,
                    "31" + suffix.substring(0, 8), "Dirección ficticia de laboratorio", new BigDecimal("10000000"));
            var supplier = fixtureServices.purchases().suppliers().create(adminSession, supplierName,
                    "NIT-QA-" + suffix, "contacto ficticio");
            supplierId = supplier.id();
            long fixtureSupplierId = supplier.id();
            var order = fixtureServices.purchases().orders().create(adminSession, fixtureSupplierId,
                    "Pedido ficticio para verificar consultas", List.of(
                            new PurchaseOrderService.OrderLineRequest(productId, 0, 1)));
            var orderLine = order.lines().get(0);

            int usersBeforeRestart = fixtureServices.identity().users().findAll().size();
            assertEquals(2, usersBeforeRestart);
            fx(() -> {
                ((Button) firstStage.getScene().getRoot().lookup("#btnLogout")).fire();
                firstStage.close();
                return null;
            });

            App restartedApplication = new App();
            fx(() -> {
                restartedApplication.start(restartedStage);
                return null;
            });
            assertInstanceOf(UserRepositoryJdbc.class, restartedApplication.getUserRepository());
            var persistedAdmin = restartedApplication.getUserRepository().findByUsername(initialUsername).orElseThrow();
            assertEquals(adminId, persistedAdmin.getId(), "El arranque no debe recrear la cuenta inicial.");
            assertEquals(usersBeforeRestart, restartedApplication.getUserRepository().findAll().size(),
                    "Reconstruir la aplicación no debe duplicar cuentas.");

            // La misma cadena recibida del entorno se introduce en el nuevo formulario JavaFX.
            fx(() -> {
                enterLogin(restartedStage, initialUsername, initialPassword);
                assertInstanceOf(BorderPane.class, restartedStage.getScene().getRoot(),
                        "La misma cuenta debe poder autenticarse después de reconstruir App y UserRepositoryJdbc.");
                Button logout = (Button) restartedStage.getScene().getRoot().lookup("#btnLogout");
                logout.fire();
                assertNotNull(restartedStage.getScene().lookup("#loginButton"),
                        "El cierre vuelve al login de la misma composición.");
                enterLogin(restartedStage, workerUsername, workerPassword);
                BorderPane menu = (BorderPane) restartedStage.getScene().getRoot();
                assertFalse(menu.lookup("#btnAdminUsers").isVisible());
                assertFalse(menu.lookup("#btnSellers").isVisible());
                assertTrue(menu.lookup("#btnPurchases").isVisible());
                assertTrue(menu.lookup("#btnSales").isVisible());
                return null;
            });

            assertEquals(1, fixtureServices.purchases().suppliers().findAll(workerSession).stream()
                    .filter(value -> supplierName.equals(value.name())).count());
            assertEquals(1, fixtureServices.purchases().orders().findAll(workerSession).stream()
                    .filter(value -> "Pedido ficticio para verificar consultas".equals(value.notes())).count());
            assertThrows(SecurityException.class,
                    () -> fixtureServices.identity().userManagement().listUsers(workerSession));
            assertThrows(SecurityException.class, () -> fixtureServices.identity().userManagement().createUser(
                    workerSession, new UserManagementService.CreateUserRequest("qa-prohibited-" + suffix,
                            workerPassword, UserRole.WORKER, true)));
            assertThrows(SecurityException.class, () -> fixtureServices.purchases().suppliers()
                    .create(workerSession, "Proveedor no autorizado", null, null));
            assertThrows(SecurityException.class, () -> fixtureServices.purchases().orders().create(workerSession,
                    fixtureSupplierId, "No autorizado", List.of(
                            new PurchaseOrderService.OrderLineRequest(productId, 0, 1))));
            assertThrows(SecurityException.class, () -> fixtureServices.purchases().orders()
                    .cancel(workerSession, order.order().id()));
            assertThrows(SecurityException.class, () -> fixtureServices.purchases().receipts().receive(workerSession,
                    order.order().id(), "worker-forbidden-" + suffix, "No autorizado",
                    List.of(new GoodsReceiptService.ReceiptLineRequest(orderLine.id(), 0, 1))));
            assertEquals(1, fixtureServices.purchases().suppliers().findAll(workerSession).stream()
                    .filter(value -> supplierName.equals(value.name())).count());
            assertEquals(1, fixtureServices.purchases().orders().findAll(workerSession).stream()
                    .filter(value -> "Pedido ficticio para verificar consultas".equals(value.notes())).count());
            assertEquals(usersBeforeRestart, fixtureServices.identity().users().findAll().size(),
                    "La creación administrativa rechazada no debe añadir una cuenta.");
            assertEquals(5, fixtureServices.inventory().availableUnits(productId),
                    "Las operaciones administrativas rechazadas no deben modificar existencias.");

            fx(() -> {
                BorderPane menu = (BorderPane) restartedStage.getScene().getRoot();
                ((Button) menu.lookup("#btnPurchases")).fire();
                javafx.scene.Node purchasesView = menu.getCenter();
                TableView<?> ordersTable = (TableView<?>) findById(purchasesView, "ordersTable");
                assertNotNull(ordersTable, "La ruta JavaFX de compras debe cargar su consulta: "
                        + ((javafx.scene.control.Label) menu.lookup("#navigationMessage")).getText()
                        + " (vista=" + purchasesView.getClass().getName() + ").");
                assertEquals(1, ordersTable.getItems().size());
                ((Button) menu.lookup("#btnSales")).fire();
                javafx.scene.Node salesView = menu.getCenter();
                @SuppressWarnings("unchecked")
                ComboBox<BeerProduct> products = (ComboBox<BeerProduct>) findById(salesView, "productCombo");
                @SuppressWarnings("unchecked")
                ComboBox<Customer> customers = (ComboBox<Customer>) findById(salesView, "customerCombo");
                products.setValue(products.getItems().stream()
                        .filter(item -> productId.equals(item.getId())).findFirst().orElseThrow());
                customers.setValue(customers.getItems().stream()
                        .filter(item -> customerId.equals(item.getId())).findFirst().orElseThrow());
                ((TextField) findById(salesView, "initialPaymentField")).setText("1000.00");
                ((Button) findById(salesView, "addLineButton")).fire();
                ((Button) findById(salesView, "registerSaleButton")).fire();
                TableView<?> history = (TableView<?>) findById(salesView, "historyTable");
                assertEquals(1, history.getItems().size(), "La venta debe persistir desde el controlador JavaFX.");
                ((Button) menu.lookup("#btnCredit")).fire();
                javafx.scene.Node portfolioView = menu.getCenter();
                @SuppressWarnings("unchecked")
                TableView<Sale> pendingSales = (TableView<Sale>) findById(portfolioView, "salesTable");
                assertEquals(1, pendingSales.getItems().size());
                assertEquals(new BigDecimal("1500.00"), pendingSales.getItems().get(0).calculateBalance());
                pendingSales.getSelectionModel().selectFirst();
                ((TextField) findById(portfolioView, "paymentAmountField")).setText("500.00");
                ((Button) findById(portfolioView, "registerPaymentButton")).fire();
                javafx.scene.control.Label status = (javafx.scene.control.Label) findById(portfolioView, "messageLabel");
                assertTrue(status.getText().contains("Abono registrado"), status.getText());
                return null;
            });

            Sale saved = fixtureServices.sales().listAll().stream()
                    .filter(value -> value.getCustomer() != null && customerId.equals(value.getCustomer().getId()))
                    .findFirst().orElseThrow();
            assertEquals(new BigDecimal("2500.00"), saved.calculateTotal());
            assertEquals(new BigDecimal("1500.00"), saved.calculateTotalPaid());
            assertEquals(new BigDecimal("1000.00"), saved.calculateBalance());
            assertEquals(new BigDecimal("1000.00"), fixtureServices.customers().getCustomerById(customerId).getBalance());
            assertEquals(4, fixtureServices.inventory().availableUnits(productId));
            assertEquals(1, fixtureServices.inventory().movementsOf(productId).stream()
                    .filter(movement -> movement.type() == com.laexclusiva.model.MovementType.EXIT).count());
            assertEquals(1, fixtureServices.purchases().suppliers().findAll(workerSession).stream()
                    .filter(value -> value.id() == fixtureSupplierId).count(),
                    "Solo el proveedor del fixture debe permanecer; el trabajador no pudo crear otro.");
        } finally {
            fx(() -> {
                firstStage.close();
                restartedStage.close();
                return null;
            });
            if (config != null) {
                cleanup(config, customerId, productId, sellerId, supplierId,
                        initialUsername, workerUsername);
            }
        }
    }

    private static ApplicationServices application(DatabaseConfig config) {
        return ApplicationServices.create(ApplicationServices.POSTGRES_MODE, Map.of(
                ApplicationServices.MODE_ENV, ApplicationServices.POSTGRES_MODE,
                DatabaseConfig.URL_ENV, config.getUrl(),
                DatabaseConfig.USER_ENV, config.getUser(),
                DatabaseConfig.PASSWORD_ENV, config.getPassword()));
    }

    private static DatabaseConfig labConfig() {
        Map<String, String> environment = System.getenv();
        assertEquals(ApplicationServices.POSTGRES_MODE, environment.get(ApplicationServices.MODE_ENV));
        assertEquals(environment.get(DatabaseConfig.TEST_URL_ENV), environment.get(DatabaseConfig.URL_ENV),
                "La aplicación también debe apuntar al mismo endpoint exclusivo de prueba.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment(environment);
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT current_database(), current_user, current_setting('cluster_name'), "
                             + "current_setting('server_version_num')::integer / 10000");
             ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals("la_exclusiva_test", rows.getString(1));
            assertEquals("identity_validation_owner", rows.getString(2));
            assertEquals("laexclusiva-identity-validation", rows.getString(3));
            assertEquals(18, rows.getInt(4));
        } catch (java.sql.SQLException failure) {
            throw new JdbcPersistenceException("No se pudo verificar el laboratorio JavaFX aislado.", failure);
        }
        assertMigrationObjects(config);
        return config;
    }

    private static void assertMigrationObjects(DatabaseConfig config) {
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT to_regclass('public.sale') IS NOT NULL
                        AND to_regclass('public.sale_payment') IS NOT NULL
                        AND to_regclass('public.purchase_order') IS NOT NULL
                        AND to_regclass('public.goods_receipt') IS NOT NULL
                        AND to_regclass('public.cash_movement') IS NOT NULL
                        AND to_regclass('public.customer_general_debt_movement') IS NOT NULL
                        AND EXISTS (SELECT FROM information_schema.columns WHERE table_schema='public'
                                    AND table_name='sale' AND column_name='idempotency_key')
                        AND EXISTS (SELECT FROM information_schema.columns WHERE table_schema='public'
                                    AND table_name='user_account' AND column_name='security_version')
                        AND EXISTS (SELECT FROM information_schema.columns WHERE table_schema='public'
                                    AND table_name='user_account' AND column_name='account_instance_id')
                        AND EXISTS (SELECT FROM pg_indexes WHERE schemaname='public'
                                    AND indexname='uq_user_account_active_seller')
                     """);
             ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next() && rows.getBoolean(1), "El esquema aislado debe incluir V001/V002/V004/V100–V106.");
        } catch (java.sql.SQLException failure) {
            throw new JdbcPersistenceException("No se pudo comprobar el esquema migrado del laboratorio.", failure);
        }
    }

    private static boolean emptyLab(DatabaseConfig config) {
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT (SELECT count(*) FROM user_account) = 0
                        AND (SELECT count(*) FROM customer) = 0
                        AND (SELECT count(*) FROM seller) = 0
                        AND (SELECT count(*) FROM beer_product) = 0
                        AND (SELECT count(*) FROM supplier) = 0
                        AND (SELECT count(*) FROM purchase_order) = 0
                        AND (SELECT count(*) FROM sale) = 0
                     """);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1);
        } catch (java.sql.SQLException failure) {
            throw new JdbcPersistenceException("No se pudo verificar que el laboratorio esté vacío.", failure);
        }
    }

    private static BeerProduct product(String id) {
        BeerProduct product = new BeerProduct(id, "Producto QA " + id, 330, 24,
                ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal("2500.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("28000.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("52000.00"));
        return product;
    }

    private static void enterLogin(Stage stage, String username, String password) {
        ((TextField) stage.getScene().lookup("#usernameField")).setText(username);
        ((PasswordField) stage.getScene().lookup("#passwordField")).setText(password);
        ((Button) stage.getScene().lookup("#loginButton")).fire();
    }

    private static Node findById(Node root, String id) {
        if (root == null) return null;
        if (id.equals(root.getId())) return root;
        if (root instanceof ScrollPane scrollPane) {
            Node content = findById(scrollPane.getContent(), id);
            if (content != null) return content;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findById(child, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T fx(java.util.concurrent.Callable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) return task.call();
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(60, TimeUnit.SECONDS);
    }

    private static void cleanup(DatabaseConfig config, String customerId, String productId, String sellerId,
                                Long supplierId, String adminUsername, String workerUsername) {
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword())) {
            connection.setAutoCommit(false);
            try {
                execute(connection, "DELETE FROM cash_movement WHERE source_reference IN "
                                + "(SELECT id FROM sale WHERE customer_id = ?) "
                                + "OR source_reference LIKE (SELECT id FROM sale WHERE customer_id = ?) || ':%'",
                        customerId, customerId);
                execute(connection, "DELETE FROM sale_payment WHERE sale_id IN "
                        + "(SELECT id FROM sale WHERE customer_id = ?)", customerId);
                execute(connection, "DELETE FROM sale_line WHERE sale_id IN "
                        + "(SELECT id FROM sale WHERE customer_id = ?)", customerId);
                execute(connection, "DELETE FROM sale WHERE customer_id = ?", customerId);
                if (supplierId != null) {
                    execute(connection, "DELETE FROM purchase_order_line WHERE purchase_order_id IN "
                            + "(SELECT id FROM purchase_order WHERE supplier_id = ?)", supplierId);
                    execute(connection, "DELETE FROM purchase_order WHERE supplier_id = ?", supplierId);
                    execute(connection, "DELETE FROM supplier WHERE id = ?", supplierId);
                }
                execute(connection, "DELETE FROM stock_movement WHERE product_id = ?", productId);
                execute(connection, "DELETE FROM inventory_item WHERE product_id = ?", productId);
                execute(connection, "DELETE FROM product_price WHERE product_id = ?", productId);
                execute(connection, "DELETE FROM beer_product WHERE id = ?", productId);
                execute(connection, "DELETE FROM customer WHERE id = ?", customerId);
                execute(connection, "DELETE FROM user_account WHERE username IN (?, ?)",
                        workerUsername, adminUsername);
                if (sellerId != null) execute(connection, "DELETE FROM seller WHERE id = ?", sellerId);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (Exception failure) {
            throw new JdbcPersistenceException("No se pudieron limpiar únicamente los fixtures QA del escenario JavaFX.", failure);
        }
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            statement.executeUpdate();
        }
    }
}
