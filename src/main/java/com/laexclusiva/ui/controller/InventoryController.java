package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockSnapshot;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import java.time.format.DateTimeFormatter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Consulta catálogo, existencias y movimientos; permite registrar entradas autorizadas. */
public class InventoryController {
    @FXML private TableView<ProductStock> inventoryTable;
    @FXML private TableColumn<ProductStock, String> productIdColumn;
    @FXML private TableColumn<ProductStock, String> productNameColumn;
    @FXML private TableColumn<ProductStock, Integer> stockColumn;
    @FXML private TableColumn<ProductStock, Integer> basketsColumn;
    @FXML private TableColumn<ProductStock, Integer> looseColumn;
    @FXML private TableView<StockMovement> movementTable;
    @FXML private TableColumn<StockMovement, String> movementProductColumn;
    @FXML private TableColumn<StockMovement, String> movementTypeColumn;
    @FXML private TableColumn<StockMovement, Integer> movementUnitsColumn;
    @FXML private TableColumn<StockMovement, String> movementReasonColumn;
    @FXML private TableColumn<StockMovement, String> movementTimeColumn;
    @FXML private HBox stockEntryPane;
    @FXML private VBox productRegistrationPane;
    @FXML private ComboBox<BeerProduct> productCombo;
    @FXML private ComboBox<ContainerType> containerTypeCombo;
    @FXML private TextField fullBasketsField;
    @FXML private TextField looseUnitsField;
    @FXML private TextField reasonField;
    @FXML private TextField productIdField;
    @FXML private TextField productNameField;
    @FXML private TextField volumeField;
    @FXML private TextField unitsPerBasketField;
    @FXML private TextField containerGroupField;
    @FXML private TextField unitPriceField;
    @FXML private TextField halfBasketPriceField;
    @FXML private TextField fullBasketPriceField;
    @FXML private Label messageLabel;

    private ProductService products;
    private InventoryService inventory;
    private UserSession session;
    private AuthorizationService authorizationService = new AuthorizationService();

    /** Inyecta la autoridad actual que se revalida en cada acción de inventario. */
    public void setAuthorizationService(AuthorizationService authorizationService) {
        if (authorizationService == null) throw new IllegalArgumentException("La autorización es obligatoria.");
        this.authorizationService = authorizationService;
    }

