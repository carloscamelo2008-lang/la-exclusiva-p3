package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.service.UserManagementService;
import com.laexclusiva.service.GoodsReceiptService;
import com.laexclusiva.service.PurchaseOrderService;
import com.laexclusiva.ui.ApplicationServices;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Valida la composición JavaFX/JDBC real dentro del laboratorio PostgreSQL efímero. */
class IdentityApplicationPostgresIntegrationTest {
    private static final String ENABLE = "laexclusiva.test.postgres.applicationServices";
    @Test
    void postgresCompositionBootstrapsOncePersistsAdministrationAndRevokesCurrentSessions() {
        DatabaseConfig config = labConfig();
        String username = "ia-admin-" + UUID.randomUUID().toString().substring(0, 12);
        String workerName = "ia-worker-" + UUID.randomUUID().toString().substring(0, 12);
        String adminPassword = "Identity-Lab-Admin-" + UUID.randomUUID() + "-A9!";
        String workerPassword = "Identity-Lab-Worker-" + UUID.randomUUID() + "-B8!";
        ApplicationServices app = application(config);
        Long adminId = null;
        Long workerId = null;
        try {
            assertSame(app.identity().authorization(), app.authorization());
            assertSame(app.identity().authentication().sessions(), app.identity().sessions());
            assertInstanceOf(UserRepositoryJdbc.class, app.identity().users());
            assertTrue(app.identity().users().findAll().isEmpty(),
                    "El escenario de bootstrap requiere la base efímera recién creada y vacía.");
            assertTrue(app.identity().bootstrapInitialAdministrator(username, adminPassword));
            assertFalse(app.identity().bootstrapInitialAdministrator("otro-admin", "Weak-Lab-Password-1!"),
                    "El bootstrap repetido no debe crear ni reinicializar cuentas.");
            var admin = app.identity().users().findByUsername(username).orElseThrow();
            adminId = admin.getId();
            assertEquals(1, app.identity().users().findAll().size());

            var authentication = app.identity().authentication();
            var authorization = app.authorization();
            var adminSession = authentication.createSession(username, adminPassword).orElseThrow();
            assertTrue(authentication.createSession(username, "contraseña incorrecta").isEmpty());
            assertTrue(authorization.hasPermission(adminSession, Permission.MANAGE_USERS));

            UserManagementService.UserChangeResult created = app.identity().userManagement().createUser(adminSession,
                    new UserManagementService.CreateUserRequest(workerName, workerPassword, UserRole.WORKER, true));
            workerId = created.id();
            var workerSession = authentication.createSession(workerName, workerPassword).orElseThrow();
            assertTrue(authorization.execute(workerSession, Permission.REGISTER_SALE, () -> true));
            assertThrows(SecurityException.class, () -> app.identity().userManagement().listUsers(workerSession));
            assertThrows(SecurityException.class, () -> authorization.execute(
                    new com.laexclusiva.model.UserSession(admin), Permission.MANAGE_USERS, () -> true));

            var currentWorker = app.identity().users().findById(workerId.toString()).orElseThrow();
            app.identity().userManagement().changeRole(adminSession, workerId.toString(),
                    currentWorker.getSecurityVersion(), UserRole.ADMINISTRATOR);
            assertFalse(authorization.hasPermission(workerSession, Permission.REGISTER_SALE),
                    "El cambio de rol debe invalidar la sesión emitida antes del cambio.");

            var promoted = authentication.createSession(workerName, workerPassword).orElseThrow();
            assertTrue(authorization.hasPermission(promoted, Permission.MANAGE_USERS));
            var promotedAccount = app.identity().users().findById(workerId.toString()).orElseThrow();
            app.identity().userManagement().setActive(adminSession, workerId.toString(),
                    promotedAccount.getSecurityVersion(), false);
            assertFalse(authorization.hasPermission(promoted, Permission.MANAGE_USERS),
                    "Una cuenta desactivada debe perder acceso aunque conserve una sesión JavaFX.");

            authentication.revoke(adminSession);
            assertFalse(authorization.hasPermission(adminSession, Permission.MANAGE_USERS));
            assertThrows(SecurityException.class, () -> app.purchases().orders().findAll(adminSession),
                    "La revocación debe proteger también los servicios JavaFX de compras.");
            ApplicationServices restarted = application(config);
            assertTrue(restarted.identity().authentication().createSession(username, adminPassword).isPresent(),
                    "La identidad JDBC debe seguir persistida tras crear otra composición de aplicación.");
        } finally {
            deleteFixtureUsers(config, workerId, adminId);
        }
    }

