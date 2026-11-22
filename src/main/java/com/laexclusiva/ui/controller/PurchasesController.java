package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.GoodsReceipt;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.PurchaseOrder;
import com.laexclusiva.model.PurchaseOrderLine;
import com.laexclusiva.model.Supplier;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.GoodsReceiptService;
import com.laexclusiva.service.GoodsReceiptService.IdempotencyConflictException;
import com.laexclusiva.service.GoodsReceiptService.ReceiptLineRequest;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.PurchaseOrderService;
import com.laexclusiva.service.PurchaseOrderService.OrderLineRequest;
import com.laexclusiva.service.SupplierService;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;

/** Presenta las operaciones de compras y conserva en la vista la intención de recepción pendiente. */
public final class PurchasesController {
    private static final String REFRESH_FAILED = " No se pudo actualizar la vista. Use el botón de actualización.";
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");
    private final AuthorizationService authorization = new AuthorizationService();

    @FXML private Pane supplierEditor, orderEditor, receiptEditor;
    @FXML private Label messageLabel, receiptMessageLabel, equivalenceLabel, selectedOrderLabel, receiptKeyLabel;
    @FXML private Button refreshButton, detailButton, refreshHistoryButton, receiveButton;
    @FXML private TextField supplierName, supplierTaxId, supplierContact, orderBaskets, orderLoose, orderNotes;
    @FXML private TextField receiptBaskets, receiptLoose, receiptNotes;
    @FXML private ComboBox<Supplier> supplierCombo;
    @FXML private ComboBox<BeerProduct> productCombo;
    @FXML private TableView<Supplier> suppliersTable;
    @FXML private TableView<PurchaseOrder> ordersTable;
    @FXML private TableView<PurchaseOrderLine> orderLinesTable;
    @FXML private TableView<OrderLineRequest> draftOrderTable;
    @FXML private TableView<ReceiptLineRequest> draftReceiptTable;
    @FXML private TableView<GoodsReceipt> historyTable;

    private SupplierService suppliers;
    private PurchaseOrderService orders;
    private GoodsReceiptService receipts;
    private ProductService products;
    private UserSession session;
    private Long selectedOrderId;
    private boolean orderOpen;
    private ReceiptIntent pending;

    private record ReceiptIntent(long orderId, String key, String notes, List<ReceiptLineRequest> lines) {}

    /**
     * Carga la vista e inyecta sus servicios en el hilo de JavaFX.
     * @return nodo configurado para montar en el contenedor de navegación
     * @throws IOException si no se puede cargar el FXML
     */
    public static Parent load(SupplierService supplierService, PurchaseOrderService purchaseOrderService,
                              GoodsReceiptService goodsReceiptService, ProductService productService,
                              UserSession session) throws IOException {
        FXMLLoader loader = new FXMLLoader(PurchasesController.class.getResource(
                "/com/laexclusiva/ui/fxml/purchases_view.fxml"));
        Parent view = loader.load();
        PurchasesController controller = loader.getController();
        controller.configure(supplierService, purchaseOrderService, goodsReceiptService, productService, session);
        return view;
    }

    /** Conecta la vista con los servicios del módulo y aplica los permisos de la sesión. */
    public void configure(SupplierService supplierService, PurchaseOrderService purchaseOrderService,
                          GoodsReceiptService goodsReceiptService, ProductService productService, UserSession session) {
        suppliers = Objects.requireNonNull(supplierService);
        orders = Objects.requireNonNull(purchaseOrderService);
        receipts = Objects.requireNonNull(goodsReceiptService);
        products = Objects.requireNonNull(productService);
        this.session = session;
        updatePermissions();
        handleRefresh();
    }

