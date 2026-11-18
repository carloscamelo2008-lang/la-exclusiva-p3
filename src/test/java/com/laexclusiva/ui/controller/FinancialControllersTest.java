package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.CashSummary;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.CashWorkflow;
import com.laexclusiva.service.CustomerGeneralDebtWorkflow;
import com.laexclusiva.ui.ApplicationServices;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regresiones visuales de permisos de caja y separación de la cartera financiera. */
class FinancialControllersTest {

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Otro conjunto de pruebas ya inició el toolkit.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void cashFormKeepsTheSameKeyAndTimestampAfterAnUncertainWrite() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            UncertainCashWorkflow cash = new UncertainCashWorkflow(services.cash());
            FXMLLoader loader = load("cash_view.fxml");
            ((CashController) loader.getController()).configure(cash, adminSession());
            text(loader, "amountField").setText("12.50");
            text(loader, "descriptionField").setText("Ingreso ficticio de validación");
            String firstKey = label(loader, "operationKeyLabel").getText();

            cash.throwAfterNextWrite = true;
            button(loader, "registerMovementButton").fire();
            assertEquals(1, cash.keys.size());
            assertEquals(firstKey, label(loader, "operationKeyLabel").getText());
            assertTrue(label(loader, "messageLabel").getText().contains("referencia se conserva"));

            text(loader, "amountField").setText("9.00");
            button(loader, "registerMovementButton").fire();
            assertEquals(1, cash.keys.size(), "No debe enviarse otra carga útil con una referencia incierta.");
            assertEquals(firstKey, label(loader, "operationKeyLabel").getText());

