package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.service.CustomerGeneralDebtWorkflow;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
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
    @FXML private HBox generalDebtPane;
    @FXML private Button registerGeneralDebtButton;
    @FXML private Button payGeneralDebtButton;
    @FXML private TextField generalDebtAmountField;
    @FXML private TextField generalDebtCategoryField;
    @FXML private TextField generalDebtDescriptionField;
    @FXML private Label generalDebtOperationKeyLabel;
    @FXML private DatePicker movementFromDatePicker;
    @FXML private DatePicker movementThroughDatePicker;
    @FXML private TextField movementCategoryFilterField;
    @FXML private TableView<GeneralDebtMovement> generalDebtMovementsTable;
    @FXML private TableColumn<GeneralDebtMovement, String> generalDebtDateColumn;
    @FXML private TableColumn<GeneralDebtMovement, String> generalDebtTypeColumn;
    @FXML private TableColumn<GeneralDebtMovement, String> generalDebtCategoryColumn;
    @FXML private TableColumn<GeneralDebtMovement, BigDecimal> generalDebtAmountColumn;
    @FXML private TableColumn<GeneralDebtMovement, String> generalDebtDescriptionColumn;

    private CustomerService customerService;
    private SalesWorkflow salesWorkflow;
    private CustomerGeneralDebtWorkflow generalDebtWorkflow;
    private boolean canRegisterPayment;
    private UserSession session;
    private AuthorizationService authorizationService = new AuthorizationService();

    /** Inyecta la autoridad actual compartida con la aplicación. */
    public void setAuthorizationService(AuthorizationService authorizationService) {
        if (authorizationService == null) throw new IllegalArgumentException("La autorización es obligatoria.");
        this.authorizationService = authorizationService;
    }
    private String pendingSaleId;
    private BigDecimal pendingAmount;
    private String pendingKey;
    private boolean updatingView;
    private boolean lastQuerySucceeded = true;
    private String pendingGeneralKey;
    private String pendingGeneralFingerprint;

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
        generalDebtDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().occurredAt().toString()));
        generalDebtTypeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().type() == GeneralDebtMovementType.PAYMENT ? "Abono general" : "Deuda general"));
        generalDebtCategoryColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().category()));
        generalDebtAmountColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().amount()));
        generalDebtDescriptionColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().description()));
        customerCombo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                refreshSelectedCustomer());
        if (movementFromDatePicker != null) movementFromDatePicker.setValue(java.time.LocalDate.now().minusDays(30));
        if (movementThroughDatePicker != null) movementThroughDatePicker.setValue(java.time.LocalDate.now());
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
        configure(customerService, salesWorkflow, null, session);
    }

    /** Variante que habilita los movimientos auditables de deuda general al activar persistencia JDBC. */
    public void configure(CustomerService customerService, SalesWorkflow salesWorkflow,
                          CustomerGeneralDebtWorkflow generalDebtWorkflow, UserSession session) {
        if (customerService == null) throw new IllegalArgumentException("El servicio de clientes es obligatorio.");
        if (session == null) throw new IllegalArgumentException("La sesión es obligatoria.");
        this.customerService = customerService;
        this.salesWorkflow = salesWorkflow;
        this.generalDebtWorkflow = generalDebtWorkflow;
        this.session = session;
        requirePermission(Permission.VIEW_PORTFOLIO, "No tiene permiso para consultar la cartera.");
        requirePermission(Permission.VIEW_CUSTOMER, "No tiene permiso para consultar clientes.");
        this.canRegisterPayment = authorizationService.hasPermission(session, Permission.REGISTER_PAYMENT);
        boolean canRegisterGeneralDebt = authorizationService.hasPermission(session, Permission.REGISTER_GENERAL_DEBT);
        paymentPane.setVisible(canRegisterPayment && salesWorkflow != null);
        paymentPane.setManaged(canRegisterPayment && salesWorkflow != null);
        generalDebtPane.setVisible(generalDebtWorkflow != null && (canRegisterPayment || canRegisterGeneralDebt));
        generalDebtPane.setManaged(generalDebtWorkflow != null && (canRegisterPayment || canRegisterGeneralDebt));
        registerGeneralDebtButton.setVisible(canRegisterGeneralDebt);
        registerGeneralDebtButton.setManaged(canRegisterGeneralDebt);
        payGeneralDebtButton.setVisible(canRegisterPayment);
        payGeneralDebtButton.setManaged(canRegisterPayment);
        if (generalDebtOperationKeyLabel != null) {
            generalDebtOperationKeyLabel.setText(generalDebtWorkflow == null
                    ? "Deuda general no conectada." : "La clave se conserva durante reintentos inciertos.");
        }
        modeLabel.setText(salesWorkflow == null || !salesWorkflow.supportsDurableIdempotency()
                ? "Modo en memoria: las claves de reintento duran mientras la aplicación está abierta."
                : "Abonos de ventas y deuda general con claves idempotentes persistidas en PostgreSQL.");
    }

    /** Lista clientes con deuda total positiva y prepara el selector de cartera por venta. */
    public void showPortfolio(List<Customer> customers) {
        requirePermission(Permission.VIEW_PORTFOLIO, "No tiene permiso para consultar la cartera.");
        requirePermission(Permission.VIEW_CUSTOMER, "No tiene permiso para consultar clientes.");
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
            if (generalDebtMovementsTable != null) generalDebtMovementsTable.getItems().clear();
            salesDebtLabel.setText("Deuda de ventas: —");
            generalDebtLabel.setText("Deuda general: —");
            lastQuerySucceeded = true;
        } else {
            lastQuerySucceeded = refreshSelectedCustomer();
        }
    }

    public void showDebtors(List<Customer> debtors) {
        requirePermission(Permission.VIEW_PORTFOLIO, "No tiene permiso para consultar la cartera.");
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
            authorizationService.execute(session, Permission.REGISTER_PAYMENT,
                    () -> salesWorkflow.registerPayment(selected.getId(), amount, key));
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
            showPortfolio(authorizationService.execute(session, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers));
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
    private void handleRegisterGeneralDebt() {
        registerGeneralDebt(false);
    }

    @FXML
    private void handlePayGeneralDebt() {
        registerGeneralDebt(true);
    }

    private void registerGeneralDebt(boolean payment) {
        Customer selected;
        BigDecimal amount;
        String category;
        String description;
        String fingerprint;
        try {
            requirePermission(payment ? Permission.REGISTER_PAYMENT : Permission.REGISTER_GENERAL_DEBT,
                    payment ? "No tiene permiso para abonar deuda general."
                            : "No tiene permiso para registrar deuda general.");
            if (generalDebtWorkflow == null) throw new IllegalStateException("La deuda general no está conectada.");
            selected = requireCustomerSelection();
            amount = new BigDecimal(generalDebtAmountField.getText().trim());
            category = requireText(generalDebtCategoryField.getText(), "La categoría es obligatoria.");
            description = requireText(generalDebtDescriptionField.getText(), "La descripción es obligatoria.");
            fingerprint = String.join("|", payment ? "PAYMENT" : "DEBT", selected.getId(),
                    amount.stripTrailingZeros().toPlainString(), category, description);
            if (pendingGeneralFingerprint != null && !pendingGeneralFingerprint.equals(fingerprint)) {
                throw new IllegalStateException("Hay una operación general de resultado incierto. Reintente los "
                        + "mismos datos antes de iniciar otra operación.");
            }
            if (pendingGeneralKey == null) pendingGeneralKey = UUID.randomUUID().toString();
            if (generalDebtOperationKeyLabel != null) {
                generalDebtOperationKeyLabel.setText("Referencia de deuda general: " + pendingGeneralKey);
            }
        } catch (RuntimeException invalid) {
            messageLabel.setText("Operación general no enviada: " + safeMessage(invalid));
            return;
        }

        try {
            if (payment) {
                authorizationService.execute(session, Permission.REGISTER_PAYMENT,
                        () -> generalDebtWorkflow.registerPayment(selected.getId(), amount, category,
                                description, pendingGeneralKey));
            } else {
                authorizationService.execute(session, Permission.REGISTER_GENERAL_DEBT,
                        () -> generalDebtWorkflow.registerDebt(selected.getId(), amount, category,
                                description, pendingGeneralKey));
            }
        } catch (IllegalArgumentException | SecurityException rejected) {
            pendingGeneralKey = null;
            pendingGeneralFingerprint = null;
            if (generalDebtOperationKeyLabel != null) {
                generalDebtOperationKeyLabel.setText("La clave se genera al registrar la siguiente operación.");
            }
            messageLabel.setText(payment ? "Abono general rechazado: " + safeMessage(rejected)
                    : "Deuda general rechazada: " + safeMessage(rejected));
            return;
        } catch (RuntimeException uncertain) {
            pendingGeneralFingerprint = fingerprint;
            messageLabel.setText("No se pudo confirmar la operación general: " + safeMessage(uncertain)
                    + " Se conserva su referencia; reintente con los mismos datos.");
            return;
        }

        pendingGeneralKey = null;
        pendingGeneralFingerprint = null;
        generalDebtAmountField.clear();
        generalDebtCategoryField.clear();
        generalDebtDescriptionField.clear();
        if (generalDebtOperationKeyLabel != null) {
            generalDebtOperationKeyLabel.setText("La clave se genera al registrar la siguiente operación.");
        }
        messageLabel.setText(payment ? "Abono aplicado solo a deuda general." : "Deuda general registrada.");
        try {
            showPortfolio(authorizationService.execute(session, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers));
            if (!lastQuerySucceeded) {
                messageLabel.setText(confirmedGeneralOperationMessage(payment)
                        + " No se pudo actualizar la consulta de cartera; use Actualizar cartera sin repetir la operación.");
            }
        } catch (RuntimeException refreshFailure) {
            clearPortfolioView();
            messageLabel.setText(confirmedGeneralOperationMessage(payment)
                    + " No se pudo actualizar la cartera: " + safeMessage(refreshFailure)
                    + " Use Actualizar cartera sin repetir la operación.");
        }
    }

    private static String confirmedGeneralOperationMessage(boolean payment) {
        return payment ? "Abono general confirmado." : "Deuda general registrada correctamente.";
    }

    @FXML
    private void handleRefreshPortfolio() {
        try {
            requirePermission(Permission.VIEW_PORTFOLIO, "No tiene permiso para consultar la cartera.");
            List<Customer> refreshed = authorizationService.execute(session, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers);
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
        if (!authorizationService.hasPermission(session, Permission.VIEW_PORTFOLIO)
                || !authorizationService.hasPermission(session, Permission.VIEW_CUSTOMER)) {
            clearPortfolioView();
            messageLabel.setText("La sesión ya no permite consultar la cartera.");
            return false;
        }
        Customer selected = customerCombo.getValue();
        if (selected == null) {
            salesTable.getItems().clear();
            salesDebtLabel.setText("Deuda de ventas: —");
            generalDebtLabel.setText("Deuda general: —");
            return true;
        }
        try {
            BigDecimal salesDebt;
            BigDecimal generalDebt;
            if (generalDebtWorkflow != null) {
                CustomerDebtBreakdown breakdown = authorizationService.execute(session, Permission.VIEW_PORTFOLIO,
                        () -> generalDebtWorkflow.findBalance(selected.getId()));
                salesDebt = breakdown.outstandingSalesDebt();
                generalDebt = breakdown.generalDebt();
            } else {
                salesDebt = salesWorkflow == null ? BigDecimal.ZERO
                        : authorizationService.execute(session, Permission.VIEW_PORTFOLIO,
                            () -> salesWorkflow.outstandingSalesDebt(selected.getId()));
                generalDebt = selected.getBalance().subtract(salesDebt);
            }
            List<Sale> pendingSales = salesWorkflow == null ? List.of()
                    : authorizationService.execute(session, Permission.VIEW_SALES, salesWorkflow::listAll).stream()
                    .filter(sale -> sale.getCustomer() != null
                            && selected.getId().equals(sale.getCustomer().getId()))
                    .filter(sale -> sale.calculateBalance().signum() > 0)
                    .toList();
            salesTable.getItems().setAll(pendingSales);
            salesDebtLabel.setText("Deuda de ventas: " + money(salesDebt));
            generalDebtLabel.setText(generalDebt.signum() < 0
                    ? "Inconsistencia: ventas superan el saldo agregado."
                    : "Deuda general: " + money(generalDebt));
            if (generalDebtWorkflow != null) {
                generalDebtMovementsTable.getItems().setAll(authorizationService.execute(session,
                        Permission.VIEW_PORTFOLIO, () -> generalDebtWorkflow.listMovements(selected.getId(),
                                movementFromDatePicker.getValue(), movementThroughDatePicker.getValue(),
                                movementCategoryFilterField.getText())));
            }
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
        if (generalDebtMovementsTable != null) generalDebtMovementsTable.getItems().clear();
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

    @FXML
    private void handleRefreshGeneralDebtMovements() {
        try {
            requirePermission(Permission.VIEW_PORTFOLIO, "No tiene permiso para consultar la cartera.");
            if (generalDebtWorkflow == null) throw new IllegalStateException("El historial general no está conectado.");
            if (!refreshSelectedCustomer()) throw new IllegalStateException("No se pudo actualizar la deuda general.");
            messageLabel.setText("Movimientos de deuda general actualizados.");
        } catch (RuntimeException failure) {
            messageLabel.setText("No se pudo consultar el historial general: " + safeMessage(failure));
        }
    }

    private Customer requireCustomerSelection() {
        Customer selected = customerCombo.getValue();
        if (selected == null) throw new IllegalArgumentException("Seleccione el cliente.");
        return selected;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
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