    @FXML
    private void initialize() {
        column(suppliersTable, "Proveedor", 220, Supplier::name);
        column(suppliersTable, "Identificación tributaria", 165, Supplier::taxId);
        column(suppliersTable, "Contacto", 200, Supplier::contactInfo);
        column(suppliersTable, "Estado", 105, supplier -> supplier.active() ? "Activo" : "Inactivo");
        column(ordersTable, "Pedido", 85, PurchaseOrder::id);
        column(ordersTable, "Proveedor (ID)", 120, PurchaseOrder::supplierId);
        column(ordersTable, "Estado", 120, order -> order.status().name());
        column(ordersTable, "Creado por", 145, PurchaseOrder::createdByUsername);
        column(ordersTable, "Notas", 240, PurchaseOrder::notes);
        column(orderLinesTable, "Línea", 75, PurchaseOrderLine::id);
        column(orderLinesTable, "Producto", 155, PurchaseOrderLine::productId);
        column(orderLinesTable, "Pedidas", 95, PurchaseOrderLine::orderedEquivalentUnits);
        column(orderLinesTable, "Recibidas", 95, PurchaseOrderLine::receivedEquivalentUnits);
        column(orderLinesTable, "Pendientes", 105, PurchaseOrderLine::pendingEquivalentUnits);
        column(draftOrderTable, "Producto", 180, OrderLineRequest::productId);
        column(draftOrderTable, "Canastas", 115, OrderLineRequest::baskets);
        column(draftOrderTable, "Sueltas", 115, OrderLineRequest::looseUnits);
        column(draftReceiptTable, "Línea", 95, ReceiptLineRequest::purchaseOrderLineId);
        column(draftReceiptTable, "Canastas", 115, ReceiptLineRequest::baskets);
        column(draftReceiptTable, "Sueltas", 115, ReceiptLineRequest::looseUnits);
        column(historyTable, "Recepción", 90, GoodsReceipt::id);
        column(historyTable, "Fecha y hora", 150, receipt -> RECEIPT_DATE.format(receipt.receivedAt()));
        column(historyTable, "Usuario", 125, GoodsReceipt::receivedByUsername);
        column(historyTable, "Unidades", 95, receipt -> receipt.lines().stream()
                .mapToLong(line -> line.receivedEquivalentUnits()).sum());
        column(historyTable, "Clave de operación", 265, GoodsReceipt::idempotencyKey);
        supplierCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Supplier supplier) {
                return supplier == null ? "" : supplier.name();
            }
            @Override public Supplier fromString(String text) {
                throw new UnsupportedOperationException("Seleccione un proveedor del catálogo.");
            }
        });
        productCombo.valueProperty().addListener((property, oldValue, value) -> showEquivalentUnits());
        orderBaskets.textProperty().addListener((property, oldValue, value) -> showEquivalentUnits());
        orderLoose.textProperty().addListener((property, oldValue, value) -> showEquivalentUnits());
        updatePermissions();
    }

    @FXML
    private void handleRefresh() {
        if (allow(Permission.VIEW_INVENTORY, messageLabel)) {
            read(messageLabel, "Compras actualizadas.", () -> {
                refreshOverview();
                if (selectedOrderId != null) refreshOrderDetail();
            });
        }
    }

    @FXML
    private void handleCreateSupplier() {
        write(messageLabel, "Proveedor registrado.", () -> {
            suppliers.create(session, supplierName.getText(), optional(supplierTaxId), optional(supplierContact));
            supplierName.clear();
            supplierTaxId.clear();
            supplierContact.clear();
        }, this::refreshOverview);
    }

    @FXML
    private void handleToggleSupplier() {
        write(messageLabel, "Estado del proveedor actualizado.", () -> {
            Supplier supplier = selected(suppliersTable, "Seleccione un proveedor.");
            suppliers.setActive(session, supplier.id(), !supplier.active());
        }, this::refreshOverview);
    }

    @FXML
    private void handleAddOrderLine() {
        if (!allow(Permission.MANAGE_INVENTORY_ENTRIES, messageLabel)) return;
        try {
            BeerProduct product = productCombo.getValue();
            if (product == null) throw new IllegalArgumentException("Seleccione un producto.");
            int baskets = quantity(orderBaskets);
            int loose = quantity(orderLoose);
            if (product.equivalentUnits(baskets, loose) <= 0) {
                throw new IllegalArgumentException("Introduzca al menos una unidad.");
            }
            draftOrderTable.getItems().add(new OrderLineRequest(product.getId(), baskets, loose));
            orderBaskets.clear();
            orderLoose.clear();
            messageLabel.setText("");
        } catch (IllegalArgumentException error) {
            messageLabel.setText(error.getMessage());
        }
    }

    @FXML
    private void handleRemoveOrderLine() {
        if (allow(Permission.MANAGE_INVENTORY_ENTRIES, messageLabel)) {
            draftOrderTable.getItems().remove(draftOrderTable.getSelectionModel().getSelectedItem());
        }
    }

    @FXML
    private void handleCreateOrder() {
        write(messageLabel, "Pedido creado. Las existencias se actualizarán al recibirlo.", () -> {
            Supplier supplier = supplierCombo.getValue();
            if (supplier == null) throw new IllegalArgumentException("Seleccione un proveedor activo.");
            orders.create(session, supplier.id(), optional(orderNotes), List.copyOf(draftOrderTable.getItems()));
            draftOrderTable.getItems().clear();
            orderNotes.clear();
        }, this::refreshOverview);
    }

    @FXML
    private void handleLoadOrder() {
        if (!allow(Permission.VIEW_INVENTORY, receiptMessageLabel)) return;
        PurchaseOrder order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            receiptMessageLabel.setText("Seleccione un pedido.");
            return;
        }
        if (!Objects.equals(selectedOrderId, order.id()) && pending == null) clearReceiptDraft();
        if (!Objects.equals(selectedOrderId, order.id())) {
            orderOpen = false;
            orderLinesTable.getItems().clear();
            historyTable.getItems().clear();
            updatePermissions();
        }
        selectedOrderId = order.id();
        selectedOrderLabel.setText("Pedido " + selectedOrderId);
        read(receiptMessageLabel, pending == null ? "Detalle e historial actualizados." : retryMessage(),
                this::refreshOrderDetail);
    }

    @FXML
    private void handleAddReceiptLine() {
        if (!allow(Permission.MANAGE_INVENTORY_ENTRIES, receiptMessageLabel) || pending != null || !orderOpen) return;
        try {
            PurchaseOrderLine line = selected(orderLinesTable, "Seleccione una línea del pedido.");
            ReceiptLineRequest entry = new ReceiptLineRequest(line.id(), quantity(receiptBaskets), quantity(receiptLoose));
            draftReceiptTable.getItems().removeIf(item -> item.purchaseOrderLineId() == line.id());
            draftReceiptTable.getItems().add(entry);
            receiptBaskets.clear();
            receiptLoose.clear();
            receiptMessageLabel.setText("");
        } catch (IllegalArgumentException error) {
            receiptMessageLabel.setText(error.getMessage());
        }
    }

    @FXML
    private void handleRemoveReceiptLine() {
        if (allow(Permission.MANAGE_INVENTORY_ENTRIES, receiptMessageLabel) && pending == null) {
            draftReceiptTable.getItems().remove(draftReceiptTable.getSelectionModel().getSelectedItem());
        }
    }

    @FXML
    private void handleReceive() {
        if (!allow(Permission.MANAGE_INVENTORY_ENTRIES, receiptMessageLabel)) return;
        if (pending == null) {
            if (!orderOpen || draftReceiptTable.getItems().isEmpty()) {
                receiptMessageLabel.setText("Seleccione un pedido abierto y añada cantidades a recibir.");
                return;
            }
            pending = new ReceiptIntent(selectedOrderId, UUID.randomUUID().toString(), optional(receiptNotes),
                    List.copyOf(draftReceiptTable.getItems()));
        }
        try {
            receipts.receive(session, pending.orderId(), pending.key(), pending.notes(), pending.lines());
        } catch (SecurityException denied) {
            receiptMessageLabel.setText("La sesión no tiene permiso para registrar recepciones.");
            updatePermissions();
            return;
        } catch (IdempotencyConflictException | IllegalArgumentException rejected) {
            pending = null;
            receiptMessageLabel.setText("Recepción rechazada. " + rejected.getMessage());
            updatePermissions();
            return;
        } catch (RuntimeException uncertain) {
            receiptMessageLabel.setText(retryMessage());
            updatePermissions();
            return;
        }
        pending = null;
        orderOpen = false;
        clearReceiptDraft();
        updatePermissions();
        afterWrite(receiptMessageLabel, "Recepción confirmada. Existencias actualizadas.", () -> {
            refreshOverview();
            refreshOrderDetail();
        });
    }

    @FXML
    private void handleRefreshHistory() {
        if (allow(Permission.VIEW_INVENTORY, receiptMessageLabel)) {
            read(receiptMessageLabel, pending == null ? "Historial actualizado." : retryMessage(), this::refreshHistory);
        }
    }

    private String retryMessage() {
        return "Resultado incierto de la recepción del pedido " + pending.orderId()
                + ". Use Reintentar recepción para conservar la misma clave y las mismas cantidades.";
    }

    private void refreshOverview() {
        var allSuppliers = suppliers.findAll(session);
        var activeSuppliers = suppliers.findActive(session);
        var catalog = products.listAll();
        var allOrders = orders.findAll(session);
        Supplier chosen = supplierCombo.getValue();
        BeerProduct product = productCombo.getValue();
        suppliersTable.getItems().setAll(allSuppliers);
        supplierCombo.getItems().setAll(activeSuppliers);
        if (chosen != null) {
            activeSuppliers.stream().filter(value -> value.id().equals(chosen.id())).findFirst()
                    .ifPresentOrElse(supplierCombo::setValue, () -> supplierCombo.setValue(null));
        }
        productCombo.getItems().setAll(catalog);
        if (product != null) {
            catalog.stream().filter(value -> value.getId().equals(product.getId())).findFirst()
                    .ifPresentOrElse(productCombo::setValue, () -> productCombo.setValue(null));
        }
        ordersTable.getItems().setAll(allOrders);
        allOrders.stream().filter(order -> order.id().equals(selectedOrderId)).findFirst()
                .ifPresent(order -> ordersTable.getSelectionModel().select(order));
    }

    private void refreshOrderDetail() {
        var details = orders.findById(session, selectedOrderId)
                .orElseThrow(() -> new IllegalArgumentException("El pedido no existe."));
        orderLinesTable.getItems().setAll(details.lines());
        orderOpen = switch (details.order().status()) {
            case PENDING, PARTIAL -> true;
            case COMPLETED, CANCELLED -> false;
        };
        selectedOrderLabel.setText("Pedido " + selectedOrderId + " · " + details.order().status());
        updatePermissions();
        refreshHistory();
    }

    private void refreshHistory() {
        if (selectedOrderId != null) {
            historyTable.getItems().setAll(receipts.findByPurchaseOrderId(session, selectedOrderId));
        }
    }

    private void clearReceiptDraft() {
        draftReceiptTable.getItems().clear();
        receiptBaskets.clear();
        receiptLoose.clear();
        receiptNotes.clear();
    }

    private void showEquivalentUnits() {
        BeerProduct product = productCombo.getValue();
        if (product == null) {
            equivalenceLabel.setText("");
            return;
        }
        String capacity = product.getUnitsPerBasket() + " unidades por canasta";
        try {
            int total = product.equivalentUnits(quantity(orderBaskets), quantity(orderLoose));
            equivalenceLabel.setText(capacity + " · " + total + " unidades equivalentes");
        } catch (IllegalArgumentException invalid) {
            equivalenceLabel.setText(capacity + " · cantidades inválidas");
        }
    }

    private void updatePermissions() {
        boolean read = authorization.hasPermission(session, Permission.VIEW_INVENTORY);
        boolean write = authorization.hasPermission(session, Permission.MANAGE_INVENTORY_ENTRIES);
        refreshButton.setDisable(!read);
        detailButton.setDisable(!read || pending != null);
        refreshHistoryButton.setDisable(!read || selectedOrderId == null);
        supplierEditor.setDisable(!write);
        orderEditor.setDisable(!write);
        receiptEditor.setDisable(!write || !orderOpen || pending != null);
        receiveButton.setDisable(!write || (!orderOpen && pending == null));
        receiveButton.setText(pending == null ? "Confirmar recepción" : "Reintentar recepción");
        receiptKeyLabel.setText(pending == null ? "" : "Clave conservada: " + pending.key());
    }

    private boolean allow(Permission permission, Label target) {
        if (authorization.hasPermission(session, permission)) return true;
        updatePermissions();
        target.setText("La sesión no tiene permiso para esta operación.");
        return false;
    }

    private void write(Label target, String success, Runnable operation, Runnable refresh) {
        if (!allow(Permission.MANAGE_INVENTORY_ENTRIES, target)) return;
        try {
            operation.run();
        } catch (IllegalArgumentException | SecurityException rejected) {
            target.setText("Operación rechazada. " + rejected.getMessage());
            return;
        } catch (RuntimeException uncertain) {
            target.setText("Resultado incierto. Actualice la vista y compruebe el registro antes de repetir.");
            return;
        }
        afterWrite(target, success, refresh);
    }

    private static void afterWrite(Label target, String success, Runnable refresh) {
        try {
            refresh.run();
            target.setText(success);
        } catch (RuntimeException error) {
            target.setText(success + REFRESH_FAILED);
        }
    }

    private static void read(Label target, String success, Runnable operation) {
        try {
            operation.run();
            target.setText(success);
        } catch (RuntimeException error) {
            target.setText((success.startsWith("Resultado incierto") ? success + " " : "")
                    + "No se pudo actualizar la vista. Vuelva a consultar.");
        }
    }

    private static int quantity(TextField field) {
        String text = field.getText().strip();
        try {
            int value = text.isEmpty() ? 0 : Integer.parseInt(text);
            if (value >= 0) return value;
        } catch (NumberFormatException invalid) {
            // El mismo mensaje cubre formatos inválidos y cantidades fuera del rango de int.
        }
        throw new IllegalArgumentException("Introduzca cantidades enteras no negativas.");
    }

    private static String optional(TextField field) {
        return field.getText().isBlank() ? null : field.getText();
    }

    private static <T> T selected(TableView<T> table, String message) {
        T item = table.getSelectionModel().getSelectedItem();
        if (item == null) throw new IllegalArgumentException(message);
        return item;
    }

    private static <T, V> void column(TableView<T> table, String title, double width, Function<T, V> value) {
        TableColumn<T, V> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }
}
