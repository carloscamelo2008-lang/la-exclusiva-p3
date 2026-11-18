package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
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
    private UserSession session;
    private final AuthorizationService authorizationService = new AuthorizationService();
    private String pendingSaleId;
    private BigDecimal pendingAmount;
    private String pendingKey;
    private boolean updatingView;
    private boolean lastQuerySucceeded = true;

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

    /** Configura servicios y sesión; la visibilidad y cada operación se autorizan desde esa sesión.
     *
     * @param customerService consulta de clientes y saldos agregados
     * @param salesWorkflow consulta de deuda por venta y registro idempotente de abonos
     * @param session sesión cuya vigencia y permisos se revisan al ejecutar acciones
     * @throws IllegalArgumentException si faltan los clientes o la sesión
     */
    public void configure(CustomerService customerService, SalesWorkflow salesWorkflow, UserSession session) {
        if (customerService == null) throw new IllegalArgumentException("El servicio de clientes es obligatorio.");
        if (session == null) throw new IllegalArgumentException("La sesión es obligatoria.");
        this.customerService = customerService;
        this.salesWorkflow = salesWorkflow;
        this.session = session;
        this.canRegisterPayment = authorizationService.hasPermission(session, Permission.REGISTER_PAYMENT);
        paymentPane.setVisible(canRegisterPayment && salesWorkflow != null);
        paymentPane.setManaged(canRegisterPayment && salesWorkflow != null);
        modeLabel.setText(salesWorkflow == null || !salesWorkflow.supportsDurableIdempotency()
                ? "Modo en memoria: las claves de reintento duran mientras la aplicación está abierta."
                : "Abonos con claves idempotentes persistidas en PostgreSQL.");
    }

    /** Lista clientes con deuda total positiva y prepara el selector de cartera por venta. */
    public void showPortfolio(List<Customer> customers) {
        if (customers == null) throw new IllegalArgumentException("La lista de clientes es obligatoria.");
        String previousCustomerId = customerCombo.getValue() == null ? null : customerCombo.getValue().getId();
        List<Customer> debtors = customers.stream()
                .filter(customer -> customer.getBalance().compareTo(BigDecimal.ZERO) > 0).toList();
        showDebtors(debtors);
        updatingView = true;
        customerCombo.getItems().setAll(customers);
        Customer selected = customers.stream().filter(customer -> customer.getId().equals(previousCustomerId))
                .findFirst().orElse(customers.isEmpty() ? null : customers.get(0));
        customerCombo.setValue(selected);
        updatingView = false;
        if (selected == null) {
            salesTable.getItems().clear();
            salesDebtLabel.setText("Deuda de ventas: —");
            generalDebtLabel.setText("Deuda general: —");
            lastQuerySucceeded = true;
        } else {
            lastQuerySucceeded = refreshSelectedCustomer();
        }
    }

    public void showDebtors(List<Customer> debtors) {
        portfolioTable.getItems().setAll(debtors);
    }

    @FXML
    private void handleRegisterPayment() {
        Sale selected;
        BigDecimal amount;
        String key;
        try {
            requirePermission(Permission.REGISTER_PAYMENT, "No tiene permiso para registrar abonos.");
            selected = salesTable.getSelectionModel().getSelectedItem();
            if (selected == null) throw new IllegalArgumentException("Seleccione una venta pendiente.");
            amount = new BigDecimal(paymentAmountField.getText().trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El abono debe ser mayor que cero.");
            }
            if (pendingKey != null && (!selected.getId().equals(pendingSaleId) || pendingAmount == null
                    || pendingAmount.compareTo(amount) != 0)) {
                throw new IllegalStateException("Hay un abono de resultado incierto. Reintente exactamente la misma "
                        + "venta y el mismo importe antes de iniciar otro abono.");
            }
            if (pendingKey == null) {
                pendingSaleId = selected.getId();
                pendingAmount = amount;
                pendingKey = UUID.randomUUID().toString();
            }
        } catch (RuntimeException failure) {
            messageLabel.setText("No se registró el abono: " + safeMessage(failure));
            return;
        }

        key = pendingKey;
        try {
            salesWorkflow.registerPayment(selected.getId(), amount, key);
        } catch (IllegalArgumentException rejected) {
            // Errores de dominio son rechazos definitivos antes de persistir el abono.
            pendingSaleId = null;
            pendingAmount = null;
            pendingKey = null;
            messageLabel.setText("Abono rechazado: " + safeMessage(rejected));
            return;
        } catch (RuntimeException uncertain) {
            // Conservar la misma clave: la escritura podría haberse confirmado antes del fallo.
            messageLabel.setText("No se pudo confirmar el resultado del abono: " + safeMessage(uncertain)
                    + " Reintente la misma venta y el mismo importe para resolverlo; no cambie la operación.");
            return;
        }

        pendingSaleId = null;
        pendingAmount = null;
        pendingKey = null;
        String confirmedMessage = "Abono registrado correctamente.";
        messageLabel.setText(confirmedMessage);
        try {
            showPortfolio(customerService.getAllCustomers());
            if (!lastQuerySucceeded) {
                messageLabel.setText(confirmedMessage
                        + " No se pudo actualizar la consulta de cartera; use Actualizar cartera sin registrar otro abono.");
            }
        } catch (RuntimeException refreshFailure) {
            clearPortfolioView();
            messageLabel.setText(confirmedMessage
                    + " No se pudo actualizar la cartera; use Actualizar cartera sin registrar otro abono.");
        }
    }

    @FXML
    private void handleRefreshPortfolio() {
        try {
            requirePermission(Permission.VIEW_PORTFOLIO, "No tiene permiso para consultar la cartera.");
            List<Customer> refreshed = customerService.getAllCustomers();
            showPortfolio(refreshed);
            if (lastQuerySucceeded) {
                messageLabel.setText("Cartera actualizada.");
            } else {
                messageLabel.setText("No se pudo consultar la cartera. Verifique la conexión y reintente.");
            }
        } catch (RuntimeException failure) {
            clearPortfolioView();
            messageLabel.setText("No se pudo consultar la cartera: " + safeMessage(failure)
                    + " Puede reintentar la consulta; no se registrará ningún abono.");
        }
    }

    private boolean refreshSelectedCustomer() {
        if (updatingView) return true;
        Customer selected = customerCombo.getValue();
        if (selected == null) {
            salesTable.getItems().clear();
            salesDebtLabel.setText("Deuda de ventas: —");
            generalDebtLabel.setText("Deuda general: —");
            return true;
        }
        try {
            BigDecimal salesDebt = salesWorkflow == null
                    ? BigDecimal.ZERO : salesWorkflow.outstandingSalesDebt(selected.getId());
            BigDecimal generalDebt = selected.getBalance().subtract(salesDebt);
            List<Sale> pendingSales = salesWorkflow == null ? List.of() : salesWorkflow.listAll().stream()
                    .filter(sale -> sale.getCustomer() != null
                            && selected.getId().equals(sale.getCustomer().getId()))
                    .filter(sale -> sale.calculateBalance().signum() > 0)
                    .toList();
            salesTable.getItems().setAll(pendingSales);
            salesDebtLabel.setText("Deuda de ventas: " + money(salesDebt));
            generalDebtLabel.setText(generalDebt.signum() < 0
                    ? "Inconsistencia: ventas superan el saldo agregado."
                    : "Deuda general: " + money(generalDebt));
            return true;
        } catch (RuntimeException failure) {
            clearPortfolioView();
            messageLabel.setText("No se pudo consultar la cartera: " + safeMessage(failure)
                    + " Use Actualizar cartera para reintentar.");
            return false;
        }
    }

    private void clearPortfolioView() {
        updatingView = true;
        portfolioTable.getItems().clear();
        salesTable.getItems().clear();
        customerCombo.getItems().clear();
        customerCombo.getSelectionModel().clearSelection();
        salesDebtLabel.setText("Deuda de ventas: no disponible");
        generalDebtLabel.setText("Deuda general: no disponible");
        updatingView = false;
        lastQuerySucceeded = false;
    }

    private void requirePermission(Permission permission, String message) {
        if (!authorizationService.hasPermission(session, permission)) throw new SecurityException(message);
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
