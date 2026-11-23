package com.laexclusiva.ui.controller;

import com.laexclusiva.model.AdministrativeAuditEvent;
import com.laexclusiva.model.CollectionChannel;
import com.laexclusiva.model.CollectionDashboard;
import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.CollectionProfile;
import com.laexclusiva.model.CollectionRecommendation;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AdministrativeAuditService;
import com.laexclusiva.service.CollectionAssistantService;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Presenta recomendaciones y herramientas de cobranza con permisos validados por los servicios. */
public final class CollectionsController {
    private static final Logger LOGGER = Logger.getLogger(CollectionsController.class.getName());

    @FXML private Label metricsLabel;
    @FXML private Label messageLabel;
    @FXML private TableView<CollectionRecommendation> recommendationsTable;
    @FXML private TableColumn<CollectionRecommendation, String> customerIdColumn;
    @FXML private TableColumn<CollectionRecommendation, String> customerNameColumn;
    @FXML private TableColumn<CollectionRecommendation, String> balanceColumn;
    @FXML private TableColumn<CollectionRecommendation, String> priorityColumn;
    @FXML private TableColumn<CollectionRecommendation, String> ageColumn;
    @FXML private TableColumn<CollectionRecommendation, String> explanationColumn;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TextField emailField;
    @FXML private ComboBox<CollectionChannel> channelCombo;
    @FXML private CheckBox consentCheck;
    @FXML private CheckBox remindersCheck;
    @FXML private DatePicker commitmentDatePicker;
    @FXML private TextField noteField;
    @FXML private TableView<CollectionFollowUp> historyTable;
    @FXML private TableColumn<CollectionFollowUp, String> historyDateColumn;
    @FXML private TableColumn<CollectionFollowUp, String> historyEventColumn;
    @FXML private TableColumn<CollectionFollowUp, String> historyNoteColumn;
    @FXML private TableView<CustomerCollectionReminder> remindersTable;
    @FXML private TableColumn<CustomerCollectionReminder, String> reminderCustomerColumn;
    @FXML private TableColumn<CustomerCollectionReminder, String> reminderChannelColumn;
    @FXML private TableColumn<CustomerCollectionReminder, String> reminderStatusColumn;
    @FXML private TableColumn<CustomerCollectionReminder, String> reminderBalanceColumn;
    @FXML private TableView<AdministrativeAuditEvent> auditTable;
    @FXML private TableColumn<AdministrativeAuditEvent, String> auditDateColumn;
    @FXML private TableColumn<AdministrativeAuditEvent, String> auditActorColumn;
    @FXML private TableColumn<AdministrativeAuditEvent, String> auditActionColumn;
    @FXML private TableColumn<AdministrativeAuditEvent, String> auditTargetColumn;
    @FXML private TableColumn<AdministrativeAuditEvent, String> auditResultColumn;
    @FXML private javafx.scene.layout.VBox administrationPane;

    private CollectionAssistantService assistant;
    private AdministrativeAuditService audit;
    private UserSession session;
    private com.laexclusiva.service.AuthorizationService authorization;

