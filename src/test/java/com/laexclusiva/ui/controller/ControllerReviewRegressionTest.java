package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.service.SellerService;
import com.laexclusiva.testutil.IdentityTestFixture;
import com.laexclusiva.ui.ApplicationServices;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regresiones de permisos y de confirmación visual en los controladores JavaFX. */
@SuppressWarnings("unchecked")
class ControllerReviewRegressionTest {
    private static final IdentityTestFixture IDENTITY = new IdentityTestFixture();

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
    void confirmedPaymentWithRefreshFailureIsNotReportedAsFailedAndRefreshDoesNotPayAgain() throws Exception {
        runOnFx(() -> {
            Customer customer = customer("CUS-PAYMENT-REFRESH", "100.00");
            FailingCustomerService customers = customerService(customer);
            IdempotentWorkflow sales = new IdempotentWorkflow(sale(customer, "SALE-PAYMENT-REFRESH", "100.00"));
            FXMLLoader loader = load("portfolio_view.fxml");
            PortfolioController controller = loader.getController();
            controller.configure(customers, sales, adminSession());
            controller.showPortfolio(customers.getAllCustomers());
            selectFirstSale(loader);
            text(loader, "paymentAmountField").setText("25.00");

            customers.failNextList = true;
            button(loader, "Registrar abono").fire();

            String message = label(loader, "messageLabel").getText();
            assertEquals(1, sales.committedPaymentCount);
            assertEquals(new BigDecimal("25.00"), sales.sale.calculateTotalPaid());
            assertTrue(message.contains("Abono registrado"), message);
            assertTrue(message.contains("Actualizar cartera"), message);
            assertFalse(message.contains("No se registró"), message);

            button(loader, "Actualizar cartera").fire();
            assertEquals(1, sales.committedPaymentCount,
                    "La actualización de pantalla no debe invocar de nuevo el pago.");
            assertTrue(label(loader, "messageLabel").getText().contains("actualizada"));
            return null;
        });
    }

    @Test
    void uncertainPaymentRetryReusesKeyAndCannotSwitchOperation() throws Exception {
        runOnFx(() -> {
            Customer customer = customer("CUS-PAYMENT-UNCERTAIN", "100.00");
            CustomerService customers = customerService(customer);
            IdempotentWorkflow sales = new IdempotentWorkflow(sale(customer, "SALE-PAYMENT-UNCERTAIN", "100.00"));
            FXMLLoader loader = load("portfolio_view.fxml");
            PortfolioController controller = loader.getController();
            controller.configure(customers, sales, adminSession());
            controller.showPortfolio(customers.getAllCustomers());
            selectFirstSale(loader);
            text(loader, "paymentAmountField").setText("20.00");
            sales.throwAfterNextPayment = true;

            button(loader, "Registrar abono").fire();
            String uncertainMessage = label(loader, "messageLabel").getText();
            assertTrue(uncertainMessage.contains("No se pudo confirmar"), uncertainMessage);
            String originalKey = sales.keys.get(0);

            text(loader, "paymentAmountField").setText("21.00");
            button(loader, "Registrar abono").fire();
            assertEquals(1, sales.keys.size(), "No se debe enviar una operación distinta mientras la anterior es incierta.");

            text(loader, "paymentAmountField").setText("20.00");
            button(loader, "Registrar abono").fire();
            assertEquals(2, sales.keys.size());
            assertEquals(originalKey, sales.keys.get(1));
            assertEquals(1, sales.committedPaymentCount);
            assertEquals(new BigDecimal("20.00"), sales.sale.calculateTotalPaid());
            assertTrue(label(loader, "messageLabel").getText().contains("Abono registrado"));
            return null;
        });
    }

    @Test
    void portfolioQueryFailureClearsPartialStateAndCanBeRetriedWithoutPayment() throws Exception {
        runOnFx(() -> {
            Customer customer = customer("CUS-PORTFOLIO-RETRY", "100.00");
            CustomerService customers = customerService(customer);
            IdempotentWorkflow sales = new IdempotentWorkflow(sale(customer, "SALE-PORTFOLIO-RETRY", "100.00"));
            FXMLLoader loader = load("portfolio_view.fxml");
            PortfolioController controller = loader.getController();
            controller.configure(customers, sales, adminSession());
            controller.showPortfolio(customers.getAllCustomers());
            sales.failNextDebtQuery = true;

            button(loader, "Actualizar cartera").fire();
            assertTrue(label(loader, "messageLabel").getText().contains("No se pudo consultar"));
            assertEquals("Deuda de ventas: no disponible", label(loader, "salesDebtLabel").getText());
            assertEquals(0, table(loader, "salesTable").getItems().size());
            assertNull(((ComboBox<?>) loader.getNamespace().get("customerCombo")).getValue());

            button(loader, "Actualizar cartera").fire();
            assertTrue(label(loader, "messageLabel").getText().contains("actualizada"));
            assertEquals(1, table(loader, "salesTable").getItems().size());
            assertEquals(0, sales.paymentAttempts);
            return null;
        });
    }

