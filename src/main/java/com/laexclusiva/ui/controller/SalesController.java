package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.service.SaleRequestFingerprint;
import com.laexclusiva.service.SellerService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/** Pantalla JavaFX para registrar ventas y consultar el historial guardado. */
public class SalesController {
    @FXML private ComboBox<BeerProduct> productCombo;
    @FXML private ComboBox<SalePresentation> presentationCombo;
    @FXML private TextField quantityField;
    @FXML private ComboBox<Seller> sellerCombo;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TextField initialPaymentField;
    @FXML private TableView<CartLine> cartTable;
    @FXML private TableColumn<CartLine, String> cartProductColumn;
    @FXML private TableColumn<CartLine, String> cartPresentationColumn;
    @FXML private TableColumn<CartLine, Integer> cartQuantityColumn;
    @FXML private TableColumn<CartLine, BigDecimal> cartPriceColumn;
    @FXML private TableColumn<CartLine, BigDecimal> cartSubtotalColumn;
    @FXML private TableView<Sale> historyTable;
    @FXML private TableColumn<Sale, String> historyIdColumn;
    @FXML private TableColumn<Sale, String> historyCustomerColumn;
    @FXML private TableColumn<Sale, BigDecimal> historyTotalColumn;
    @FXML private TableColumn<Sale, BigDecimal> historyBalanceColumn;
    @FXML private Label totalLabel;
    @FXML private Label messageLabel;
    @FXML private Label saleOperationKeyLabel;

    private ProductService products;
    private InventoryService inventory;
    private CustomerService customers;
    private SellerService sellers;
    private SalesWorkflow sales;
    private boolean canViewAllSales;
    private String assignedSellerId;
    private UserSession session;
    private String saleOperationKey = UUID.randomUUID().toString();
    private String uncertainSaleFingerprint;
    private final AuthorizationService authorizationService = new AuthorizationService();

