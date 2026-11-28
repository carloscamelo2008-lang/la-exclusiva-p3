package com.laexclusiva.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.InventoryItem;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockSnapshot;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.InventoryRepository;
import com.laexclusiva.repository.ProductRepository;
import com.laexclusiva.repository.StockMovementRepository;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.ProductImageStore;
import com.laexclusiva.testutil.IdentityTestFixture;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Regresiones JavaFX para distinguir escrituras confirmadas de fallos de refresco del inventario. */
class InventoryControllerReviewRegressionTest {
    private static final IdentityTestFixture IDENTITY = new IdentityTestFixture();
    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Otras pruebas de interfaz pueden haber iniciado JavaFX en este mismo proceso.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void confirmedStockEntryKeepsSuccessWhenRefreshFailsAndManualRefreshOnlyReads() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader loader = loadInventoryView();
            InventoryController controller = loader.getController();
            UserSession session = adminSession();
            controller.configure(fixture.products, fixture.inventory, session);

            @SuppressWarnings("unchecked")
            ComboBox<BeerProduct> productCombo = (ComboBox<BeerProduct>) loader.getNamespace().get("productCombo");
            productCombo.setValue(fixture.catalogProduct);
            text(loader, "fullBasketsField", "0");
            text(loader, "looseUnitsField", "2");
            text(loader, "reasonField", "Entrada ficticia de prueba");
            fixture.stock.failReadAfterNextSave = true;

            ((Button) loader.getNamespace().get("registerStockButton")).fire();

            Label message = (Label) loader.getNamespace().get("messageLabel");
            assertTrue(message.getText().contains("Entrada registrada"));
            assertTrue(message.getText().contains("No se pudo actualizar la vista"));
            assertTrue(message.getText().contains("Actualizar inventario"));
            assertFalse(message.getText().contains("No se registró la entrada"));
            assertEquals(2, fixture.stock.totalUnits(fixture.catalogProduct.getId()));
            assertEquals(1, fixture.stock.saveCalls);
            assertEquals(1, fixture.movements.saved.size());

            ((Button) loader.getNamespace().get("refreshInventoryButton")).fire();

