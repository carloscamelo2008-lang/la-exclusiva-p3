package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockSnapshot;
import com.laexclusiva.model.SalePresentation;
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

    /** Configura dependencias, permiso de catálogo y permiso de entrada de inventario. */
    public void configure(ProductService products, InventoryService inventory,
                          boolean canManageProducts, boolean canManageEntries) {
        if (products == null || inventory == null) {
            throw new IllegalArgumentException("Los servicios de producto e inventario son obligatorios.");
        }
        this.products = products;
        this.inventory = inventory;
        productCombo.getItems().setAll(products.listAll());
        stockEntryPane.setVisible(canManageEntries);
        stockEntryPane.setManaged(canManageEntries);
        productRegistrationPane.setVisible(canManageProducts);
        productRegistrationPane.setManaged(canManageProducts);
        refresh();
    }

    /** Registra una referencia nueva con precios independientes para cada presentación. */
    @FXML
    private void handleRegisterProduct() {
        try {
            ContainerType containerType = productType();
            BeerProduct product = new BeerProduct(productIdField.getText().trim(), productNameField.getText().trim(),
                    Integer.parseInt(volumeField.getText().trim()),
                    Integer.parseInt(unitsPerBasketField.getText().trim()), containerType,
                    containerGroupField.getText().trim().isEmpty() ? null : containerGroupField.getText().trim());
            product.definePrice(SalePresentation.UNIT, new java.math.BigDecimal(unitPriceField.getText().trim()));
            product.definePrice(SalePresentation.HALF_BASKET,
                    new java.math.BigDecimal(halfBasketPriceField.getText().trim()));
            product.definePrice(SalePresentation.FULL_BASKET,
                    new java.math.BigDecimal(fullBasketPriceField.getText().trim()));
            products.register(product);
            productIdField.clear();
            productNameField.clear();
            volumeField.clear();
            unitsPerBasketField.clear();
            containerGroupField.clear();
            unitPriceField.clear();
            halfBasketPriceField.clear();
            fullBasketPriceField.clear();
            messageLabel.setText("Producto y precios registrados en el catálogo.");
            refresh();
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró el producto: " + safeMessage(failure));
        }
    }

    @FXML
    private void handleAddStock() {
        try {
            BeerProduct product = productCombo.getValue();
            if (product == null) throw new IllegalArgumentException("Seleccione un producto.");
            int baskets = parseNonNegative(fullBasketsField.getText(), "canastas");
            int loose = parseNonNegative(looseUnitsField.getText(), "unidades sueltas");
            inventory.addStock(product.getId(), baskets, loose, reasonField.getText());
            messageLabel.setText("Entrada registrada dentro de la transacción de inventario.");
            fullBasketsField.setText("0");
            looseUnitsField.setText("0");
            reasonField.clear();
            refresh();
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró la entrada: " + safeMessage(failure));
        }
    }

    private void refresh() {
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