            text(loader, "amountField").setText("12.50");
            ((DatePicker) loader.getNamespace().get("movementDatePicker")).setValue(LocalDate.now().minusDays(1));
            button(loader, "registerMovementButton").fire();
            assertEquals(1, cash.keys.size(), "La fecha también forma parte de la solicitud incierta.");
            ((DatePicker) loader.getNamespace().get("movementDatePicker")).setValue(LocalDate.now());
            button(loader, "registerMovementButton").fire();
            assertEquals(List.of(cash.keys.get(0), cash.keys.get(0)), cash.keys);
            assertEquals(1, services.cash().listMovements(LocalDate.now(), LocalDate.now(), null).size());
            assertNotEquals(firstKey, label(loader, "operationKeyLabel").getText());
            assertTrue(label(loader, "messageLabel").getText().contains("Movimiento registrado."));
            return null;
        });
    }

    @Test
    void confirmedCashWriteSurvivesRefreshFailureAndRotatesItsKeyOnlyAfterCommit() throws Exception {
        runOnFx(() -> {
            ControlledCashWorkflow cash = new ControlledCashWorkflow();
            cash.records.add(existingCashMovement());
            FXMLLoader loader = load("cash_view.fxml");
            ((CashController) loader.getController()).configure(cash, adminSession());
            String originalKey = label(loader, "operationKeyLabel").getText();
            cash.failNextSummary = true;
            text(loader, "amountField").setText("12.50");
            text(loader, "descriptionField").setText("Ingreso ficticio confirmado");

            button(loader, "registerMovementButton").fire();

            assertEquals(1, cash.writeCalls, "La confirmación debe corresponder a una sola escritura.");
            assertEquals(1, cash.records.size() - 1, "El fixture existente no debe duplicarse.");
            assertNotEquals(originalKey, label(loader, "operationKeyLabel").getText(),
                    "La clave solo se renueva cuando registerMovement retorna confirmado.");
            assertTrue(label(loader, "messageLabel").getText().contains("Movimiento registrado correctamente"));
            assertTrue(label(loader, "messageLabel").getText().contains("no se pudo actualizar"));
            assertTrue(((TableView<?>) loader.getNamespace().get("movementsTable")).getItems().isEmpty());
            assertUnavailableLabels(loader);
            return null;
        });
    }

    @Test
    void failedCashRefreshClearsRowsAndEveryPreviousSummaryValue() throws Exception {
        runOnFx(() -> {
            ControlledCashWorkflow cash = new ControlledCashWorkflow();
            cash.records.add(existingCashMovement());
            FXMLLoader loader = load("cash_view.fxml");
            ((CashController) loader.getController()).configure(cash, adminSession());
            assertTrue(label(loader, "invoicedSalesLabel").getText().contains("100"));
            assertFalse(((TableView<?>) loader.getNamespace().get("movementsTable")).getItems().isEmpty());
            cash.failNextSummary = true;

            button(loader, "refreshButton").fire();

            assertTrue(((TableView<?>) loader.getNamespace().get("movementsTable")).getItems().isEmpty());
            assertUnavailableLabels(loader);
            assertTrue(label(loader, "messageLabel").getText().contains("No se pudo consultar caja"));
            return null;
        });
    }

    @Test
    void refreshCashButtonPerformsOnlyReadOperations() throws Exception {
        runOnFx(() -> {
            ControlledCashWorkflow cash = new ControlledCashWorkflow();
            FXMLLoader loader = load("cash_view.fxml");
            ((CashController) loader.getController()).configure(cash, adminSession());
            cash.resetCounters();
            String operationKey = label(loader, "operationKeyLabel").getText();

            button(loader, "refreshButton").fire();

            assertEquals(0, cash.writeCalls);
            assertEquals(1, cash.listCalls);
            assertEquals(1, cash.summaryCalls);
            assertEquals(operationKey, label(loader, "operationKeyLabel").getText());
            assertTrue(label(loader, "messageLabel").getText().contains("Caja actualizada"));
            return null;
        });
    }

    @Test
    void cashHandlersRecheckSessionPermissionsAfterTheScreenWasConfigured() throws Exception {
        runOnFx(() -> {
            ControlledCashWorkflow cash = new ControlledCashWorkflow();
            FXMLLoader loader = load("cash_view.fxml");
            UserSession session = adminSession();
            ((CashController) loader.getController()).configure(cash, session);
            cash.resetCounters();
            session.invalidate();
            text(loader, "amountField").setText("8.00");
            text(loader, "descriptionField").setText("Intento con sesión vencida");

            button(loader, "registerMovementButton").fire();
            assertEquals(0, cash.writeCalls);
            assertTrue(label(loader, "messageLabel").getText().contains("permiso de caja"));

            button(loader, "refreshButton").fire();
            assertEquals(0, cash.listCalls);
            assertEquals(0, cash.summaryCalls);
            assertUnavailableLabels(loader);
            return null;
        });
    }

    @Test
    void workerCannotReadOrWriteCashEvenWhenTheScreenIsConfiguredDirectly() throws Exception {
        runOnFx(() -> {
            CountingCashWorkflow cash = new CountingCashWorkflow();
            FXMLLoader loader = load("cash_view.fxml");
            ((CashController) loader.getController()).configure(cash, new UserSession(
                    new UserAccount(700L, "trabajador-ficticio", "unused", UserRole.WORKER, null)));

            assertFalse(button(loader, "registerMovementButton").isVisible());
            assertEquals(0, cash.listCalls);
            assertEquals(0, cash.summaryCalls);
            button(loader, "refreshButton").fire();
            button(loader, "registerMovementButton").fire();
            assertEquals(0, cash.listCalls);
            assertEquals(0, cash.summaryCalls);
            assertEquals(0, cash.writeCalls);
            assertTrue(label(loader, "messageLabel").getText().contains("permiso"));
            return null;
        });
    }

    @Test
    void workerCannotCreateGeneralDebtThroughTheCustomerRegistrationPermission() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String customerId = "CUS-FINANCE-WORKER";
            services.customers().registerCustomer(customerId, "Cliente ficticio", "3000000103", "Dirección de prueba");
            UserSession worker = new UserSession(new UserAccount(702L, "trabajador-cartera", "unused",
                    UserRole.WORKER, null));
            FXMLLoader loader = load("portfolio_view.fxml");
            PortfolioController controller = loader.getController();
            controller.configure(services.customers(), services.sales(), services.generalDebt(), worker);
            controller.showPortfolio(services.customers().getAllCustomers());

            assertFalse(button(loader, "registerGeneralDebtButton").isVisible());
            text(loader, "generalDebtAmountField").setText("50.00");
            text(loader, "generalDebtCategoryField").setText("Cargo ficticio");
            text(loader, "generalDebtDescriptionField").setText("Intento no autorizado");
            button(loader, "registerGeneralDebtButton").fire();

            assertEquals(0, services.customers().getCustomerById(customerId).getBalance()
                    .compareTo(BigDecimal.ZERO));
            assertTrue(services.generalDebt().listMovements(customerId, null, null, null).isEmpty());
            assertTrue(label(loader, "messageLabel").getText().contains("No tiene permiso"));
            return null;
        });
    }

    @Test
    void portfolioGeneralDebtPaymentDoesNotChangeTheOutstandingSale() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String customerId = "CUS-FINANCE-UI";
            services.customers().registerCustomer(customerId, "Cliente ficticio", "3000000101", "Dirección de prueba");
            services.customers().addDebtToCustomer(customerId, new BigDecimal("30.00"));
            var seller = services.sellers().registerSeller("Vendedor ficticio", "3000000102", "Dirección de prueba");
            BeerProduct product = new BeerProduct("P-FINANCE-UI", "Producto ficticio", 330, 24,
                    ContainerType.NON_RETURNABLE_CAN, null);
            product.definePrice(SalePresentation.UNIT, new BigDecimal("100.00"));
            product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("450.00"));
            product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("900.00"));
            services.products().register(product);
            services.inventory().addStock(product.getId(), 0, 2, "Fixture visual ficticio");
            var sale = services.sales().registerSale(List.of(
                    new StockRequest(product.getId(), SalePresentation.UNIT, 1)), seller.getId(),
                    customerId, BigDecimal.ZERO);

            FXMLLoader loader = load("portfolio_view.fxml");
            PortfolioController controller = loader.getController();
            UncertainGeneralDebtWorkflow generalDebt = new UncertainGeneralDebtWorkflow(services.generalDebt());
            controller.configure(services.customers(), services.sales(), generalDebt, adminSession());
            controller.showPortfolio(services.customers().getAllCustomers());

            text(loader, "generalDebtAmountField").setText("10.00");
            text(loader, "generalDebtCategoryField").setText("Ajuste ficticio");
            text(loader, "generalDebtDescriptionField").setText("Deuda general de validación");
            button(loader, "registerGeneralDebtButton").fire();
            assertTrue(label(loader, "messageLabel").getText().contains("Deuda general registrada"));

            text(loader, "generalDebtAmountField").setText("15.00");
            text(loader, "generalDebtCategoryField").setText("Abono ficticio");
            text(loader, "generalDebtDescriptionField").setText("Abono solo a deuda general");
            generalDebt.throwAfterNextPayment = true;
            button(loader, "payGeneralDebtButton").fire();
            String uncertainKey = label(loader, "generalDebtOperationKeyLabel").getText();
            assertTrue(label(loader, "messageLabel").getText().contains("Se conserva su referencia"));
            assertEquals(1, generalDebt.paymentKeys.size());

            text(loader, "generalDebtAmountField").setText("17.00");
            button(loader, "payGeneralDebtButton").fire();
            assertEquals(1, generalDebt.paymentKeys.size(), "No se admite otra carga con una clave incierta.");
            assertEquals(uncertainKey, label(loader, "generalDebtOperationKeyLabel").getText());

            text(loader, "generalDebtAmountField").setText("15.00");
            button(loader, "payGeneralDebtButton").fire();

            assertEquals(0, services.sales().findById(sale.getId()).calculateTotalPaid().compareTo(BigDecimal.ZERO));
            assertEquals(0, services.sales().findById(sale.getId()).calculateBalance()
                    .compareTo(new BigDecimal("100.00")));
            assertEquals(0, services.customers().getCustomerById(customerId).getBalance()
                    .compareTo(new BigDecimal("125.00")));
            assertEquals(0, services.generalDebt().findBalance(customerId).generalDebt()
                    .compareTo(new BigDecimal("25.00")));
            assertEquals(2, services.generalDebt().listMovements(customerId, null, null, null).size());
            assertEquals(2, generalDebt.paymentKeys.size());
            assertEquals(generalDebt.paymentKeys.get(0), generalDebt.paymentKeys.get(1));
            assertTrue(label(loader, "messageLabel").getText().contains("solo a deuda general"));
            return null;
        });
    }

    @Test
    void confirmedGeneralDebtWithInternalRefreshFailureKeepsSuccessAndRefreshIsReadOnly() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String customerId = "CUS-GENERAL-DEBT-REFRESH";
            services.customers().registerCustomer(customerId, "Cliente ficticio", "3000000411", "Dirección");
            RefreshFailingGeneralDebtWorkflow generalDebt =
                    new RefreshFailingGeneralDebtWorkflow(services.generalDebt());
            FXMLLoader loader = load("portfolio_view.fxml");
            ((PortfolioController) loader.getController()).configure(services.customers(), services.sales(),
                    generalDebt, adminSession());
            ((PortfolioController) loader.getController()).showPortfolio(services.customers().getAllCustomers());
            text(loader, "generalDebtAmountField").setText("35.00");
            text(loader, "generalDebtCategoryField").setText("Ajuste ficticio");
            text(loader, "generalDebtDescriptionField").setText("Alta con fallo de consulta");

            button(loader, "registerGeneralDebtButton").fire();

            String message = label(loader, "messageLabel").getText();
            assertEquals(1, generalDebt.debtWrites);
            assertTrue(message.contains("Deuda general registrada correctamente"), message);
            assertTrue(message.contains("No se pudo actualizar la consulta de cartera"), message);
            assertTrue(label(loader, "generalDebtLabel").getText().contains("no disponible"));
            assertTrue(table(loader, "portfolioTable").getItems().isEmpty());
            assertNull(((ComboBox<?>) loader.getNamespace().get("customerCombo")).getValue());
            assertTrue(label(loader, "generalDebtOperationKeyLabel").getText().contains("se genera al registrar"));

            button(loader, "refreshPortfolioButton").fire();
            assertEquals(1, generalDebt.debtWrites, "Actualizar cartera no debe repetir el cargo confirmado.");
            assertTrue(label(loader, "messageLabel").getText().contains("Cartera actualizada"));
            assertTrue(label(loader, "generalDebtLabel").getText().startsWith("Deuda general:"));
            return null;
        });
    }

    @Test
    void confirmedGeneralPaymentWithInternalRefreshFailureKeepsSuccessAndRefreshIsReadOnly() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            String customerId = "CUS-GENERAL-PAYMENT-REFRESH";
            services.customers().registerCustomer(customerId, "Cliente ficticio", "3000000412", "Dirección");
            services.generalDebt().registerDebt(customerId, new BigDecimal("60.00"), "Fixture",
                    "Saldo ficticio para regresión", "seed-general-refresh-" + customerId);
            RefreshFailingGeneralDebtWorkflow generalDebt =
                    new RefreshFailingGeneralDebtWorkflow(services.generalDebt());
            FXMLLoader loader = load("portfolio_view.fxml");
            ((PortfolioController) loader.getController()).configure(services.customers(), services.sales(),
                    generalDebt, adminSession());
            ((PortfolioController) loader.getController()).showPortfolio(services.customers().getAllCustomers());
            text(loader, "generalDebtAmountField").setText("20.00");
            text(loader, "generalDebtCategoryField").setText("Abono ficticio");
            text(loader, "generalDebtDescriptionField").setText("Pago con fallo de consulta");

            button(loader, "payGeneralDebtButton").fire();

            String message = label(loader, "messageLabel").getText();
            assertEquals(1, generalDebt.paymentWrites);
            assertTrue(message.contains("Abono general confirmado"), message);
            assertTrue(message.contains("No se pudo actualizar la consulta de cartera"), message);
            assertTrue(label(loader, "generalDebtLabel").getText().contains("no disponible"));
            assertTrue(table(loader, "portfolioTable").getItems().isEmpty());
            assertNull(((ComboBox<?>) loader.getNamespace().get("customerCombo")).getValue());
            assertTrue(label(loader, "generalDebtOperationKeyLabel").getText().contains("se genera al registrar"));

            button(loader, "refreshPortfolioButton").fire();
            assertEquals(1, generalDebt.paymentWrites, "Actualizar cartera solo consulta; no aplica otro abono.");
            assertTrue(label(loader, "messageLabel").getText().contains("Cartera actualizada"));
            assertTrue(label(loader, "generalDebtLabel").getText().startsWith("Deuda general:"));
            return null;
        });
    }

    private static UserSession adminSession() {
        return new UserSession(new UserAccount(701L, "admin-ficticio", "unused", UserRole.ADMINISTRATOR, null));
    }

    private static FXMLLoader load(String name) throws Exception {
        FXMLLoader loader = new FXMLLoader(FinancialControllersTest.class.getResource(
                "/com/laexclusiva/ui/fxml/" + name));
        loader.load();
        return loader;
    }

    private static Button button(FXMLLoader loader, String id) {
        return (Button) loader.getNamespace().get(id);
    }

    private static TextField text(FXMLLoader loader, String id) {
        return (TextField) loader.getNamespace().get(id);
    }

    private static Label label(FXMLLoader loader, String id) {
        return (Label) loader.getNamespace().get(id);
    }

    @SuppressWarnings("unchecked")
    private static TableView<Object> table(FXMLLoader loader, String id) {
        return (TableView<Object>) loader.getNamespace().get(id);
    }

    private static CashMovement existingCashMovement() {
        return new CashMovement(1, CashDirection.INCOME, CashCategory.OTHER_INCOME, new BigDecimal("7.00"),
                LocalDate.now().atTime(8, 0), "Ingreso ficticio anterior", "existing-cash-key",
                "MANUAL", "existing-cash-source");
    }

    private static void assertUnavailableLabels(FXMLLoader loader) {
        for (String id : List.of("invoicedSalesLabel", "saleReceiptsLabel", "generalDebtReceiptsLabel",
                "otherIncomeLabel", "expensesLabel", "cashInLabel", "netCashLabel")) {
            assertTrue(label(loader, id).getText().contains("no disponible"), id);
        }
    }

    private static void runOnFx(java.util.concurrent.Callable<Void> callback) throws Exception {
        FutureTask<Void> task = new FutureTask<>(callback);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    private static final class UncertainCashWorkflow implements CashWorkflow {
        private final CashWorkflow delegate;
        private final java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        private boolean throwAfterNextWrite;

        private UncertainCashWorkflow(CashWorkflow delegate) { this.delegate = delegate; }

        @Override
        public CashMovement registerMovement(CashDirection direction, CashCategory category, BigDecimal amount,
                                             LocalDateTime occurredAt, String description, String key) {
            keys.add(key);
            CashMovement result = delegate.registerMovement(direction, category, amount, occurredAt, description, key);
            if (throwAfterNextWrite) {
                throwAfterNextWrite = false;
                throw new IllegalStateException("Respuesta perdida luego de guardar.");
            }
            return result;
        }

        @Override public List<CashMovement> listMovements(LocalDate from, LocalDate through, CashCategory category) {
            return delegate.listMovements(from, through, category);
        }
        @Override public CashSummary summarize(LocalDate from, LocalDate through) {
            return delegate.summarize(from, through);
        }
        @Override public boolean supportsDurableIdempotency() { return delegate.supportsDurableIdempotency(); }
    }

    private static final class CountingCashWorkflow implements CashWorkflow {
        private int writeCalls;
        private int listCalls;
        private int summaryCalls;

        @Override
        public CashMovement registerMovement(CashDirection direction, CashCategory category, BigDecimal amount,
                                             LocalDateTime occurredAt, String description, String key) {
            writeCalls++;
            throw new AssertionError("Una operación no autorizada alcanzó el caso de uso de caja.");
        }
        @Override public List<CashMovement> listMovements(LocalDate from, LocalDate through, CashCategory category) {
            listCalls++;
            throw new AssertionError("Una consulta no autorizada alcanzó el caso de uso de caja.");
        }
        @Override public CashSummary summarize(LocalDate from, LocalDate through) {
            summaryCalls++;
            throw new AssertionError("Un resumen no autorizado alcanzó el caso de uso de caja.");
        }
        @Override public boolean supportsDurableIdempotency() { return false; }
    }

    private static final class ControlledCashWorkflow implements CashWorkflow {
        private final java.util.ArrayList<CashMovement> records = new java.util.ArrayList<>();
        private int writeCalls;
        private int listCalls;
        private int summaryCalls;
        private boolean failNextList;
        private boolean failNextSummary;

        @Override
        public CashMovement registerMovement(CashDirection direction, CashCategory category, BigDecimal amount,
                                             LocalDateTime occurredAt, String description, String key) {
            writeCalls++;
            CashMovement saved = new CashMovement(records.size() + 1L, direction, category, amount,
                    occurredAt, description, key, "MANUAL", key);
            records.add(saved);
            return saved;
        }

        @Override
        public List<CashMovement> listMovements(LocalDate from, LocalDate through, CashCategory category) {
            listCalls++;
            if (failNextList) {
                failNextList = false;
                throw new IllegalStateException("lectura de movimientos simulada no disponible");
            }
            return List.copyOf(records);
        }

        @Override
        public CashSummary summarize(LocalDate from, LocalDate through) {
            summaryCalls++;
            if (failNextSummary) {
                failNextSummary = false;
                throw new IllegalStateException("lectura del resumen simulada no disponible");
            }
            return new CashSummary(new BigDecimal("100.00"), new BigDecimal("40.00"),
                    new BigDecimal("10.00"), new BigDecimal("7.00"), new BigDecimal("2.00"));
        }

        private void resetCounters() {
            writeCalls = 0;
            listCalls = 0;
            summaryCalls = 0;
        }

        @Override public boolean supportsDurableIdempotency() { return true; }
    }

    private static final class UncertainGeneralDebtWorkflow implements CustomerGeneralDebtWorkflow {
        private final CustomerGeneralDebtWorkflow delegate;
        private final java.util.ArrayList<String> paymentKeys = new java.util.ArrayList<>();
        private boolean throwAfterNextPayment;

        private UncertainGeneralDebtWorkflow(CustomerGeneralDebtWorkflow delegate) { this.delegate = delegate; }

        @Override public CustomerDebtBreakdown findBalance(String customerId) {
            return delegate.findBalance(customerId);
        }
        @Override public GeneralDebtMovement registerDebt(String customerId, BigDecimal amount, String category,
                                                          String description, String idempotencyKey) {
            return delegate.registerDebt(customerId, amount, category, description, idempotencyKey);
        }
        @Override public GeneralDebtMovement registerPayment(String customerId, BigDecimal amount, String category,
                                                             String description, String idempotencyKey) {
            paymentKeys.add(idempotencyKey);
            GeneralDebtMovement result = delegate.registerPayment(customerId, amount, category, description,
                    idempotencyKey);
            if (throwAfterNextPayment) {
                throwAfterNextPayment = false;
                throw new IllegalStateException("Respuesta perdida después de guardar.");
            }
            return result;
        }
        @Override public List<GeneralDebtMovement> listMovements(String customerId, LocalDate from,
                                                                 LocalDate through, String category) {
            return delegate.listMovements(customerId, from, through, category);
        }
        @Override public boolean supportsDurableIdempotency() { return delegate.supportsDurableIdempotency(); }
    }

    private static final class RefreshFailingGeneralDebtWorkflow implements CustomerGeneralDebtWorkflow {
        private final CustomerGeneralDebtWorkflow delegate;
        private int debtWrites;
        private int paymentWrites;
        private boolean failNextBalance;

        private RefreshFailingGeneralDebtWorkflow(CustomerGeneralDebtWorkflow delegate) {
            this.delegate = delegate;
        }

        @Override public CustomerDebtBreakdown findBalance(String customerId) {
            if (failNextBalance) {
                failNextBalance = false;
                throw new IllegalStateException("consulta simulada de saldo no disponible");
            }
            return delegate.findBalance(customerId);
        }

        @Override public GeneralDebtMovement registerDebt(String customerId, BigDecimal amount, String category,
                                                          String description, String idempotencyKey) {
            debtWrites++;
            GeneralDebtMovement confirmed = delegate.registerDebt(customerId, amount, category, description,
                    idempotencyKey);
            failNextBalance = true;
            return confirmed;
        }

        @Override public GeneralDebtMovement registerPayment(String customerId, BigDecimal amount, String category,
                                                             String description, String idempotencyKey) {
            paymentWrites++;
            GeneralDebtMovement confirmed = delegate.registerPayment(customerId, amount, category, description,
                    idempotencyKey);
            failNextBalance = true;
            return confirmed;
        }

        @Override public List<GeneralDebtMovement> listMovements(String customerId, LocalDate from,
                                                                 LocalDate through, String category) {
            return delegate.listMovements(customerId, from, through, category);
        }

        @Override public boolean supportsDurableIdempotency() { return delegate.supportsDurableIdempotency(); }
    }
}