    @Test
    void confirmedSaleWithHistoryRefreshFailureKeepsTruthfulMessageAndDoesNotRepeatSale() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            Seller seller = services.sellers().registerSeller("Vendedor venta", "3000000201", "Calle 1");
            BeerProduct product = services.products().listAll().get(0);
            services.inventory().addStock(product.getId(), 0, 2, "Fixture de regresión UI");
            FailingHistoryWorkflow sales = new FailingHistoryWorkflow(services.sales());

            FXMLLoader loader = load("sales_view.fxml");
            SalesController controller = loader.getController();
            controller.configure(services.products(), services.inventory(), services.customers(),
                    services.sellers(), sales, adminSession());
            ((ComboBox<BeerProduct>) loader.getNamespace().get("productCombo")).setValue(product);
            ((ComboBox<SalePresentation>) loader.getNamespace().get("presentationCombo"))
                    .setValue(SalePresentation.UNIT);
            ((ComboBox<Seller>) loader.getNamespace().get("sellerCombo")).setValue(seller);
            text(loader, "initialPaymentField").setText(product.priceFor(SalePresentation.UNIT).toPlainString());
            button(loader, "Agregar línea").fire();
            sales.failNextHistory = true;

            button(loader, "Registrar venta").fire();
            String message = label(loader, "messageLabel").getText();
            assertEquals(1, sales.saleRegistrations);
            assertEquals(1, services.sales().listAll().size(), "La venta ya quedó guardada en el servicio.");
            assertEquals(0, table(loader, "cartTable").getItems().size(),
                    "El carrito se limpia solo tras recibir confirmación del flujo de escritura.");
            assertTrue(message.contains("Venta registrada"), message);
            assertTrue(message.contains("Actualizar historial"), message);
            assertFalse(message.contains("La venta no se registró"), message);