    @FXML
    private void initialize() {
        productIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().product().getId()));
        productNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().product().getName()));
        stockColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().stock().totalUnits()));
        basketsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().stock().fullBaskets()));
        looseColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().stock().looseUnits()));
        movementProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().productId()));
        movementTypeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().type().name()));
        movementUnitsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().units()));
        movementReasonColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().reason()));
        movementTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().timestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        productCombo.setConverter(new StringConverter<>() {
            @Override public String toString(BeerProduct product) { return product == null ? "" : product.toString(); }
            @Override public BeerProduct fromString(String value) { return null; }
        });
        containerTypeCombo.getItems().setAll(ContainerType.values());
        containerTypeCombo.setValue(ContainerType.NON_RETURNABLE_CAN);
        containerTypeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ContainerType value) { return value == null ? "" : value.name(); }
            @Override public ContainerType fromString(String value) { return ContainerType.valueOf(value); }
        });
    }

    /** Configura dependencias y calcula controles visibles a partir de la sesión actual.
     *
     * @param products catálogo de productos
     * @param inventory consulta y escritura de existencias
     * @param session sesión autenticada que determina los permisos de catálogo e inventario
     * @throws IllegalArgumentException si falta una dependencia o la sesión
     */
    public void configure(ProductService products, InventoryService inventory, UserSession session) {
        if (products == null || inventory == null) {
            throw new IllegalArgumentException("Los servicios de producto e inventario son obligatorios.");
        }
        if (session == null) throw new IllegalArgumentException("La sesión es obligatoria.");
        this.products = products;
        this.inventory = inventory;
        this.session = session;
        requirePermission(Permission.VIEW_CATALOG, "No tiene permiso para consultar el catálogo.");
        requirePermission(Permission.VIEW_INVENTORY, "No tiene permiso para consultar el inventario.");
        productCombo.getItems().setAll(products.listAll());
        boolean canManageEntries = authorizationService.hasPermission(session, Permission.MANAGE_INVENTORY_ENTRIES);
        boolean canManageProducts = authorizationService.hasPermission(session, Permission.MANAGE_PRODUCTS);
        stockEntryPane.setVisible(canManageEntries);
        stockEntryPane.setManaged(canManageEntries);
        productRegistrationPane.setVisible(canManageProducts);
        productRegistrationPane.setManaged(canManageProducts);
        refresh();
    }

    /** Registra una referencia nueva con precios independientes para cada presentación. */
    @FXML
    private void handleRegisterProduct() {
        BeerProduct product;
        try {
            requirePermission(Permission.MANAGE_PRODUCTS, "No tiene permiso para modificar el catálogo.");
            ContainerType containerType = productType();
            product = new BeerProduct(productIdField.getText().trim(), productNameField.getText().trim(),
                    Integer.parseInt(volumeField.getText().trim()),
                    Integer.parseInt(unitsPerBasketField.getText().trim()), containerType,
                    containerGroupField.getText().trim().isEmpty() ? null : containerGroupField.getText().trim());
            product.definePrice(SalePresentation.UNIT, new java.math.BigDecimal(unitPriceField.getText().trim()));
            product.definePrice(SalePresentation.HALF_BASKET,
                    new java.math.BigDecimal(halfBasketPriceField.getText().trim()));
            product.definePrice(SalePresentation.FULL_BASKET,
                    new java.math.BigDecimal(fullBasketPriceField.getText().trim()));
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró el producto: " + safeMessage(failure));
            return;
        }

        try {
            products.register(product);
        } catch (RuntimeException failure) {
            messageLabel.setText("No se pudo confirmar el registro del producto. Use «Actualizar inventario» "
                    + "y compruebe el catálogo antes de volver a intentarlo. " + safeMessage(failure));
            return;
        }

        clearProductFields();
        refreshAfterConfirmedWrite("Producto y precios registrados en el catálogo.");
    }

    @FXML
    private void handleAddStock() {
        BeerProduct product;
        int baskets;
        int loose;
        try {
            requirePermission(Permission.MANAGE_INVENTORY_ENTRIES,
                    "No tiene permiso para registrar entradas de inventario.");
            product = productCombo.getValue();
            if (product == null) throw new IllegalArgumentException("Seleccione un producto.");
            baskets = parseNonNegative(fullBasketsField.getText(), "canastas");
            loose = parseNonNegative(looseUnitsField.getText(), "unidades sueltas");
            if (reasonField.getText() == null || reasonField.getText().isBlank()) {
                throw new IllegalArgumentException("El motivo de la entrada es obligatorio.");
            }
            if (product.equivalentUnits(baskets, loose) <= 0) {
                throw new IllegalArgumentException("Debe ingresar al menos una unidad.");
            }
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró la entrada: " + safeMessage(failure));
            return;
        }

        try {
            inventory.addStock(product.getId(), baskets, loose, reasonField.getText());
        } catch (RuntimeException failure) {
            messageLabel.setText("No se pudo confirmar el resultado de la entrada. Use «Actualizar inventario» "
                    + "para verificar las existencias y movimientos antes de intentar otra entrada. "
                    + safeMessage(failure));
            return;
        }

        fullBasketsField.setText("0");
        looseUnitsField.setText("0");
        reasonField.clear();
        refreshAfterConfirmedWrite("Entrada registrada dentro de la transacción de inventario.");
    }

    /** Vuelve a consultar catálogo, existencias y movimientos sin ejecutar ninguna escritura. */
    @FXML
    private void handleRefreshInventory() {
        try {
            requirePermission(Permission.VIEW_CATALOG, "No tiene permiso para consultar el catálogo.");
            requirePermission(Permission.VIEW_INVENTORY, "No tiene permiso para consultar el inventario.");
            refresh();
            messageLabel.setText("Inventario actualizado.");
        } catch (RuntimeException failure) {
            messageLabel.setText("No se pudo actualizar el inventario: " + safeMessage(failure));
        }
    }

    private void refreshAfterConfirmedWrite(String successMessage) {
        messageLabel.setText(successMessage);
        try {
            refresh();
        } catch (RuntimeException failure) {
            messageLabel.setText(successMessage + " No se pudo actualizar la vista. Use «Actualizar inventario» "
                    + "para consultar el estado actual. " + safeMessage(failure));
        }
    }

    private void clearProductFields() {
        productIdField.clear();
        productNameField.clear();
        volumeField.clear();
        unitsPerBasketField.clear();
        containerGroupField.clear();
        unitPriceField.clear();
        halfBasketPriceField.clear();
        fullBasketPriceField.clear();
    }

    private void requirePermission(Permission permission, String message) {
        if (!authorizationService.hasPermission(session, permission)) throw new SecurityException(message);
    }

    private void refresh() {
        requirePermission(Permission.VIEW_CATALOG, "No tiene permiso para consultar el catálogo.");
        requirePermission(Permission.VIEW_INVENTORY, "No tiene permiso para consultar el inventario.");
        java.util.List<BeerProduct> catalog = products.listAll();
        productCombo.getItems().setAll(catalog);
        inventoryTable.getItems().setAll(catalog.stream()
                .map(product -> new ProductStock(product, inventory.getStock(product.getId()))).toList());
        movementTable.getItems().setAll(inventory.allMovements());
    }

    private ContainerType productType() {
        ContainerType selected = containerTypeCombo.getValue();
        if (selected == null) throw new IllegalArgumentException("Seleccione el tipo de envase.");
        return selected;
    }

    private static int parseNonNegative(String value, String field) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 0) throw new IllegalArgumentException("Las " + field + " no pueden ser negativas.");
        return parsed;
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Error inesperado." : failure.getMessage();
    }

    private record ProductStock(BeerProduct product, StockSnapshot stock) { }
}
