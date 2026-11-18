package com.laexclusiva.ui.controller;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.service.SellerService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
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

    private ProductService products;
    private InventoryService inventory;
    private CustomerService customers;
    private SellerService sellers;
    private SalesWorkflow sales;
    private boolean canViewAllSales;
    private String assignedSellerId;

    @FXML
    private void initialize() {
        presentationCombo.getItems().setAll(SalePresentation.UNIT,
                SalePresentation.HALF_BASKET, SalePresentation.FULL_BASKET);
        presentationCombo.setConverter(converter(SalesController::presentationName));
        presentationCombo.setValue(SalePresentation.UNIT);
        quantityField.setText("1");
        initialPaymentField.setText("0");
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
     * Carga servicios, catálogo, clientes, vendedores y sesión autenticada.
     *
     * @param products servicio del catálogo
     * @param inventory servicio de existencias
     * @param customers servicio de clientes
     * @param sellers servicio de vendedores
     * @param sales flujo de venta y abono según la composición activa
     * @param session sesión autenticada que define permisos y vendedor asignado
     * @param canViewSellers indica si el usuario puede seleccionar y consultar vendedores
     * @throws IllegalArgumentException si falta una dependencia obligatoria
     */
    public void configure(ProductService products, InventoryService inventory, CustomerService customers,
                          SellerService sellers, SalesWorkflow sales, UserSession session,
                          boolean canViewSellers) {
        this.products = require(products);
        this.inventory = require(inventory);
        this.customers = require(customers);
        this.sellers = require(sellers);
        this.sales = require(sales);
        UserSession activeSession = require(session);
        this.canViewAllSales = activeSession.getUserAccount().getRole() == UserRole.ADMINISTRATOR;
        this.assignedSellerId = activeSession.getUserAccount().getSellerId();
        productCombo.getItems().setAll(products.listAll());
        productCombo.setConverter(converter(product -> product + " · "
                + inventory.getStock(product.getId()).totalUnits() + " u"));
        customerCombo.getItems().setAll(customers.getAllCustomers());
        List<Seller> availableSellers = sellers.getAllSellers();
        if (canViewSellers) {
            sellerCombo.getItems().setAll(availableSellers);
        } else if (assignedSellerId != null) {
            sellerCombo.getItems().setAll(availableSellers.stream()
                    .filter(seller -> assignedSellerId.equals(seller.getId())).toList());
            sellerCombo.setDisable(true);
        } else {
            sellerCombo.getItems().clear();
            sellerCombo.setDisable(true);
            messageLabel.setText("La cuenta no tiene un vendedor asignado; solicite la asignación al administrador.");
        }
        if (canViewSellers && assignedSellerId != null) {
            sellerCombo.getItems().stream().filter(seller -> assignedSellerId.equals(seller.getId()))
                    .findFirst().ifPresent(sellerCombo::setValue);
        }
        refreshHistory();
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
        try {
            if (cartTable.getItems().isEmpty()) {
                throw new IllegalArgumentException("Agregue al menos un producto.");
            }
            Seller seller = require(sellerCombo.getValue());
            Customer customer = customerCombo.getValue();
            BigDecimal initialPayment = new BigDecimal(initialPaymentField.getText().trim());
            List<StockRequest> requests = cartTable.getItems().stream().map(CartLine::request).toList();
            Sale saved = sales.registerSale(requests, seller.getId(),
                    customer == null ? null : customer.getId(), initialPayment);
            String persistence = sales.supportsDurableIdempotency() ? "PostgreSQL" : "memoria";
            messageLabel.setText("Venta guardada en " + persistence + ": " + saved.getId()
                    + " · saldo " + money(saved.calculateBalance()));
            cartTable.getItems().clear();
            initialPaymentField.setText("0");
            customerCombo.getItems().setAll(customers.getAllCustomers());
            refreshHistory();
            refreshTotal();
        } catch (RuntimeException failure) {
            messageLabel.setText("La venta no se registró: " + safeMessage(failure));
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