            button(loader, "Actualizar historial").fire();
            assertEquals(1, sales.saleRegistrations, "Actualizar el historial no registra una segunda venta.");
            assertEquals(1, table(loader, "historyTable").getItems().size());
            return null;
        });
    }

    @Test
    void insufficientStockKeepsCartAllowsCorrectionAndUsesANewOperationKey() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            Seller seller = services.sellers().registerSeller("Vendedor de stock", "3000000211", "Calle 11");
            String customerId = "CUS-STOCK-RETRY";
            services.customers().registerCustomer(customerId,
                    "Cliente de stock", "3000000212", "Dirección 11");
            Customer customer = services.customers().getCustomerById(customerId);
            BeerProduct product = services.products().listAll().get(0);
            services.inventory().addStock(product.getId(), 0, 1, "Fixture de stock insuficiente");
            StockRejectedOnceWorkflow sales = new StockRejectedOnceWorkflow(services.sales(), product.getId());
            FXMLLoader loader = load("sales_view.fxml");
            ((SalesController) loader.getController()).configure(services.products(), services.inventory(),
                    services.customers(), services.sellers(), sales, adminSession());
            ((ComboBox<BeerProduct>) loader.getNamespace().get("productCombo")).setValue(product);
            ((ComboBox<SalePresentation>) loader.getNamespace().get("presentationCombo"))
                    .setValue(SalePresentation.UNIT);
            ((ComboBox<Seller>) loader.getNamespace().get("sellerCombo")).setValue(seller);
            ((ComboBox<Customer>) loader.getNamespace().get("customerCombo")).setValue(customer);
            text(loader, "initialPaymentField").setText("0");
            text(loader, "quantityField").setText("2");
            button(loader, "Agregar línea").fire();
            String rejectedKey = label(loader, "saleOperationKeyLabel").getText();

            button(loader, "Registrar venta").fire();

            String rejection = label(loader, "messageLabel").getText();
            assertTrue(rejection.contains("existencias insuficientes"), rejection);
            assertTrue(rejection.contains("carrito se conserva"), rejection);
            assertEquals(1, table(loader, "cartTable").getItems().size());
            assertEquals(0, services.sales().listAll().size());
            assertEquals(1, services.inventory().getStock(product.getId()).totalUnits());
            assertEquals(0, services.customers().getCustomerById(customer.getId()).getBalance()
                    .compareTo(BigDecimal.ZERO));
            assertEquals(0, services.inventory().movementsOf(product.getId()).stream()
                    .filter(movement -> movement.type() == com.laexclusiva.model.MovementType.EXIT).count());
            String correctedKey = label(loader, "saleOperationKeyLabel").getText();
            assertNotEquals(rejectedKey, correctedKey, "Una nueva carga debe tener otra referencia idempotente.");

            table(loader, "cartTable").getSelectionModel().selectFirst();
            button(loader, "Quitar seleccionada").fire();
            text(loader, "quantityField").setText("1");
            button(loader, "Agregar línea").fire();
            button(loader, "Registrar venta").fire();

            assertEquals(2, sales.keys.size());
            assertNotEquals(sales.keys.get(0), sales.keys.get(1));
            assertEquals(1, services.sales().listAll().size());
            assertEquals(0, services.inventory().getStock(product.getId()).totalUnits());
            assertEquals(0, services.customers().getCustomerById(customer.getId()).getBalance()
                    .compareTo(product.priceFor(SalePresentation.UNIT)));
            assertTrue(label(loader, "messageLabel").getText().contains("Venta registrada"));
            assertEquals(0, table(loader, "cartTable").getItems().size());
            return null;
        });
    }

    @Test
    void stockRejectionOnRetryDoesNotReleaseAnEarlierUncertainSaleKey() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            Seller seller = services.sellers().registerSeller("Vendedor incierto", "3000000213", "Calle 13");
            BeerProduct product = services.products().listAll().get(0);
            services.inventory().addStock(product.getId(), 0, 1, "Fixture de respuesta incierta");
            UncertainSaleWorkflow sales = new UncertainSaleWorkflow(services.sales());
            FXMLLoader loader = load("sales_view.fxml");
            ((SalesController) loader.getController()).configure(services.products(), services.inventory(),
                    services.customers(), services.sellers(), sales, adminSession());
            ((ComboBox<BeerProduct>) loader.getNamespace().get("productCombo")).setValue(product);
            ((ComboBox<SalePresentation>) loader.getNamespace().get("presentationCombo"))
                    .setValue(SalePresentation.UNIT);
            ((ComboBox<Seller>) loader.getNamespace().get("sellerCombo")).setValue(seller);
            text(loader, "initialPaymentField").setText(product.priceFor(SalePresentation.UNIT).toPlainString());
            button(loader, "Agregar línea").fire();
            String originalReference = label(loader, "saleOperationKeyLabel").getText();
            sales.throwAfterNextSale = true;

            button(loader, "Registrar venta").fire();
            assertTrue(label(loader, "messageLabel").getText().contains("resultado de la venta"));
            sales.throwStockOnNextSale = true;
            button(loader, "Registrar venta").fire();

            String message = label(loader, "messageLabel").getText();
            assertTrue(message.contains("existencias insuficientes"), message);
            assertTrue(message.contains("solicitud anterior aún tiene resultado incierto"), message);
            assertEquals(originalReference, label(loader, "saleOperationKeyLabel").getText());
            assertEquals(2, sales.keys.size());
            assertEquals(sales.keys.get(0), sales.keys.get(1));

            text(loader, "initialPaymentField").setText("0");
            button(loader, "Registrar venta").fire();
            assertEquals(2, sales.keys.size(), "Una carga distinta no se envía mientras la anterior sea incierta.");
            assertEquals(originalReference, label(loader, "saleOperationKeyLabel").getText());
            return null;
        });
    }

    @Test
    void uncertainSaleKeepsVisibleKeyAndRequiresTheSamePayloadForRetry() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            Seller seller = services.sellers().registerSeller("Vendedor de reintento", "3000000202", "Calle 2");
            BeerProduct product = services.products().listAll().get(0);
            services.inventory().addStock(product.getId(), 0, 2, "Fixture de idempotencia JavaFX");
            UncertainSaleWorkflow sales = new UncertainSaleWorkflow(services.sales());
            FXMLLoader loader = load("sales_view.fxml");
            SalesController controller = loader.getController();
            controller.configure(services.products(), services.inventory(), services.customers(),
                    services.sellers(), sales, adminSession());
            ((ComboBox<BeerProduct>) loader.getNamespace().get("productCombo")).setValue(product);
            ((ComboBox<SalePresentation>) loader.getNamespace().get("presentationCombo"))
                    .setValue(SalePresentation.UNIT);
            ((ComboBox<Seller>) loader.getNamespace().get("sellerCombo")).setValue(seller);
            text(loader, "initialPaymentField").setText(product.priceFor(SalePresentation.UNIT).toPlainString());
            button(loader, "Agregar línea").fire();
            String operationReference = label(loader, "saleOperationKeyLabel").getText();
            sales.throwAfterNextSale = true;

            button(loader, "Registrar venta").fire();
            assertEquals(1, services.sales().listAll().size(), "El primer intento pudo confirmarse en memoria.");
            assertEquals(1, services.inventory().getStock(product.getId()).totalUnits());
            assertEquals(1, sales.keys.size());
            assertTrue(label(loader, "messageLabel").getText().contains("referencia se conservan"));
            assertEquals(operationReference, label(loader, "saleOperationKeyLabel").getText());

            text(loader, "initialPaymentField").setText("0");
            button(loader, "Registrar venta").fire();
            assertEquals(1, sales.keys.size(), "No se envía una solicitud distinta con la clave incierta.");

            text(loader, "initialPaymentField").setText(product.priceFor(SalePresentation.UNIT).toPlainString());
            button(loader, "Registrar venta").fire();
            assertEquals(2, sales.keys.size());
            assertEquals(sales.keys.get(0), sales.keys.get(1));
            assertEquals(1, services.sales().listAll().size());
            assertEquals(1, services.inventory().getStock(product.getId()).totalUnits());
            assertTrue(label(loader, "messageLabel").getText().contains("Venta registrada"));
            assertNotEquals(operationReference, label(loader, "saleOperationKeyLabel").getText());
            return null;
        });
    }

    @Test
    void writeHandlersRecheckSessionAndPermissionsBeforeCallingServices() throws Exception {
        runOnFx(() -> {
            CountingCustomerService customers = new CountingCustomerService();
            CountingSellerService sellers = new CountingSellerService();
            CountingProductService products = new CountingProductService();
            CountingInventoryService inventory = new CountingInventoryService(products);
            CountingWorkflow sales = new CountingWorkflow();
            UserSession workerSession = IDENTITY.issue(UserRole.WORKER).session();

            FXMLLoader customerLoader = load("customers_view.fxml");
            ((CustomerController) customerLoader.getController()).configure(customers, workerSession);
            workerSession.invalidate();
            button(customerLoader, "Registrar cliente").fire();

            UserSession activeWorker = IDENTITY.issue(UserRole.WORKER).session();
            FXMLLoader sellerLoader = load("sellers_view.fxml");
            ((SellerController) sellerLoader.getController()).configure(sellers, activeWorker);
            button(sellerLoader, "Registrar vendedor").fire();

            FXMLLoader inventoryLoader = load("inventory_view.fxml");
            ((InventoryController) inventoryLoader.getController()).configure(products, inventory, activeWorker);
            button(inventoryLoader, "Registrar producto").fire();
            button(inventoryLoader, "Registrar entrada").fire();

            FXMLLoader portfolioLoader = load("portfolio_view.fxml");
            ((PortfolioController) portfolioLoader.getController()).configure(customers, sales, activeWorker);
            button(portfolioLoader, "Registrar abono").fire();

            FXMLLoader salesLoader = load("sales_view.fxml");
            ((SalesController) salesLoader.getController()).configure(products, inventory, customers,
                    sellers, sales, activeWorker);
            button(salesLoader, "Registrar venta").fire();

            assertAll(
                    () -> assertEquals(0, customers.customerWrites),
                    () -> assertEquals(0, sellers.sellerWrites),
                    () -> assertEquals(0, products.productWrites),
                    () -> assertEquals(0, inventory.stockWrites),
                    () -> assertEquals(0, sales.paymentAttempts),
                    () -> assertEquals(0, sales.saleRegistrations)
            );
            return null;
        });
    }

    @Test
    void workerCannotRegisterSaleUnderAnotherSellerEvenIfSelectionIsChangedProgrammatically() throws Exception {
        runOnFx(() -> {
            ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
            Seller assigned = services.sellers().registerSeller("Asignado", "3000000202", "Calle 2");
            Seller other = services.sellers().registerSeller("Otro", "3000000203", "Calle 3");
            BeerProduct product = services.products().listAll().get(0);
            CountingWorkflow sales = new CountingWorkflow();
            FXMLLoader loader = load("sales_view.fxml");
            SalesController controller = loader.getController();
            controller.configure(services.products(), services.inventory(), services.customers(),
                    services.sellers(), sales,
                    IDENTITY.issue("assigned-worker", UserRole.WORKER, assigned.getId()).session());
            ((ComboBox<BeerProduct>) loader.getNamespace().get("productCombo")).setValue(product);
            ((ComboBox<Seller>) loader.getNamespace().get("sellerCombo")).setValue(other);
            ((ComboBox<SalePresentation>) loader.getNamespace().get("presentationCombo"))
                    .setValue(SalePresentation.UNIT);
            text(loader, "initialPaymentField").setText(product.priceFor(SalePresentation.UNIT).toPlainString());
            button(loader, "Agregar línea").fire();
            button(loader, "Registrar venta").fire();

            assertEquals(assigned.getId(), ((Seller) ((ComboBox<?>) loader.getNamespace()
                    .get("sellerCombo")).getItems().get(0)).getId());
            assertEquals(0, sales.saleRegistrations);
            assertTrue(label(loader, "messageLabel").getText().contains("vendedor asignado"));
            return null;
        });
    }

    private static Customer customer(String id, String balance) {
        return new Customer(id, "Cliente de prueba", "3000000000", "Dirección de prueba", new BigDecimal(balance));
    }

    private static FailingCustomerService customerService(Customer customer) {
        CustomerRepository repository = new CustomerRepository();
        repository.save(customer);
        return new FailingCustomerService(repository);
    }

    private static Sale sale(Customer customer, String id, String total) {
        BeerProduct product = new BeerProduct("P-" + id, "Producto ficticio", 330, 24,
                ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal(total));
        Sale initial = new Sale(LocalDateTime.now(),
                new Seller("SELLER-" + id, "Vendedor ficticio", "3000000001", "Dirección"),
                customer, List.of(new SaleLine(product, SalePresentation.UNIT, 1)), BigDecimal.ZERO);
        return Sale.reconstitute(id, initial.getCreatedAt(), initial.getSeller(), customer,
                initial.getLines(), initial.getPayments());
    }

    private static UserSession adminSession() {
        return IDENTITY.issue(UserRole.ADMINISTRATOR).session();
    }

    private static FXMLLoader load(String name) throws Exception {
        FXMLLoader loader = new FXMLLoader(ControllerReviewRegressionTest.class.getResource(
                "/com/laexclusiva/ui/fxml/" + name));
        loader.load();
        Object controller = loader.getController();
        if (controller instanceof CustomerController value) value.setAuthorizationService(IDENTITY.authorization());
        if (controller instanceof SellerController value) value.setAuthorizationService(IDENTITY.authorization());
        if (controller instanceof InventoryController value) value.setAuthorizationService(IDENTITY.authorization());
        if (controller instanceof SalesController value) value.setAuthorizationService(IDENTITY.authorization());
        if (controller instanceof PortfolioController value) value.setAuthorizationService(IDENTITY.authorization());
        if (controller instanceof CashController value) value.setAuthorizationService(IDENTITY.authorization());
        return loader;
    }

    private static void selectFirstSale(FXMLLoader loader) {
        @SuppressWarnings("unchecked")
        TableView<Sale> table = (TableView<Sale>) loader.getNamespace().get("salesTable");
        table.getSelectionModel().selectFirst();
    }

    private static Button button(FXMLLoader loader, String text) {
        String id = switch (text) {
            case "Registrar abono" -> "registerPaymentButton";
            case "Actualizar cartera" -> "refreshPortfolioButton";
            case "Agregar línea" -> "addLineButton";
            case "Quitar seleccionada" -> "removeLineButton";
            case "Registrar venta" -> "registerSaleButton";
            case "Actualizar historial" -> "refreshHistoryButton";
            case "Registrar cliente" -> "registerCustomerButton";
            case "Registrar vendedor" -> "registerSellerButton";
            case "Registrar producto" -> "registerProductButton";
            case "Registrar entrada" -> "registerStockButton";
            default -> throw new AssertionError("Botón sin fx:id conocido: " + text);
        };
        Object result = loader.getNamespace().get(id);
        assertNotNull(result, "No se encontró el botón " + text + " (" + id + ").");
        return (Button) result;
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

    private static void runOnFx(java.util.concurrent.Callable<Void> callback) throws Exception {
        FutureTask<Void> task = new FutureTask<>(callback);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    private static final class FailingCustomerService extends CustomerService {
        private boolean failNextList;

        private FailingCustomerService(CustomerRepository repository) { super(repository); }

        @Override
        public List<Customer> getAllCustomers() {
            if (failNextList) {
                failNextList = false;
                throw new IllegalStateException("conexión de consulta no disponible");
            }
            return super.getAllCustomers();
        }
    }

    private static class IdempotentWorkflow implements SalesWorkflow {
        private Sale sale;
        private final Map<String, BigDecimal> paymentKeys = new HashMap<>();
        private final List<String> keys = new ArrayList<>();
        private int committedPaymentCount;
        private int paymentAttempts;
        private boolean throwAfterNextPayment;
        private boolean failNextDebtQuery;

        private IdempotentWorkflow(Sale sale) { this.sale = sale; }

        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment) {
            throw new UnsupportedOperationException();
        }

        @Override public Sale registerPayment(String saleId, BigDecimal amount, String idempotencyKey) {
            paymentAttempts++;
            keys.add(idempotencyKey);
            BigDecimal prior = paymentKeys.get(idempotencyKey);
            if (prior == null) {
                sale.registerPayment(new SalePayment(amount, LocalDateTime.now()));
                paymentKeys.put(idempotencyKey, amount);
                committedPaymentCount++;
            } else if (prior.compareTo(amount) != 0) {
                throw new IllegalArgumentException("La clave ya se usó con otro importe.");
            }
            if (throwAfterNextPayment) {
                throwAfterNextPayment = false;
                throw new IllegalStateException("respuesta perdida después de guardar");
            }
            return sale.copy();
        }

        @Override public Sale findById(String saleId) { return sale.copy(); }
        @Override public List<Sale> listAll() { return List.of(sale.copy()); }
        @Override public BigDecimal outstandingSalesDebt(String customerId) {
            if (failNextDebtQuery) {
                failNextDebtQuery = false;
                throw new IllegalStateException("base de datos no disponible");
            }
            return sale.calculateBalance();
        }
        @Override public boolean supportsDurableIdempotency() { return true; }
    }

    private static final class FailingHistoryWorkflow implements SalesWorkflow {
        private final SalesWorkflow delegate;
        private boolean failNextHistory;
        private int saleRegistrations;

        private FailingHistoryWorkflow(SalesWorkflow delegate) { this.delegate = delegate; }

        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment) {
            saleRegistrations++;
            return delegate.registerSale(lines, sellerId, customerId, initialPayment);
        }
        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment, String key) {
            saleRegistrations++;
            return delegate.registerSale(lines, sellerId, customerId, initialPayment, key);
        }
        @Override public Sale registerPayment(String saleId, BigDecimal amount, String key) {
            return delegate.registerPayment(saleId, amount, key);
        }
        @Override public Sale findById(String saleId) { return delegate.findById(saleId); }
        @Override public List<Sale> listAll() {
            if (failNextHistory) {
                failNextHistory = false;
                throw new IllegalStateException("falló la consulta del historial");
            }
            return delegate.listAll();
        }
        @Override public BigDecimal outstandingSalesDebt(String customerId) {
            return delegate.outstandingSalesDebt(customerId);
        }
        @Override public boolean supportsDurableIdempotency() { return delegate.supportsDurableIdempotency(); }
    }

    private static final class UncertainSaleWorkflow implements SalesWorkflow {
        private final SalesWorkflow delegate;
        private final List<String> keys = new ArrayList<>();
        private boolean throwAfterNextSale;
        private boolean throwStockOnNextSale;

        private UncertainSaleWorkflow(SalesWorkflow delegate) { this.delegate = delegate; }

        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment) {
            return delegate.registerSale(lines, sellerId, customerId, initialPayment);
        }
        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment, String key) {
            keys.add(key);
            if (throwStockOnNextSale) {
                throwStockOnNextSale = false;
                throw new InsufficientStockException("P-UNCERTAIN", 2, 1);
            }
            Sale saved = delegate.registerSale(lines, sellerId, customerId, initialPayment, key);
            if (throwAfterNextSale) {
                throwAfterNextSale = false;
                throw new IllegalStateException("Respuesta perdida después del commit.");
            }
            return saved;
        }
        @Override public Sale registerPayment(String saleId, BigDecimal amount, String key) {
            return delegate.registerPayment(saleId, amount, key);
        }
        @Override public Sale findById(String saleId) { return delegate.findById(saleId); }
        @Override public List<Sale> listAll() { return delegate.listAll(); }
        @Override public BigDecimal outstandingSalesDebt(String customerId) {
            return delegate.outstandingSalesDebt(customerId);
        }
        @Override public boolean supportsDurableIdempotency() { return delegate.supportsDurableIdempotency(); }
    }

    private static final class StockRejectedOnceWorkflow implements SalesWorkflow {
        private final SalesWorkflow delegate;
        private final String productId;
        private final List<String> keys = new ArrayList<>();
        private boolean rejectFirst = true;

        private StockRejectedOnceWorkflow(SalesWorkflow delegate, String productId) {
            this.delegate = delegate;
            this.productId = productId;
        }

        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment) {
            return delegate.registerSale(lines, sellerId, customerId, initialPayment);
        }
        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment, String key) {
            keys.add(key);
            if (rejectFirst) {
                rejectFirst = false;
                throw new InsufficientStockException(productId, 2, 1);
            }
            return delegate.registerSale(lines, sellerId, customerId, initialPayment, key);
        }
        @Override public Sale registerPayment(String saleId, BigDecimal amount, String key) {
            return delegate.registerPayment(saleId, amount, key);
        }
        @Override public Sale findById(String saleId) { return delegate.findById(saleId); }
        @Override public List<Sale> listAll() { return delegate.listAll(); }
        @Override public BigDecimal outstandingSalesDebt(String customerId) {
            return delegate.outstandingSalesDebt(customerId);
        }
        @Override public boolean supportsDurableIdempotency() { return delegate.supportsDurableIdempotency(); }
    }

    private static final class CountingWorkflow implements SalesWorkflow {
        private int saleRegistrations;
        private int paymentAttempts;
        @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                                           BigDecimal initialPayment) {
            saleRegistrations++;
            throw new AssertionError("La escritura no autorizada alcanzó SalesWorkflow.registerSale.");
        }
        @Override public Sale registerPayment(String saleId, BigDecimal amount, String key) {
            paymentAttempts++;
            throw new AssertionError("La escritura no autorizada alcanzó SalesWorkflow.registerPayment.");
        }
        @Override public Sale findById(String saleId) { return null; }
        @Override public List<Sale> listAll() { return List.of(); }
        @Override public BigDecimal outstandingSalesDebt(String customerId) { return BigDecimal.ZERO; }
        @Override public boolean supportsDurableIdempotency() { return false; }
    }

    private static final class CountingCustomerService extends CustomerService {
        private int customerWrites;
        private CountingCustomerService() { super(new CustomerRepository()); }
        @Override public void registerCustomer(String id, String name, String phone, String address) {
            customerWrites++;
            throw new AssertionError("La escritura no autorizada alcanzó CustomerService.");
        }
    }

    private static final class CountingSellerService extends SellerService {
        private int sellerWrites;
        private CountingSellerService() { super(new com.laexclusiva.repository.SellerRepository()); }
        @Override public Seller registerSeller(String name, String phone, String address) {
            sellerWrites++;
            throw new AssertionError("La escritura no autorizada alcanzó SellerService.");
        }
    }

    private static final class CountingProductService extends ProductService {
        private int productWrites;
        private CountingProductService() { super(new InMemoryProductRepository()); }
        @Override public void register(BeerProduct product) {
            productWrites++;
            throw new AssertionError("La escritura no autorizada alcanzó ProductService.");
        }
    }

    private static final class CountingInventoryService extends InventoryService {
        private int stockWrites;
        private CountingInventoryService(ProductService products) {
            super(products, new InMemoryInventoryRepository(), new InMemoryStockMovementRepository());
        }
        @Override public void addStock(String productId, int baskets, int units, String reason) {
            stockWrites++;
            throw new AssertionError("La escritura no autorizada alcanzó InventoryService.");
        }
    }
}
