package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Sale;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.SalesWorkflow;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import java.util.UUID;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class PortfolioController {

    @FXML private TableView<Customer> portfolioTable;
    @FXML private TableColumn<Customer, String> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, BigDecimal> balanceColumn;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TableView<Sale> salesTable;
    @FXML private TableColumn<Sale, String> saleIdColumn;
    @FXML private TableColumn<Sale, BigDecimal> saleTotalColumn;
    @FXML private TableColumn<Sale, BigDecimal> salePaidColumn;
    @FXML private TableColumn<Sale, BigDecimal> saleBalanceColumn;
    @FXML private TextField paymentAmountField;
    @FXML private Label salesDebtLabel;
    @FXML private Label generalDebtLabel;
    @FXML private Label messageLabel;
    @FXML private Label modeLabel;
    @FXML private HBox paymentPane;

    private CustomerService customerService;
    private SalesWorkflow salesWorkflow;
    private boolean canRegisterPayment;
    private String pendingSaleId;
    private BigDecimal pendingAmount;
    private String pendingKey;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        balanceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getBalance()));
        balanceColumn.setCellFactory(column -> new TableCell<>() {
            private final NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));

            @Override
            protected void updateItem(BigDecimal balance, boolean empty) {
                super.updateItem(balance, empty);
                if (empty || balance == null) {
                    setText(null);
                    return;
                }
                format.setMaximumFractionDigits(Math.max(2, balance.scale()));
                format.setMinimumFractionDigits(2);
                setText("$ " + format.format(balance));
            }
        });
        customerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Customer customer) { return customer == null ? "" : customer.getName(); }
            @Override public Customer fromString(String value) { return null; }
        });
        saleIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getId()));
        saleTotalColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().calculateTotal()));
        salePaidColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().calculateTotalPaid()));
        saleBalanceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().calculateBalance()));
        customerCombo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                refreshSelectedCustomer());
        salesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                paymentAmountField.setText(newValue.calculateBalance().toPlainString());
            }
        });
    }

    /** Configura la cartera y muestra la acción de pago solo al rol autorizado. */
    public void configure(CustomerService customerService, SalesWorkflow salesWorkflow, boolean canRegisterPayment) {
        if (customerService == null) throw new IllegalArgumentException("El servicio de clientes es obligatorio.");
        this.customerService = customerService;
        this.salesWorkflow = salesWorkflow;
        this.canRegisterPayment = canRegisterPayment;
        paymentPane.setVisible(canRegisterPayment && salesWorkflow != null);
        paymentPane.setManaged(canRegisterPayment && salesWorkflow != null);
        modeLabel.setText(salesWorkflow == null || !salesWorkflow.supportsDurableIdempotency()
                ? "Modo en memoria: las claves de reintento duran mientras la aplicación está abierta."
                : "Abonos con claves idempotentes persistidas en PostgreSQL.");
    }

    /** Lista clientes con deuda total positiva y prepara el selector de cartera por venta. */
    public void showPortfolio(List<Customer> customers) {
        if (customers == null) throw new IllegalArgumentException("La lista de clientes es obligatoria.");
        List<Customer> debtors = customers.stream()
                .filter(customer -> customer.getBalance().compareTo(BigDecimal.ZERO) > 0).toList();
        showDebtors(debtors);
        customerCombo.getItems().setAll(customers);
        if (!customers.isEmpty()) customerCombo.setValue(customers.get(0));
        if (customers.isEmpty()) refreshSelectedCustomer();
    }

    public void showDebtors(List<Customer> debtors) {
        portfolioTable.getItems().setAll(debtors);
    }

    @FXML
    private void handleRegisterPayment() {
        try {
            Sale selected = salesTable.getSelectionModel().getSelectedItem();
            if (selected == null) throw new IllegalArgumentException("Seleccione una venta pendiente.");
            BigDecimal amount = new BigDecimal(paymentAmountField.getText().trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El abono debe ser mayor que cero.");
            }
            if (!selected.getId().equals(pendingSaleId) || pendingAmount == null
                    || pendingAmount.compareTo(amount) != 0 || pendingKey == null) {
                pendingSaleId = selected.getId();
                pendingAmount = amount;
                pendingKey = UUID.randomUUID().toString();
            }
            salesWorkflow.registerPayment(selected.getId(), amount, pendingKey);
            pendingSaleId = null;
            pendingAmount = null;
            pendingKey = null;
            messageLabel.setText("Abono registrado correctamente.");
            showPortfolio(customerService.getAllCustomers());
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró el abono: " + safeMessage(failure)
                    + " Si reintenta el mismo importe y venta, se conserva la clave del intento.");
        }
    }

    private void refreshSelectedCustomer() {
        Customer selected = customerCombo.getValue();
        salesTable.getItems().clear();
        if (selected == null) {
            salesDebtLabel.setText("Deuda de ventas: —");
            generalDebtLabel.setText("Deuda general: —");
            return;
        }
        BigDecimal salesDebt = salesWorkflow == null
                ? BigDecimal.ZERO : salesWorkflow.outstandingSalesDebt(selected.getId());
        BigDecimal generalDebt = selected.getBalance().subtract(salesDebt);
        salesDebtLabel.setText("Deuda de ventas: " + money(salesDebt));
        generalDebtLabel.setText(generalDebt.signum() < 0
                ? "Inconsistencia: ventas superan el saldo agregado."
                : "Deuda general: " + money(generalDebt));
        if (salesWorkflow != null) {
            salesTable.getItems().setAll(salesWorkflow.listAll().stream()
                    .filter(sale -> sale.getCustomer() != null
                            && selected.getId().equals(sale.getCustomer().getId()))
                    .filter(sale -> sale.calculateBalance().signum() > 0)
                    .toList());
        }
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
}
