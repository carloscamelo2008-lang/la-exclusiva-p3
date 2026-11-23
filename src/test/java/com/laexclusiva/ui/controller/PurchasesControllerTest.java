package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.GoodsReceipt;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.persistence.jdbc.JdbcPersistenceException;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.InMemoryGoodsReceiptRepository;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemoryPurchaseOrderRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.InMemorySupplierRepository;
import com.laexclusiva.service.GoodsReceiptService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.PurchaseOrderService;
import com.laexclusiva.service.SupplierService;
import com.laexclusiva.testutil.IdentityTestFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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

/** Verifica las acciones JavaFX con servicios reales y repositorios en memoria. */
class PurchasesControllerTest {
    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
            // El resto de pruebas de interfaz comparte el mismo toolkit.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void loadsViewAndEmitsOrderWithoutStockThenReceivesInTwoSteps() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader view = fixture.load(fixture.admin);
            assertNotNull(PurchasesController.load(fixture.suppliers, fixture.orders, fixture.receipts,
                    fixture.products, fixture.admin, fixture.identity.authorization()));
            createOrder(view);
            assertTrue(message(view, "messageLabel").contains("Pedido creado"));
            assertTrue(fixture.inventory.findAll().isEmpty());
            openOrder(view);
            queueReceipt(view, "2");
            button(view, "receiveButton").fire();
            assertTrue(message(view, "receiptMessageLabel").contains("Recepción confirmada"));
            assertTrue(message(view, "selectedOrderLabel").contains("PARTIAL"));
            assertEquals(76, fixture.stock());
            queueReceipt(view, "2");
            button(view, "receiveButton").fire();
            assertTrue(message(view, "selectedOrderLabel").contains("COMPLETED"));
            assertEquals(152, fixture.stock());
            button(view, "refreshHistoryButton").fire();
            assertEquals(2, table(view, "historyTable").getItems().size());
            assertEquals(2, fixture.movements.findAll().size());
            return null;
        });
    }

    @Test
    void workerCanReadButCannotUseWriteControls() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader view = fixture.load(fixture.worker);
            for (String id : List.of("createSupplierButton", "toggleSupplierButton", "createOrderButton",
                    "addOrderLineButton", "addReceiptLineButton", "receiveButton")) {
                assertTrue(button(view, id).isDisabled(), id);
                button(view, id).fire();
            }
            assertFalse(button(view, "refreshButton").isDisabled());
            assertEquals(1, table(view, "suppliersTable").getItems().size());
            assertTrue(fixture.orders.findAll(fixture.admin).isEmpty());
            return null;
        });
    }

    @Test
    void keepsConfirmationWhenOnlyTheRefreshFails() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader view = fixture.load(fixture.admin);
            fixture.failCatalogRefresh = true;
            createOrder(view);
            String orderMessage = message(view, "messageLabel");
            assertTrue(orderMessage.contains("Pedido creado"));
            assertTrue(orderMessage.contains("No se pudo actualizar la vista"));
            assertFalse(orderMessage.contains("Resultado incierto"));
            assertTrue(fixture.inventory.findAll().isEmpty());
            fixture.failCatalogRefresh = false;
            button(view, "refreshButton").fire();

            openOrder(view);
            queueReceipt(view, "2");
            fixture.failCatalogRefresh = true;
            button(view, "receiveButton").fire();
            String receiptMessage = message(view, "receiptMessageLabel");
            assertTrue(receiptMessage.contains("Recepción confirmada"));
            assertTrue(receiptMessage.contains("No se pudo actualizar la vista"));
            assertEquals("", message(view, "receiptKeyLabel"));
            assertEquals(76, fixture.stock());
            return null;
        });
    }

    @Test
    void retriesUncertainCommitWithOriginalKeyAndQuantities() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader view = fixture.load(fixture.admin);
            createOrder(view);
            openOrder(view);
            queueReceipt(view, "4");
            fixture.boundary.loseNextReceiptResponse = true;
            button(view, "receiveButton").fire();
            assertTrue(message(view, "receiptMessageLabel").contains("Resultado incierto"));
            var order = fixture.orders.findAll(fixture.admin).get(0);
            GoodsReceipt committed = fixture.receipts.findByPurchaseOrderId(fixture.admin, order.id()).get(0);
            assertTrue(message(view, "receiptKeyLabel").contains(committed.idempotencyKey()));
            assertTrue(button(view, "addReceiptLineButton").isDisabled());
            text(view, "receiptBaskets").setText("1");
            button(view, "refreshButton").fire();
            assertFalse(button(view, "receiveButton").isDisabled());
            assertTrue(message(view, "receiptMessageLabel").contains("Resultado incierto"));
            button(view, "receiveButton").fire();
            assertTrue(message(view, "receiptMessageLabel").contains("Recepción confirmada"));
            assertEquals("", message(view, "receiptKeyLabel"));
            assertEquals(152, fixture.stock());
            assertEquals(List.of(committed), fixture.receipts.findByPurchaseOrderId(fixture.admin, order.id()));
            assertEquals(1, fixture.movements.findAll().size());
            return null;
        });
    }

    private static void createOrder(FXMLLoader view) {
        ((ComboBox<?>) view.getNamespace().get("supplierCombo")).getSelectionModel().selectFirst();
        ((ComboBox<?>) view.getNamespace().get("productCombo")).getSelectionModel().selectFirst();
        text(view, "orderBaskets").setText("4");
        assertTrue(message(view, "equivalenceLabel").contains("152"));
        button(view, "addOrderLineButton").fire();
        button(view, "createOrderButton").fire();
    }

    private static void openOrder(FXMLLoader view) {
        table(view, "ordersTable").getSelectionModel().selectFirst();
        button(view, "detailButton").fire();
    }

    private static void queueReceipt(FXMLLoader view, String baskets) {
        table(view, "orderLinesTable").getSelectionModel().selectFirst();
        text(view, "receiptBaskets").setText(baskets);
        button(view, "addReceiptLineButton").fire();
    }

    private static Button button(FXMLLoader view, String id) {
        return (Button) view.getNamespace().get(id);
    }

    private static TextField text(FXMLLoader view, String id) {
        return (TextField) view.getNamespace().get(id);
    }

    private static TableView<?> table(FXMLLoader view, String id) {
        return (TableView<?>) view.getNamespace().get(id);
    }

    private static String message(FXMLLoader view, String id) {
        return ((Label) view.getNamespace().get(id)).getText();
    }

    private static <T> T onFxThread(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private static final class Fixture {
        private final IdentityTestFixture identity = new IdentityTestFixture();
        private final UserSession admin = identity.issue(UserRole.ADMINISTRATOR).session();
        private final UserSession worker = identity.issue(UserRole.WORKER).session();
        private final InMemoryProductRepository catalog = new InMemoryProductRepository();
        private final InMemoryInventoryRepository inventory = new InMemoryInventoryRepository();
        private final InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
        private final TestBoundary boundary = new TestBoundary();
        private boolean failCatalogRefresh;
        private final ProductService products = new ProductService(catalog) {
            @Override
            public List<BeerProduct> listAll() {
                if (failCatalogRefresh) throw new JdbcPersistenceException("Fallo de lectura simulado.");
                return super.listAll();
            }
        };
        private final SupplierService suppliers;
        private final PurchaseOrderService orders;
        private final GoodsReceiptService receipts;

        private Fixture() {
            var product = new BeerProduct("P38", "Producto ficticio", 330, 38,
                    ContainerType.NON_RETURNABLE_CAN, null);
            for (SalePresentation presentation : List.of(
                    SalePresentation.UNIT, SalePresentation.HALF_BASKET, SalePresentation.FULL_BASKET)) {
                product.definePrice(presentation, BigDecimal.ONE);
            }
            products.register(product);
            var supplierRepository = new InMemorySupplierRepository();
            var orderRepository = new InMemoryPurchaseOrderRepository();
            var authorization = identity.authorization();
            suppliers = new SupplierService(supplierRepository, authorization, boundary);
            orders = new PurchaseOrderService(orderRepository, supplierRepository, products, authorization, boundary);
            receipts = new GoodsReceiptService(orderRepository, new InMemoryGoodsReceiptRepository(), products,
                    authorization, boundary, inventory, movements);
            suppliers.create(admin, "Proveedor ficticio", null, null);
        }

        private FXMLLoader load(UserSession session) throws Exception {
            var loader = new FXMLLoader(PurchasesControllerTest.class.getResource(
                    "/com/laexclusiva/ui/fxml/purchases_view.fxml"));
            loader.load();
            PurchasesController controller = loader.getController();
            controller.configure(suppliers, orders, receipts, products, session, identity.authorization());
            return loader;
        }

        private int stock() {
            return inventory.findByProductId("P38").orElseThrow().totalUnits();
        }
    }

    private static final class TestBoundary implements TransactionBoundary {
        private boolean loseNextReceiptResponse;

        @Override
        public <T> T inTransaction(Supplier<T> operation) {
            T result = operation.get();
            if (loseNextReceiptResponse && result instanceof GoodsReceipt) {
                loseNextReceiptResponse = false;
                throw new JdbcPersistenceException("Respuesta de confirmación perdida.");
            }
            return result;
        }
    }
}
