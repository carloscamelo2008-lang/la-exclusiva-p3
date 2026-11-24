package com.laexclusiva.ui.controller;

import com.laexclusiva.model.CollectionRecommendation;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.ui.ApplicationServices;
import java.text.NumberFormat;
import java.util.Locale;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Presentación del contrato protegido de Carlos, sin almacenamiento ni cálculos de deuda. */
public final class CollectionDashboardView extends VBox {
    public CollectionDashboardView() { getStyleClass().add("card"); setSpacing(14); setMinWidth(0); }

    /** Retira todos los datos financieros antes de revalidar o cerrar la sesión. */
    public void clear() { getChildren().clear(); setVisible(false); setManaged(false); }

    public void refresh(ApplicationServices services, UserSession session, Runnable openModule) {
        clear();
        if (services == null || session == null) return;
        try {
            if (!services.authorization().hasPermission(session, Permission.VIEW_PORTFOLIO)) return;
            var data = services.collectionAssistant().dashboard(session);
            Label title = new Label("Alertas y recordatorios"); title.getStyleClass().add("section-title");
            FlowPane metrics = new FlowPane(12, 12);
            metrics.getChildren().addAll(metric("!", "Compromisos vencidos", data.overdueCommitments(), "urgent"),
                    metric("◷", "Deudas verificadas de 30+ días", data.agedDebts(), "attention"),
                    metric("↗", "Compromisos próximos (3 días)", data.upcomingCommitments(), "upcoming"),
                    metric("▤", "Avisos pendientes de revisión", data.statementsPendingReview(), "review"));
            TableView<CollectionRecommendation> table = new TableView<>();
            table.setId("dashboardRecommendations"); table.setPrefHeight(220); table.setMinHeight(180);
            table.setAccessibleText("Recomendaciones actuales de Cartera");
            column(table, "Cliente", 200, r -> r.customerName());
            column(table, "Saldo (COP)", 150, r -> NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(r.outstandingBalance()));
            column(table, "Prioridad", 150, r -> priority(r.priority()));
            column(table, "Motivo", 560, r -> r.explanation());
            table.setPlaceholder(new Label("No hay recomendaciones con saldo pendiente."));
            table.getItems().setAll(data.recommendations());
            Button open = new Button("Abrir Alertas y recordatorios"); open.setId("openCollectionsDashboard");
            open.setWrapText(true); open.setOnAction(e -> openModule.run());
            getChildren().setAll(title, metrics, table, open); setVisible(true); setManaged(true);
        } catch (RuntimeException unavailable) {
            clear(); // Un fallo no conserva saldos de una consulta anterior ni expone su excepción.
        }
    }

    private static VBox metric(String icon, String caption, int value, String severity) {
        Label number = new Label(icon + "  " + value); number.getStyleClass().add("collection-count");
        Label text = new Label(caption); text.setWrapText(true); text.setMaxWidth(210);
        VBox box = new VBox(8, number, text); box.setPrefWidth(230); box.setMinWidth(0);
        box.getStyleClass().addAll("collection-metric", severity); box.setAccessibleText(caption + ": " + value);
        return box;
    }

    private static void column(TableView<CollectionRecommendation> table, String name, double width,
            java.util.function.Function<CollectionRecommendation,String> value) {
        TableColumn<CollectionRecommendation,String> column = new TableColumn<>(name);
        column.setPrefWidth(width); column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        table.getColumns().add(column);
    }

    static String priority(CollectionRecommendation.Priority priority) {
        return switch (priority) { case HIGH -> "Alta"; case MEDIUM -> "Media";
            case UPCOMING -> "Próximo seguimiento"; case GENERAL -> "General"; };
    }
}