    @FXML
    private void initialize() {
        customerIdColumn.setCellValueFactory(cell -> text(cell.getValue().customerId()));
        customerNameColumn.setCellValueFactory(cell -> text(cell.getValue().customerName()));
        balanceColumn.setCellValueFactory(cell -> text(cell.getValue().outstandingBalance().toPlainString()));
        priorityColumn.setCellValueFactory(cell -> text(priorityLabel(cell.getValue().priority())));
        ageColumn.setCellValueFactory(cell -> text(cell.getValue().debtAge()));
        explanationColumn.setCellValueFactory(cell -> text(cell.getValue().explanation()));
        historyDateColumn.setCellValueFactory(cell -> text(cell.getValue().occurredAt().toString()));
        historyEventColumn.setCellValueFactory(cell -> text(cell.getValue().eventType().name()));
        historyNoteColumn.setCellValueFactory(cell -> text(cell.getValue().note() == null ? "" : cell.getValue().note()));
        reminderCustomerColumn.setCellValueFactory(cell -> text(cell.getValue().customerId()));
        reminderChannelColumn.setCellValueFactory(cell -> text(cell.getValue().channel().name()));
        reminderStatusColumn.setCellValueFactory(cell -> text(cell.getValue().status().name()));
        reminderBalanceColumn.setCellValueFactory(cell -> text(cell.getValue().balanceSnapshot().toPlainString()));
        auditDateColumn.setCellValueFactory(cell -> text(cell.getValue().occurredAt().toString()));
        auditActorColumn.setCellValueFactory(cell -> text(cell.getValue().actorUserId()));
        auditActionColumn.setCellValueFactory(cell -> text(cell.getValue().action().name()));
        auditTargetColumn.setCellValueFactory(cell -> text(cell.getValue().targetType() + ": "
                + cell.getValue().targetReference()));
        auditResultColumn.setCellValueFactory(cell -> text(cell.getValue().result().name()));
        customerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Customer customer) {
                return customer == null ? "" : customer.getName() + " (" + customer.getId() + ")";
            }
            @Override public Customer fromString(String value) { return null; }
        });
        channelCombo.getItems().setAll(CollectionChannel.values());
        channelCombo.setConverter(new StringConverter<>() {
            @Override public String toString(CollectionChannel channel) {
                return channel == null ? "" : switch (channel) {
                    case NONE -> "Sin autorización";
                    case EMAIL -> "Correo electrónico";
                    case PHONE -> "Teléfono (manual)";
                };
            }
            @Override public CollectionChannel fromString(String value) { return null; }
        });
        channelCombo.setValue(CollectionChannel.NONE);
    }

    /** Configura el tablero con la sesión actual; el detalle de contacto se entrega solo al administrador. */
    public void configure(CollectionAssistantService assistant, AdministrativeAuditService audit,
            List<Customer> customers, UserSession session,
            com.laexclusiva.service.AuthorizationService authorization) {
        if (assistant == null || audit == null || customers == null || session == null || authorization == null) {
            throw new IllegalArgumentException("La vista requiere servicios, clientes y sesión.");
        }
        authorization.requireSessionAuthority();
        if (!authorization.hasPermission(session, Permission.VIEW_PORTFOLIO)) {
            throw new SecurityException("No tiene permiso para consultar la Cartera.");
        }
        this.assistant = assistant;
        this.audit = audit;
        this.session = session;
        this.authorization = authorization;
        boolean administrator = authorization.hasPermission(session, Permission.MANAGE_COLLECTION);
        administrationPane.setVisible(administrator);
        administrationPane.setManaged(administrator);
        if (administrator) customerCombo.getItems().setAll(customers.stream().map(Customer::copy).toList());
        refreshDashboard();
        if (administrator) {
            refreshReminders();
            refreshAudit();
        }
    }

    @FXML private void handleRefresh() { refreshDashboard(); }

    @FXML
    private void handleCustomerSelected() {
        if (!isAdministrator() || customerCombo.getValue() == null) return;
        try {
            Customer selected = customerCombo.getValue();
            CollectionProfile profile = assistant.profile(session, selected.getId());
            emailField.setText(profile.email() == null ? "" : profile.email());
            channelCombo.setValue(profile.authorizedChannel());
            consentCheck.setSelected(profile.collectionConsent());
            remindersCheck.setSelected(profile.remindersEnabled());
            commitmentDatePicker.setValue(profile.commitmentDate());
            noteField.clear();
            historyTable.getItems().setAll(assistant.history(session, selected.getId()));
            messageLabel.setText("Preferencias cargadas. El teléfono se consulta del registro del cliente.");
        } catch (RuntimeException failure) {
            showSafeFailure("No se pudo consultar el perfil de cobranza.", failure);
        }
    }

    @FXML
    private void handleSaveProfile() {
        if (!isAdministrator()) { messageLabel.setText("Solo administración puede cambiar las preferencias."); return; }
        Customer customer = customerCombo.getValue();
        if (customer == null) { messageLabel.setText("Seleccione un cliente."); return; }
        try {
            CollectionProfile saved = assistant.saveProfile(session, customer.getId(), emailField.getText(),
                    channelCombo.getValue(), consentCheck.isSelected(), remindersCheck.isSelected(),
                    commitmentDatePicker.getValue(), noteField.getText());
            consentCheck.setSelected(saved.collectionConsent());
            remindersCheck.setSelected(saved.remindersEnabled());
            channelCombo.setValue(saved.authorizedChannel());
            if (!saved.collectionConsent()) emailField.clear();
            noteField.clear();
            historyTable.getItems().setAll(assistant.history(session, customer.getId()));
            messageLabel.setText(saved.collectionConsent()
                    ? "Preferencias y seguimiento guardados con autorización registrada."
                    : "Preferencias guardadas; los recordatorios quedan desactivados.");
            refreshDashboard();
        } catch (IllegalArgumentException invalid) {
            messageLabel.setText(UiErrorMessages.safeMessage(invalid));
        } catch (RuntimeException failure) {
            showSafeFailure("No se pudo guardar el seguimiento.", failure);
        }
    }

    @FXML
    private void handleGenerateStatement() {
        if (!hasPermission(Permission.VIEW_PORTFOLIO)) { messageLabel.setText("No tiene permiso para consultar Cartera."); return; }
        String customerId = selectedCustomerId();
        if (customerId == null) return;
        try {
            byte[] pdf = assistant.statementPdf(session, customerId);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar estado de cuenta comercial");
            chooser.setInitialFileName("Estado de cuenta - La Exclusiva.pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
            java.io.File file = chooser.showSaveDialog(recommendationsTable.getScene().getWindow());
            if (file != null) {
                Files.write(file.toPath(), pdf);
                messageLabel.setText("Estado de cuenta generado. No es una factura electrónica DIAN.");
            }
        } catch (RuntimeException | IOException failure) {
            showSafeFailure("No se pudo generar el estado de cuenta.", failure);
        }
    }

    @FXML private void handlePrepareReminders() {
        if (!isAdministrator()) { messageLabel.setText("Solo administración puede preparar recordatorios."); return; }
        try {
            CollectionAssistantService.PreparationResult result = assistant.prepareWeeklyReminders(session);
            remindersTable.getItems().setAll(assistant.listReminders(session, null));
            messageLabel.setText("Avisos nuevos para revisión: " + result.created()
                    + ". Los correos requieren selección y confirmación individual.");
            refreshDashboard();
        } catch (RuntimeException failure) {
            showSafeFailure("No se pudieron preparar los recordatorios.", failure);
        }
    }

    @FXML private void handleRefreshReminders() { refreshReminders(); }
    @FXML private void handleRefreshAudit() { refreshAudit(); }

    @FXML
    private void handlePreviewAndSend() {
        if (!isAdministrator()) { messageLabel.setText("Solo administración puede revisar envíos."); return; }
        CustomerCollectionReminder reminder = remindersTable.getSelectionModel().getSelectedItem();
        if (reminder == null) { messageLabel.setText("Seleccione un aviso."); return; }
        try {
            CollectionAssistantService.EmailPreview preview = assistant.previewReminder(session, reminder.id());
            String body;
            try (var document = Loader.loadPDF(preview.pdf())) {
                body = new PDFTextStripper().getText(document);
            }
            TextArea previewText = new TextArea("Para: " + preview.recipient() + "\nAsunto: " + preview.subject()
                    + "\n\n" + preview.text() + "\n\n--- Estado de cuenta adjunto ---\n" + body);
            previewText.setEditable(false);
            previewText.setWrapText(true);
            previewText.setPrefColumnCount(72);
            previewText.setPrefRowCount(24);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Revisar correo y estado de cuenta");
            confirm.setHeaderText("Revise destinatario, instrucciones y saldo antes de continuar.");
            confirm.getDialogPane().setContent(previewText);
            ButtonType send = new ButtonType("Enviar correo");
            ButtonType cancel = new ButtonType("Cancelar");
            confirm.getButtonTypes().setAll(send, cancel);
            if (confirm.showAndWait().orElse(cancel) != send) return;
            CollectionAssistantService.DeliveryResult result = assistant.sendReminder(session, reminder.id());
            messageLabel.setText(result.message());
            refreshReminders();
            refreshAudit();
            refreshDashboard();
        } catch (RuntimeException | IOException failure) {
            showSafeFailure("No se pudo preparar o entregar el aviso.", failure);
        }
    }

    private void refreshDashboard() {
        try {
            CollectionDashboard dashboard = assistant.dashboard(session);
            recommendationsTable.getItems().setAll(dashboard.recommendations());
            metricsLabel.setText("Compromisos vencidos: " + dashboard.overdueCommitments()
                    + " | Deudas 30+ días: " + dashboard.agedDebts()
                    + " | Próximos 3 días: " + dashboard.upcomingCommitments()
                    + " | Avisos pendientes de revisión: " + dashboard.statementsPendingReview());
            if (messageLabel != null && messageLabel.getText().isBlank()) messageLabel.setText("Tablero actualizado.");
        } catch (RuntimeException failure) {
            recommendationsTable.getItems().clear();
            metricsLabel.setText("Indicadores no disponibles.");
            showSafeFailure("No se pudo actualizar el tablero.", failure);
        }
    }

    private void refreshReminders() {
        if (!isAdministrator()) return;
        try { remindersTable.getItems().setAll(assistant.listReminders(session, null)); }
        catch (RuntimeException failure) { remindersTable.getItems().clear(); showSafeFailure("No se pudo consultar el lote.", failure); }
    }

    private void refreshAudit() {
        if (!isAdministrator()) return;
        try { auditTable.getItems().setAll(audit.list(session, LocalDate.now().minusDays(90), null, 100)); }
        catch (RuntimeException failure) { auditTable.getItems().clear(); showSafeFailure("No se pudo consultar la auditoría.", failure); }
    }

    private Customer selectedCustomer() {
        Customer selected = customerCombo.getValue();
        if (selected == null) messageLabel.setText("Seleccione un cliente.");
        return selected;
    }

    private String selectedCustomerId() {
        if (isAdministrator()) {
            Customer selected = selectedCustomer();
            return selected == null ? null : selected.getId();
        }
        CollectionRecommendation selected = recommendationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) messageLabel.setText("Seleccione un cliente de la lista de alertas.");
        return selected == null ? null : selected.customerId();
    }

    private boolean isAdministrator() { return hasPermission(Permission.MANAGE_COLLECTION); }
    private boolean hasPermission(Permission permission) {
        return authorization != null && session != null && authorization.hasPermission(session, permission);
    }

    private void showSafeFailure(String prefix, Throwable failure) {
        LOGGER.log(Level.WARNING, prefix + " Clase de error: " + failure.getClass().getSimpleName());
        messageLabel.setText(prefix + " Verifique los datos o la conexión y vuelva a consultar.");
    }

    private static String priorityLabel(CollectionRecommendation.Priority priority) {
        return switch (priority) {
            case HIGH -> "Alta";
            case MEDIUM -> "Media";
            case UPCOMING -> "Próximo seguimiento";
            case GENERAL -> "General";
        };
    }

    private static ReadOnlyStringWrapper text(Object value) {
        return new ReadOnlyStringWrapper(value == null ? "" : value.toString());
    }
}