            TableView<?> inventoryTable = (TableView<?>) loader.getNamespace().get("inventoryTable");
            TableView<?> movementTable = (TableView<?>) loader.getNamespace().get("movementTable");
            assertEquals(2, inventoryTable.getColumns().get(2).getCellData(0));
            assertEquals(1, movementTable.getItems().size());
            assertEquals(2, fixture.stock.totalUnits(fixture.catalogProduct.getId()));
            assertEquals(1, fixture.stock.saveCalls, "El refresco no debe repetir la entrada.");
            assertEquals(1, fixture.movements.saved.size(), "El refresco no debe volver a registrar el movimiento.");
            return null;
        });
    }

    @Test
    void confirmedProductRegistrationKeepsSuccessWhenCatalogRefreshFails() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader loader = loadInventoryView();
            InventoryController controller = loader.getController();
            controller.configure(fixture.products, fixture.inventory, adminSession());

            text(loader, "productIdField", "UI-NEW-PRODUCT");
            text(loader, "productNameField", "Producto de prueba");
            text(loader, "volumeField", "330");
            text(loader, "unitsPerBasketField", "24");
            text(loader, "unitPriceField", "100");
            text(loader, "halfBasketPriceField", "1100");
            text(loader, "fullBasketPriceField", "2100");
            fixture.catalog.failListAfterNextSave = true;

            ((Button) loader.getNamespace().get("registerProductButton")).fire();

            Label message = (Label) loader.getNamespace().get("messageLabel");
            assertTrue(message.getText().contains("Producto y precios registrados"));
            assertTrue(message.getText().contains("No se pudo actualizar la vista"));
            assertTrue(message.getText().contains("Actualizar inventario"));
            assertFalse(message.getText().contains("No se registró el producto"));
            assertEquals(1, fixture.catalog.saveCalls);
            assertTrue(fixture.catalog.products.containsKey("UI-NEW-PRODUCT"));

            ((Button) loader.getNamespace().get("refreshInventoryButton")).fire();

            @SuppressWarnings("unchecked")
            ComboBox<BeerProduct> productCombo = (ComboBox<BeerProduct>) loader.getNamespace().get("productCombo");
            assertTrue(productCombo.getItems().stream().anyMatch(product -> product.getId().equals("UI-NEW-PRODUCT")));
            assertEquals(1, fixture.catalog.saveCalls, "La consulta no debe repetir el alta del producto.");
            return null;
        });
    }

    @Test
    void writeHandlersRecheckSessionAuthorizationBeforeCallingServices() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader loader = loadInventoryView();
            InventoryController controller = loader.getController();
            UserSession session = adminSession();
            controller.configure(fixture.products, fixture.inventory, session);
            session.invalidate();

            @SuppressWarnings("unchecked")
            ComboBox<BeerProduct> productCombo = (ComboBox<BeerProduct>) loader.getNamespace().get("productCombo");
            productCombo.setValue(fixture.catalogProduct);
            text(loader, "fullBasketsField", "0");
            text(loader, "looseUnitsField", "2");
            text(loader, "reasonField", "Entrada no autorizada");
            ((Button) loader.getNamespace().get("registerStockButton")).fire();
            assertTrue(((Label) loader.getNamespace().get("messageLabel")).getText().contains("permiso"));
            assertEquals(0, fixture.stock.saveCalls);
            assertTrue(fixture.movements.saved.isEmpty());

            text(loader, "productIdField", "UI-UNAUTHORIZED");
            text(loader, "productNameField", "Producto no autorizado");
            text(loader, "volumeField", "330");
            text(loader, "unitsPerBasketField", "24");
            text(loader, "unitPriceField", "100");
            text(loader, "halfBasketPriceField", "1100");
            text(loader, "fullBasketPriceField", "2100");
            ((Button) loader.getNamespace().get("registerProductButton")).fire();
            assertTrue(((Label) loader.getNamespace().get("messageLabel")).getText().contains("permiso"));
            assertEquals(0, fixture.catalog.saveCalls);
            assertFalse(fixture.catalog.products.containsKey("UI-UNAUTHORIZED"));
            return null;
        });
    }

    @Test
    void administratorCanImportProductPhotoAndCreateNewReturnableContainerGroup() throws Exception {
        onFxThread(() -> {
            Fixture fixture = new Fixture();
            FXMLLoader loader = loadInventoryView();
            InventoryController controller = loader.getController();
            controller.configure(fixture.products, fixture.inventory, adminSession());
            controller.setProductImageStore(new ProductImageStore(temporaryDirectory.resolve("catalog")));
            Path photo = temporaryDirectory.resolve("poker.png");
            try {
                ImageIO.write(new BufferedImage(24, 18, BufferedImage.TYPE_INT_RGB), "png", photo.toFile());
                controller.importProductImage(fixture.catalogProduct, photo);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            BeerProduct persisted = fixture.products.findById(fixture.catalogProduct.getId());
            assertTrue(persisted.getImageFilename().matches("[0-9a-f]{64}\\.png"));
            ImageView image = (ImageView) loader.getNamespace().get("productImagePreview");
            assertTrue(image.getImage() != null);

            @SuppressWarnings("unchecked")
            ComboBox<ContainerType> type = (ComboBox<ContainerType>) loader.getNamespace().get("containerTypeCombo");
            type.setValue(ContainerType.RETURNABLE_SHARED);
            ((TextField) loader.getNamespace().get("productIdField")).setText("GROUP-CREATION");
            ((TextField) loader.getNamespace().get("productNameField")).setText("Envase prueba");
            ((TextField) loader.getNamespace().get("volumeField")).setText("330");
            ((TextField) loader.getNamespace().get("unitsPerBasketField")).setText("24");
            @SuppressWarnings("unchecked")
            ComboBox<String> groups = (ComboBox<String>) loader.getNamespace().get("containerGroupCombo");
            groups.getEditor().setText("grupo-retornable-nuevo");
            ((TextField) loader.getNamespace().get("unitPriceField")).setText("100");
            ((TextField) loader.getNamespace().get("halfBasketPriceField")).setText("1100");
            ((TextField) loader.getNamespace().get("fullBasketPriceField")).setText("2100");
            ((Button) loader.getNamespace().get("registerProductButton")).fire();

            assertEquals("grupo-retornable-nuevo", fixture.products.findById("GROUP-CREATION").getContainerGroup());
            assertTrue(groups.getItems().contains("grupo-retornable-nuevo"));
            return null;
        });
    }

    @Test
    void salesProductSelectionShowsItsPersistedPhotoOrTheFallback() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(InventoryControllerReviewRegressionTest.class.getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            loader.load();
            SalesController controller = loader.getController();
            ProductImageStore store = new ProductImageStore(temporaryDirectory.resolve("sales-images"));
            controller.setProductImageStore(store);
            Path photo = temporaryDirectory.resolve("cola.png");
            try {
                ImageIO.write(new BufferedImage(18, 14, BufferedImage.TYPE_INT_RGB), "png", photo.toFile());
                BeerProduct product = product("SALES-IMAGE", "Cola de prueba");
                product.setImageFilename(store.importImage(photo));
                @SuppressWarnings("unchecked")
                ComboBox<BeerProduct> selection = (ComboBox<BeerProduct>) loader.getNamespace().get("productCombo");
                selection.setValue(product);
                assertEquals("Cola de prueba", ((Label) loader.getNamespace().get("productImageStatus")).getText());
                assertTrue(((ImageView) loader.getNamespace().get("productImagePreview")).getImage() != null);

                product.setImageFilename(null);
                selection.setValue(null);
                selection.setValue(product);
                assertEquals("Sin fotografía", ((Label) loader.getNamespace().get("productImageStatus")).getText());
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            return null;
        });
    }

    private static FXMLLoader loadInventoryView() throws Exception {
        FXMLLoader loader = new FXMLLoader(InventoryControllerReviewRegressionTest.class.getResource(
                "/com/laexclusiva/ui/fxml/inventory_view.fxml"));
        loader.load();
        ((InventoryController) loader.getController()).setAuthorizationService(IDENTITY.authorization());
        return loader;
    }

    private static UserSession adminSession() {
        return IDENTITY.issue(UserRole.ADMINISTRATOR).session();
    }

    private static void text(FXMLLoader loader, String id, String value) {
        ((TextField) loader.getNamespace().get(id)).setText(value);
    }

    private static <T> T onFxThread(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private static BeerProduct product(String id, String name) {
        BeerProduct product = new BeerProduct(id, name, 330, 24, ContainerType.NON_RETURNABLE_CAN, null);
        product.definePrice(SalePresentation.UNIT, new BigDecimal("100.00"));
        product.definePrice(SalePresentation.HALF_BASKET, new BigDecimal("1100.00"));
        product.definePrice(SalePresentation.FULL_BASKET, new BigDecimal("2100.00"));
        return product;
    }

    private static final class Fixture {
        private final BeerProduct catalogProduct = product("UI-PRODUCT", "Producto existente");
        private final Catalog catalog = new Catalog(catalogProduct);
        private final ProductService products = new ProductService(catalog);
        private final Stock stock = new Stock();
        private final Movements movements = new Movements();
        private final InventoryService inventory = new InventoryService(products, stock, movements);
    }

    private static final class Catalog implements ProductRepository {
        private final Map<String, BeerProduct> products = new LinkedHashMap<>();
        private int saveCalls;
        private boolean failListAfterNextSave;
        private boolean failNextList;

        private Catalog(BeerProduct initial) {
            products.put(initial.getId(), initial.copy());
        }

        @Override public void save(BeerProduct product) {
            products.put(product.getId(), product.copy());
            saveCalls++;
            if (failListAfterNextSave) {
                failListAfterNextSave = false;
                failNextList = true;
            }
        }
        @Override public void updatePrice(String id, SalePresentation presentation, BigDecimal price) {
            throw new UnsupportedOperationException();
        }
        @Override public void addMissingPrice(String id, SalePresentation presentation, BigDecimal price) {
            throw new UnsupportedOperationException();
        }
        @Override public void updateImageFilename(String id, String filename) {
            BeerProduct product = products.get(id);
            if (product == null) throw new IllegalArgumentException("Producto ausente.");
            product.setImageFilename(filename);
        }
        @Override public Optional<BeerProduct> findById(String id) {
            return Optional.ofNullable(products.get(id)).map(BeerProduct::copy);
        }
        @Override public List<BeerProduct> findAll() {
            if (failNextList) {
                failNextList = false;
                throw new IllegalStateException("Fallo simulado al consultar el catálogo.");
            }
            return products.values().stream().map(BeerProduct::copy).toList();
        }
        @Override public boolean existsById(String id) { return products.containsKey(id); }
    }

    private static final class Stock implements InventoryRepository {
        private final Map<String, InventoryItem> items = new LinkedHashMap<>();
        private int saveCalls;
        private boolean failReadAfterNextSave;
        private boolean failNextRead;

        @Override public void save(InventoryItem item) {
            items.put(item.getProduct().getId(), item.copy());
            saveCalls++;
            if (failReadAfterNextSave) {
                failReadAfterNextSave = false;
                failNextRead = true;
            }
        }
        @Override public Optional<InventoryItem> findByProductId(String productId) {
            if (failNextRead) {
                failNextRead = false;
                throw new IllegalStateException("Fallo simulado al consultar existencias.");
            }
            return Optional.ofNullable(items.get(productId)).map(InventoryItem::copy);
        }
        @Override public Optional<InventoryItem> findByProductIdForUpdate(String productId) {
            return findByProductId(productId);
        }
        @Override public List<InventoryItem> findAll() {
            return items.values().stream().map(InventoryItem::copy).toList();
        }
        private int totalUnits(String productId) {
            return findByProductId(productId).map(InventoryItem::totalUnits).orElse(0);
        }
    }

    private static final class Movements implements StockMovementRepository {
        private final List<StockMovement> saved = new ArrayList<>();
        @Override public void save(StockMovement movement) { saved.add(movement); }
        @Override public boolean remove(StockMovement movement) { return saved.remove(movement); }
        @Override public List<StockMovement> findAll() { return List.copyOf(saved); }
        @Override public List<StockMovement> findByProductId(String productId) {
            return saved.stream().filter(movement -> movement.productId().equals(productId)).toList();
        }
    }
}