    @Test
    void composedApplicationPersistsCommercialWorkflowAndLeavesNoEffectsOnRejectedSale() {
        DatabaseConfig config = labConfig();
        ApplicationServices app = application(config);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String customerId = "ia-customer-" + suffix.substring(0, 20);
        String productId = "ia-product-" + suffix.substring(0, 20);
        String adminName = "ia-commerce-" + suffix.substring(0, 16);
        String adminPassword = "Identity-Lab-Commerce-" + suffix + "-C7!";
        String saleKey = "identity-app-sale-" + suffix;
        String installmentKey = "identity-app-payment-" + suffix;
        String generalDebtKey = "identity-app-debt-" + suffix;
        String generalPaymentKey = "identity-app-general-payment-" + suffix;
        String saleId = null;
        String sellerId = null;
        Long supplierId = null;
        Long adminId = null;
        try {
            assertTrue(app.identity().bootstrapInitialAdministrator(adminName, adminPassword));
            var adminSession = app.identity().authentication().createSession(adminName, adminPassword).orElseThrow();

            app.customers().registerCustomer(customerId, "Cliente ficticio " + suffix.substring(0, 6),
                    "5550001", "Dirección de laboratorio", new BigDecimal("10000000"));
            var seller = app.sellers().registerSeller("Vendedor ficticio " + suffix.substring(0, 6),
                    "5550002", "Dirección de laboratorio");
            sellerId = seller.getId();
            String registeredSellerId = sellerId;
            app.products().register(product(productId));
            app.authorization().execute(adminSession, Permission.MANAGE_INVENTORY_ENTRIES,
                    () -> app.inventory().addStock(productId, 0, 10, "Entrada ficticia de integración " + suffix));
            assertEquals(10, app.inventory().availableUnits(productId));

            var account = app.identity().users().findByUsername(adminName).orElseThrow();
            adminId = account.getId();
            app.identity().userManagement().assignSeller(adminSession, account.getId().toString(),
                    account.getSecurityVersion(), registeredSellerId);
            assertThrows(SecurityException.class, () -> app.purchases().suppliers().findAll(adminSession),
                    "El cambio de asignación debe invalidar la sesión anterior también en Compras.");
            var purchasesSession = app.identity().authentication()
                    .createSession(adminName, adminPassword).orElseThrow();

            List<StockRequest> lines = List.of(new StockRequest(productId, SalePresentation.UNIT, 2));
            assertThrows(SecurityException.class,
                    () -> app.sales().registerSale(lines, registeredSellerId, customerId, BigDecimal.ZERO, saleKey));
            app.sellers().setActive(registeredSellerId, false);
            assertThrows(IllegalArgumentException.class, () -> app.authorization().execute(purchasesSession,
                    Permission.REGISTER_SALE,
                    () -> app.sales().registerSale(lines, registeredSellerId, customerId, BigDecimal.ZERO, saleKey)));
            app.sellers().setActive(registeredSellerId, true);
            Sale sale = app.authorization().execute(purchasesSession, com.laexclusiva.model.Permission.REGISTER_SALE, () -> app.sales().registerSale(lines, registeredSellerId, customerId,
                    new BigDecimal("2000.00"), saleKey));
            saleId = sale.getId();
            assertEquals(new BigDecimal("5000.00"), sale.calculateTotal());
            assertEquals(new BigDecimal("3000.00"), sale.calculateBalance());
            assertEquals(8, app.inventory().availableUnits(productId));
            assertEquals(new BigDecimal("3000.00"), app.customers().getCustomerById(customerId).getBalance());
            assertEquals(new BigDecimal("3000.00"), app.sales().outstandingSalesDebt(customerId));

            Sale replay = app.authorization().execute(purchasesSession, com.laexclusiva.model.Permission.REGISTER_SALE, () -> app.sales().registerSale(lines, registeredSellerId, customerId,
                    new BigDecimal("2000.00"), saleKey));
            assertEquals(saleId, replay.getId(), "Repetir la misma clave debe recuperar la venta original.");
            assertEquals(8, app.inventory().availableUnits(productId), "El replay no vuelve a descontar existencias.");

            app.authorization().execute(purchasesSession, Permission.REGISTER_PAYMENT, () -> {
                app.sales().registerPayment(sale.getId(), new BigDecimal("1000.00"), installmentKey);
                app.sales().registerPayment(sale.getId(), new BigDecimal("1000.00"), installmentKey);
            });
            for (var payment : app.sales().findById(saleId).getPayments()) {
                assertEquals(adminId, payment.getReceivedByUserId());
            }
            assertEquals(new BigDecimal("2000.00"), app.sales().findById(saleId).calculateBalance());
            assertEquals(new BigDecimal("2000.00"), app.customers().getCustomerById(customerId).getBalance());

            app.generalDebt().registerDebt(customerId, new BigDecimal("500.00"), "LABORATORIO",
                    "Cargo ficticio de integración", generalDebtKey);
            var generalPayment = app.authorization().execute(purchasesSession, Permission.REGISTER_PAYMENT,
                    () -> app.generalDebt().registerPayment(customerId, new BigDecimal("200.00"), "LABORATORIO",
                            "Pago ficticio de integración", generalPaymentKey));
            assertEquals(adminId, generalPayment.receivedByUserId());
            var breakdown = app.generalDebt().findBalance(customerId);
            assertEquals(new BigDecimal("2000.00"), breakdown.outstandingSalesDebt());
            assertEquals(new BigDecimal("300.00"), breakdown.generalDebt());

            LocalDate from = LocalDate.now().minusDays(1);
            LocalDate through = LocalDate.now().plusDays(1);
            var summary = app.cash().summarize(from, through);
            assertEquals(new BigDecimal("5000.00"), summary.invoicedSales());
            assertEquals(new BigDecimal("3000.00"), summary.saleReceipts());
            assertEquals(new BigDecimal("200.00"), summary.generalDebtReceipts());
            int movementCount = app.inventory().movementsOf(productId).size();
            assertEquals(2, movementCount, "Solo la entrada y la salida confirmadas deben aparecer.");
            for (var movement : app.inventory().movementsOf(productId)) {
                assertEquals(adminId, movement.actorUserId());
            }
            assertTrue(app.cash().listMovements(from, through, null).stream()
                    .allMatch(movement -> movement.userId() != null));

            var before = summary;
            assertThrows(com.laexclusiva.model.InsufficientStockException.class,
                    () -> app.authorization().execute(purchasesSession, com.laexclusiva.model.Permission.REGISTER_SALE, () -> app.sales().registerSale(List.of(new StockRequest(productId, SalePresentation.UNIT, 9)),
                            registeredSellerId, customerId, BigDecimal.ZERO, "identity-app-rejected-" + suffix)));
            assertEquals(8, app.inventory().availableUnits(productId));
            assertEquals(movementCount, app.inventory().movementsOf(productId).size());
            assertEquals(new BigDecimal("2300.00"), app.customers().getCustomerById(customerId).getBalance());
            assertEquals(before, app.cash().summarize(from, through));

            var supplier = app.purchases().suppliers().create(purchasesSession,
                    "Proveedor ficticio " + suffix.substring(0, 6), null, null);
            supplierId = supplier.id();
            var order = app.purchases().orders().create(purchasesSession, supplier.id(), "Fixture de laboratorio",
                    List.of(new PurchaseOrderService.OrderLineRequest(productId, 1, 0)));
            assertEquals(adminId, order.order().createdByUserId(), "El pedido debe guardar el ID persistido del actor.");
            var receiptLine = order.lines().get(0);
            var receipt = app.purchases().receipts().receive(purchasesSession, order.order().id(),
                    "identity-app-receipt-" + suffix, "Recepción ficticia",
                    List.of(new GoodsReceiptService.ReceiptLineRequest(receiptLine.id(), 1, 0)));
            assertEquals(adminId, receipt.receivedByUserId(), "La recepción debe guardar el ID persistido del actor.");
            assertEquals(32, app.inventory().availableUnits(productId));
            assertEquals(3, app.inventory().movementsOf(productId).size());
            assertEquals(before, app.cash().summarize(from, through),
                    "Una recepción de compra no se contabiliza como efectivo de venta.");

            ApplicationServices restarted = application(config);
            var restartedSession = restarted.identity().authentication()
                    .createSession(adminName, adminPassword).orElseThrow();
            assertEquals(saleId, restarted.sales().findById(saleId).getId());
            assertEquals(32, restarted.inventory().availableUnits(productId));
            assertEquals(3, restarted.inventory().movementsOf(productId).size());
            assertEquals(order.order().id(), restarted.purchases().orders()
                    .findById(restartedSession, order.order().id()).orElseThrow().order().id());
            assertEquals(new BigDecimal("2300.00"), restarted.customers().getCustomerById(customerId).getBalance());
            assertTrue(restartedSession.isActive());

            var manual = restarted.authorization().execute(restartedSession, Permission.REGISTER_CASH_MOVEMENT,
                    () -> restarted.cash().registerMovement(com.laexclusiva.model.CashDirection.INCOME,
                            com.laexclusiva.model.CashCategory.OTHER_INCOME, BigDecimal.ONE,
                            java.time.LocalDateTime.now(), "Caja ficticia", saleKey + "-manual",
                            com.laexclusiva.model.PaymentMethod.TRANSFERENCIA));
            assertEquals(adminId, manual.userId());
            assertEquals(com.laexclusiva.model.PaymentMethod.TRANSFERENCIA, manual.paymentMethod());
            assertTrue(restarted.cash().listMovements(from, through, null).stream()
                    .anyMatch(m -> m.id() == manual.id() && m.paymentMethod() == manual.paymentMethod()
                            && manual.userId().equals(m.userId())));
        } finally {
            cleanupCommercialFixture(config, customerId, productId, sellerId, supplierId, saleKey,
                    generalPaymentKey, adminName);
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
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLE), "Integración reservada al laboratorio efímero CI.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
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
            throw new JdbcPersistenceException("No se pudo validar el laboratorio aislado.", failure);
        }
        return config;
    }

    private static BeerProduct product(String id) {
        BeerProduct product = new BeerProduct(id, "Producto ficticio de integración", 330, 24,
                ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal("2500.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("28000.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("52000.00"));
        return product;
    }

    private static void deleteFixtureUsers(DatabaseConfig config, Long... ids) {
        if (ids[0] == null && ids[1] == null) return;
        executeCleanup(config, connection -> {
            for (Long id : ids) {
                if (id == null) continue;
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM administrative_audit_event
                        WHERE actor_user_id = ? OR (target_type = 'USER' AND target_reference = ?)
                        """)) {
                    statement.setLong(1, id);
                    statement.setString(2, id.toString());
                    statement.executeUpdate();
                }
            }
            for (Long id : ids) {
                if (id == null) continue;
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM user_account WHERE id = ?")) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
            }
        });
    }

    private static void cleanupCommercialFixture(DatabaseConfig config, String customerId, String productId,
                                                String sellerId, Long supplierId, String saleKey, String generalPaymentKey,
                                                String username) {
        executeCleanup(config, connection -> {
            if (supplierId != null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM goods_receipt_line WHERE goods_receipt_id IN (
                            SELECT r.id FROM goods_receipt r JOIN purchase_order o ON o.id = r.purchase_order_id
                            WHERE o.supplier_id = ?)
                        """)) {
                    statement.setLong(1, supplierId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM goods_receipt WHERE purchase_order_id IN
                            (SELECT id FROM purchase_order WHERE supplier_id = ?)
                        """)) {
                    statement.setLong(1, supplierId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM purchase_order_line WHERE purchase_order_id IN
                            (SELECT id FROM purchase_order WHERE supplier_id = ?)
                        """)) {
                    statement.setLong(1, supplierId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM purchase_order WHERE supplier_id = ?")) {
                    statement.setLong(1, supplierId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM supplier WHERE id = ?")) {
                    statement.setLong(1, supplierId);
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM cash_movement WHERE source_reference IN
                    (SELECT id FROM sale WHERE idempotency_key = ?)
                    OR source_reference LIKE (SELECT id FROM sale WHERE idempotency_key = ?) || ':%'
                    OR idempotency_key = 'GENERAL-DEBT-PAYMENT:' || ?
                    """)) {
                statement.setString(1, saleKey);
                statement.setString(2, saleKey);
                statement.setString(3, generalPaymentKey);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM customer_general_debt_movement WHERE customer_id = ?")) {
                statement.setString(1, customerId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM sale_payment WHERE sale_id IN (SELECT id FROM sale WHERE idempotency_key = ?)
                    """)) {
                statement.setString(1, saleKey);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM sale_line WHERE sale_id IN (SELECT id FROM sale WHERE idempotency_key = ?)
                    """)) {
                statement.setString(1, saleKey);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM sale WHERE idempotency_key = ?")) {
                statement.setString(1, saleKey);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM cash_movement WHERE idempotency_key = ?")) {
                statement.setString(1, saleKey + "-manual");
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM stock_movement WHERE product_id = ?")) {
                statement.setString(1, productId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM inventory_item WHERE product_id = ?")) {
                statement.setString(1, productId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM product_price WHERE product_id = ?")) {
                statement.setString(1, productId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM beer_product WHERE id = ?")) {
                statement.setString(1, productId);
                statement.executeUpdate();
            }
            try (PreparedStatement auditCleanup = connection.prepareStatement("""
                    DELETE FROM administrative_audit_event
                    WHERE actor_user_id IN (SELECT id FROM user_account WHERE username = ?)
                       OR (target_type = 'USER' AND target_reference IN
                           (SELECT id::text FROM user_account WHERE username = ?))
                       OR (target_type = 'CUSTOMER' AND target_reference = ?)
                    """)) {
                auditCleanup.setString(1, username);
                auditCleanup.setString(2, username);
                auditCleanup.setString(3, customerId);
                auditCleanup.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM customer WHERE id = ?")) {
                statement.setString(1, customerId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM user_account WHERE username = ?")) {
                statement.setString(1, username);
                statement.executeUpdate();
            }
            if (sellerId != null) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM seller WHERE id = ?")) {
                    statement.setString(1, sellerId);
                    statement.executeUpdate();
                }
            }
        });
    }

    private static void executeCleanup(DatabaseConfig config, SqlCleanup cleanup) {
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword())) {
            connection.setAutoCommit(false);
            try {
                cleanup.apply(connection);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (Exception failure) {
            throw new JdbcPersistenceException("No se pudieron limpiar exclusivamente los fixtures de integración.", failure);
        }
    }

    @FunctionalInterface
    private interface SqlCleanup {
        void apply(Connection connection) throws Exception;
    }
}