    @FXML
    private void initialize() {
        presentationCombo.getItems().setAll(SalePresentation.UNIT,
                SalePresentation.HALF_BASKET, SalePresentation.FULL_BASKET);
        presentationCombo.setConverter(converter(SalesController::presentationName));
        presentationCombo.setValue(SalePresentation.UNIT);
        quantityField.setText("1");
        initialPaymentField.setText("0");
        refreshSaleOperationKeyLabel();
        productCombo.setConverter(converter(BeerProduct::toString));
        sellerCombo.setConverter(converter(Seller::getName));
        customerCombo.setConverter(converter(Customer::getName));
        cartProductColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().product().getName()));
        cartPresentationColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().request().presentation().code()));
        cartQuantityColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().request().quantity()));
        cartPriceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().line().getUnitPrice()));
        cartSubtotalColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().line().getSubtotal()));
        historyIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        historyCustomerColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getCustomer() == null ? "Contado" : cell.getValue().getCustomer().getName()));
        historyTotalColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().calculateTotal()));
        historyBalanceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().calculateBalance()));
    }

    /**
     * Carga servicios, catálogo, clientes, vendedores y sesión autenticada. La sesión es la fuente
     * de permisos y de la asignación de vendedor; el controlador vuelve a comprobarla al registrar.
     *
     * @param products servicio del catálogo
     * @param inventory servicio de existencias
     * @param customers servicio de clientes
     * @param sellers servicio de vendedores
     * @param sales flujo de venta y abono según la composición activa
     * @param session sesión autenticada que define permisos y vendedor asignado
     * @throws IllegalArgumentException si falta una dependencia obligatoria
     */
    public void configure(ProductService products, InventoryService inventory, CustomerService customers,
                          SellerService sellers, SalesWorkflow sales, UserSession session) {
        this.products = require(products);
        this.inventory = require(inventory);
        this.customers = require(customers);
        this.sellers = require(sellers);
        this.sales = require(sales);
        this.session = require(session);
        this.canViewAllSales = session.getUserAccount().getRole() == UserRole.ADMINISTRATOR;
        this.assignedSellerId = session.getUserAccount().getSellerId();
        productCombo.getItems().setAll(products.listAll());
        productCombo.setConverter(converter(product -> product + " · "
                + inventory.getStock(product.getId()).totalUnits() + " u"));
        customerCombo.getItems().setAll(customers.getAllCustomers());
        List<Seller> availableSellers = sellers.getAllSellers();
        boolean canViewSellers = authorizationService.hasPermission(session, Permission.VIEW_SELLERS);
        if (canViewSellers) {
            sellerCombo.getItems().setAll(availableSellers);
            sellerCombo.setDisable(false);
        } else if (assignedSellerId != null) {
            List<Seller> assigned = availableSellers.stream()
                    .filter(seller -> assignedSellerId.equals(seller.getId())).toList();
            sellerCombo.getItems().setAll(assigned);
            sellerCombo.setDisable(true);
            if (assigned.isEmpty()) {
                messageLabel.setText("El vendedor asignado a la cuenta no existe en el registro; solicite su corrección.");
            } else {
                sellerCombo.setValue(assigned.get(0));
            }
        } else {
            sellerCombo.getItems().clear();
            sellerCombo.setDisable(true);
            messageLabel.setText("La cuenta no tiene vendedor asignado; solicite la asignación al administrador.");
        }
        if (canViewSellers && assignedSellerId != null) {
            sellerCombo.getItems().stream().filter(seller -> assignedSellerId.equals(seller.getId()))
                    .findFirst().ifPresent(sellerCombo::setValue);
        }
        try {
            refreshHistory();
        } catch (RuntimeException failure) {
            historyTable.getItems().clear();
            messageLabel.setText("No se pudo consultar el historial de ventas. Use Actualizar historial para reintentar.");
        }
        refreshTotal();
    }

    @FXML
    private void handleAddLine() {
        try {
            BeerProduct product = require(productCombo.getValue());
            SalePresentation presentation = require(presentationCombo.getValue());
            int quantity = Integer.parseInt(quantityField.getText().trim());
            StockRequest request = new StockRequest(product.getId(), presentation, quantity);
            SaleLine line = new SaleLine(product, presentation, quantity);
            cartTable.getItems().add(new CartLine(product, request, line));
            refreshTotal();
            messageLabel.setText("");
        } catch (RuntimeException failure) {
            messageLabel.setText("No se agregó la línea: " + safeMessage(failure));
        }
    }

    @FXML
    private void handleRemoveLine() {
        CartLine selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            cartTable.getItems().remove(selected);
            refreshTotal();
        }
    }

    @FXML
    private void handleRegisterSale() {
        Sale saved;
        String submittedFingerprint = null;
        boolean submitted = false;
        boolean retryingUncertainRequest = uncertainSaleFingerprint != null;
        try {
            requirePermission(Permission.REGISTER_SALE, "No tiene permiso para registrar ventas.");
            requirePermission(Permission.VIEW_SALES, "No tiene permiso para consultar las ventas.");
            if (cartTable.getItems().isEmpty()) {
                throw new IllegalArgumentException("Agregue al menos un producto.");
            }
            Seller seller = require(sellerCombo.getValue());
            if (!isAuthorizedSeller(seller)) {
                throw new SecurityException("La venta debe registrarse con el vendedor asignado a la sesión.");
            }
            Customer customer = customerCombo.getValue();
            BigDecimal initialPayment = new BigDecimal(initialPaymentField.getText().trim());
            List<StockRequest> requests = cartTable.getItems().stream().map(CartLine::request).toList();
            submittedFingerprint = SaleRequestFingerprint.calculate(requests, seller.getId(),
                    customer == null ? null : customer.getId(), initialPayment);
            if (uncertainSaleFingerprint != null && !uncertainSaleFingerprint.equals(submittedFingerprint)) {
                throw new IllegalArgumentException("Hay un intento anterior de resultado incierto. Restaure el mismo "
                        + "carrito, cliente, vendedor y pago inicial para reintentar con la referencia visible.");
            }
            submitted = true;
            saved = sales.registerSale(requests, seller.getId(),
                    customer == null ? null : customer.getId(), initialPayment, saleOperationKey);
        } catch (IllegalArgumentException | SecurityException rejected) {
            messageLabel.setText("Venta no registrada: " + safeMessage(rejected) + " El carrito se conserva.");
            return;
        } catch (InsufficientStockException rejectedForStock) {
            if (!retryingUncertainRequest) {
                // El flujo transaccional confirmó el rechazo; una carga corregida inicia otra solicitud.
                saleOperationKey = UUID.randomUUID().toString();
                refreshSaleOperationKeyLabel();
            }
            String message = "Venta rechazada por existencias insuficientes: " + safeMessage(rejectedForStock)
                    + " El carrito se conserva; ajuste las cantidades y vuelva a intentarlo.";
            if (retryingUncertainRequest) {
                // El rechazo de este reintento no demuestra qué ocurrió con la llamada anterior.
                message += " La solicitud anterior aún tiene resultado incierto; conserve la referencia y no cambie "
                        + "su carga hasta resolverla.";
            }
            messageLabel.setText(message);
            return;
        } catch (RuntimeException failure) {
            if (submitted) uncertainSaleFingerprint = submittedFingerprint;
            messageLabel.setText("No se pudo confirmar el resultado de la venta: " + safeMessage(failure)
                    + " El carrito y la referencia se conservan. Reintente únicamente la misma solicitud "
                    + "o verifique el historial antes de cambiar los datos.");
            return;
        }
        String persistence = sales.supportsDurableIdempotency() ? "PostgreSQL" : "memoria";
        messageLabel.setText("Venta registrada en " + persistence + ": " + saved.getId()
                + " · saldo " + money(saved.calculateBalance()));
        cartTable.getItems().clear();
        initialPaymentField.setText("0");
        uncertainSaleFingerprint = null;
        saleOperationKey = UUID.randomUUID().toString();
        refreshSaleOperationKeyLabel();
        refreshTotal();
        try {
            customerCombo.getItems().setAll(customers.getAllCustomers());
        } catch (RuntimeException failure) {
            messageLabel.setText("Venta registrada: " + saved.getId()
                    + ". No se pudo actualizar la lista de clientes; la venta no se repetirá.");
        }
        try {
            refreshHistory();
        } catch (RuntimeException failure) {
            historyTable.getItems().clear();
            messageLabel.setText("Venta registrada: " + saved.getId()
                    + ". No se pudo actualizar el historial; use Actualizar historial sin volver a registrar.");
        }
    }

    @FXML
    private void handleRefreshHistory() {
        try {
            requirePermission(Permission.VIEW_SALES, "No tiene permiso para consultar las ventas.");
            refreshHistory();
            messageLabel.setText("Historial actualizado.");
        } catch (RuntimeException failure) {
            historyTable.getItems().clear();
            messageLabel.setText("No se pudo actualizar el historial: " + safeMessage(failure));
        }
    }

    private void refreshHistory() {
        if (sales != null) {
            List<Sale> history = sales.listAll();
            if (!canViewAllSales) {
                history = assignedSellerId == null ? List.of() : history.stream()
                        .filter(sale -> assignedSellerId.equals(sale.getSeller().getId())).toList();
            }
            historyTable.getItems().setAll(history);
        }
    }

    private void refreshTotal() {
        BigDecimal total = cartTable.getItems().stream().map(line -> line.line().getSubtotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalLabel.setText("Total: " + money(total));
    }

    private void refreshSaleOperationKeyLabel() {
        if (saleOperationKeyLabel != null) {
            saleOperationKeyLabel.setText("Referencia de operación: " + saleOperationKey);
        }
    }

    private void requirePermission(Permission permission, String message) {
        if (!authorizationService.hasPermission(session, permission)) throw new SecurityException(message);
    }

    private boolean isAuthorizedSeller(Seller seller) {
        if (session.getUserAccount().getRole() == UserRole.ADMINISTRATOR) return true;
        if (assignedSellerId == null || !assignedSellerId.equals(seller.getId())) return false;
        return sellers.getAllSellers().stream().anyMatch(candidate -> assignedSellerId.equals(candidate.getId()));
    }

    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$ " + format.format(amount);
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Error inesperado." : failure.getMessage();
    }

    private static String presentationName(SalePresentation presentation) {
        return switch (presentation.code()) {
            case "UNIT" -> "Unidad";
            case "HALF_BASKET" -> "Media canasta";
            case "FULL_BASKET" -> "Canasta completa";
            default -> presentation.code();
        };
    }

    private static <T> T require(T value) {
        if (value == null) throw new IllegalArgumentException("Seleccione un valor válido.");
        return value;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> display) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : display.apply(value); }
            @Override public T fromString(String value) { return null; }
        };
    }

    private record CartLine(BeerProduct product, StockRequest request, SaleLine line) { }
}
