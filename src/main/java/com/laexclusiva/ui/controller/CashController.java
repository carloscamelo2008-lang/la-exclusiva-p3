package com.laexclusiva.ui.controller;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CashMovement;
import com.laexclusiva.model.CashSummary;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.CashWorkflow;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/** Interfaz de caja para registrar gastos/otros ingresos y consultar recaudos reales. */
public final class CashController {
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker throughDatePicker;
    @FXML private ComboBox<CashCategory> categoryFilterCombo;
    @FXML private TableView<CashMovement> movementsTable;
    @FXML private TableColumn<CashMovement, String> dateColumn;
    @FXML private TableColumn<CashMovement, String> directionColumn;
    @FXML private TableColumn<CashMovement, String> categoryColumn;
    @FXML private TableColumn<CashMovement, BigDecimal> amountColumn;
    @FXML private TableColumn<CashMovement, String> descriptionColumn;
    @FXML private DatePicker movementDatePicker;
    @FXML private ComboBox<CashDirection> directionCombo;
    @FXML private ComboBox<CashCategory> entryCategoryCombo;
    @FXML private TextField amountField;
    @FXML private TextField descriptionField;
    @FXML private Button registerMovementButton;
    @FXML private Label operationKeyLabel;
    @FXML private Label invoicedSalesLabel;
    @FXML private Label saleReceiptsLabel;
    @FXML private Label generalDebtReceiptsLabel;
    @FXML private Label otherIncomeLabel;
    @FXML private Label expensesLabel;
    @FXML private Label cashInLabel;
    @FXML private Label netCashLabel;
    @FXML private Label messageLabel;

    private final AuthorizationService authorization = new AuthorizationService();
    private CashWorkflow cash;
    private UserSession session;
    private String operationKey = UUID.randomUUID().toString();
    private String uncertainFingerprint;
    private LocalDateTime pendingOccurredAt;

    @FXML
    private void initialize() {
        dateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().occurredAt().toString()));
        directionColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().direction() == CashDirection.INCOME ? "Ingreso" : "Egreso"));
        categoryColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().category().label()));
        amountColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().amount()));
        descriptionColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().description()));
        directionCombo.getItems().setAll(CashDirection.values());
        directionCombo.setConverter(converter(direction -> direction == CashDirection.INCOME ? "Ingreso" : "Egreso"));
        directionCombo.setValue(CashDirection.INCOME);
        directionCombo.valueProperty().addListener((observable, oldValue, newValue) -> refreshEntryCategories());
        entryCategoryCombo.setConverter(converter(CashCategory::label));
        categoryFilterCombo.getItems().setAll(CashCategory.values());
        categoryFilterCombo.setConverter(converter(CashCategory::label));
        categoryFilterCombo.setPromptText("Todas las categorías");
        fromDatePicker.setValue(LocalDate.now());
        throughDatePicker.setValue(LocalDate.now());
        movementDatePicker.setValue(LocalDate.now());
        refreshEntryCategories();
        operationKeyLabel.setText("Referencia de operación: " + operationKey);
    }

    /** Configura el caso de uso y limita la captura financiera a usuarios autorizados. */
    public void configure(CashWorkflow cash, UserSession session) {
        if (cash == null || session == null) throw new IllegalArgumentException("Caja y sesión son obligatorias.");
        this.cash = cash;
        this.session = session;
        boolean canView = authorization.hasPermission(session, Permission.VIEW_CASH);
        boolean canRegister = authorization.hasPermission(session, Permission.REGISTER_CASH_MOVEMENT);
        registerMovementButton.setVisible(canRegister);
        registerMovementButton.setManaged(canRegister);
        amountField.setDisable(!canRegister);
        descriptionField.setDisable(!canRegister);
        movementDatePicker.setDisable(!canRegister);
        directionCombo.setDisable(!canRegister);
        entryCategoryCombo.setDisable(!canRegister);
        if (canView) {
            refresh();
        } else {
            movementsTable.getItems().clear();
            messageLabel.setText("No tiene permiso para consultar caja.");
        }
    }

    @FXML
    private void handleRegisterMovement() {
        String fingerprint;
        CashDirection direction;
        CashCategory category;
        BigDecimal amount;
        LocalDate date;
        LocalDateTime occurredAt;
        String description;
        try {
            requirePermission(Permission.REGISTER_CASH_MOVEMENT,
                    "Solo un usuario con permiso de caja puede registrar movimientos.");
            direction = require(directionCombo.getValue(), "Seleccione ingreso o egreso.");
            category = require(entryCategoryCombo.getValue(), "Seleccione una categoría.");
            amount = new BigDecimal(amountField.getText().trim());
            date = require(movementDatePicker.getValue(), "Seleccione la fecha.");
            description = requireText(descriptionField.getText());
            occurredAt = uncertainFingerprint == null ? date.atTime(LocalTime.now()) : pendingOccurredAt;
            fingerprint = String.join("|", direction.name(), category.name(), date.toString(),
                    amount.stripTrailingZeros().toPlainString(), occurredAt.toString(), description);
            if (uncertainFingerprint != null && !uncertainFingerprint.equals(fingerprint)) {
                throw new IllegalArgumentException("Hay un movimiento de resultado incierto. Restaure los mismos "
                        + "datos y reintente con la referencia visible antes de iniciar otro movimiento.");
            }
            pendingOccurredAt = occurredAt;
        } catch (RuntimeException invalid) {
            messageLabel.setText("Movimiento no enviado: " + safeMessage(invalid));
            return;
        }

        try {
            cash.registerMovement(direction, category, amount, occurredAt,
                    description, operationKey);
        } catch (IllegalArgumentException | SecurityException rejected) {
            if (uncertainFingerprint == null) pendingOccurredAt = null;
            messageLabel.setText("Movimiento rechazado: " + safeMessage(rejected));
            return;
        } catch (RuntimeException uncertain) {
            uncertainFingerprint = fingerprint;
            messageLabel.setText("No se pudo confirmar el movimiento: " + safeMessage(uncertain)
                    + " La referencia se conserva; verifique o reintente exactamente los mismos datos.");
            return;
        }

        uncertainFingerprint = null;
        pendingOccurredAt = null;
        operationKey = UUID.randomUUID().toString();
        operationKeyLabel.setText("Referencia de operación: " + operationKey);
        amountField.clear();
        descriptionField.clear();
        messageLabel.setText("Movimiento registrado.");
        if (authorization.hasPermission(session, Permission.VIEW_CASH)) refresh();
    }

    @FXML
    private void handleRefresh() {
        try {
            requirePermission(Permission.VIEW_CASH, "No tiene permiso para consultar caja.");
            refresh();
            messageLabel.setText("Caja actualizada.");
        } catch (RuntimeException failure) {
            movementsTable.getItems().clear();
            messageLabel.setText("No se pudo consultar caja: " + safeMessage(failure));
        }
    }

    private void refresh() {
        List<CashMovement> records = cash.listMovements(fromDatePicker.getValue(),
                throughDatePicker.getValue(), categoryFilterCombo.getValue());
        movementsTable.getItems().setAll(records);
        CashSummary summary = cash.summarize(fromDatePicker.getValue(), throughDatePicker.getValue());
        invoicedSalesLabel.setText("Ventas facturadas: " + money(summary.invoicedSales()));
        saleReceiptsLabel.setText("Efectivo recibido por ventas: " + money(summary.saleReceipts()));
        generalDebtReceiptsLabel.setText("Pagos de deuda general: " + money(summary.generalDebtReceipts()));
        otherIncomeLabel.setText("Otros ingresos: " + money(summary.otherIncome()));
        expensesLabel.setText("Egresos: " + money(summary.expenses()));
        cashInLabel.setText("Ingresos reales de caja: " + money(summary.cashIn()));
        netCashLabel.setText("Saldo neto de caja: " + money(summary.netCash()));
    }

    private void refreshEntryCategories() {
        CashDirection direction = directionCombo.getValue();
        if (direction == null) return;
        List<CashCategory> allowed = java.util.Arrays.stream(CashCategory.values())
                .filter(CashCategory::isManualEntry).filter(category -> category.direction() == direction).toList();
        entryCategoryCombo.getItems().setAll(allowed);
        if (!allowed.contains(entryCategoryCombo.getValue())) entryCategoryCombo.setValue(allowed.get(0));
    }

    private void requirePermission(Permission permission, String message) {
        if (!authorization.hasPermission(session, permission)) throw new SecurityException(message);
    }

    private static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$ " + format.format(value);
    }

    private static String requireText(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("La descripción es obligatoria.");
        return text.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank() ? "Error inesperado." : failure.getMessage();
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : label.apply(value); }
            @Override public T fromString(String text) { return null; }
        };
    }
}
