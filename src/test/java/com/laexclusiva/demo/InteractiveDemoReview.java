package com.laexclusiva.demo;

import com.laexclusiva.ui.ApplicationServices;
import com.laexclusiva.ui.controller.LoginController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/** Herramienta manual de revisión; no sustituye el punto de entrada de producción. */
public final class InteractiveDemoReview {
    private InteractiveDemoReview() { }

    public static void main(String[] args) throws Exception {
        if (!"memory".equals(System.getenv("LAEXCLUSIVA_PERSISTENCE_MODE"))) {
            throw new IllegalStateException("La revisión requiere explícitamente modo memory.");
        }
        String adminPassword = System.getenv("LAEXCLUSIVA_DEMO_ADMIN_PASSWORD");
        String workerPassword = System.getenv("LAEXCLUSIVA_DEMO_WORKER_PASSWORD");
        var loaded = InteractiveDemoData.create(InteractiveDemoData.configuration(
                args.length == 0 ? null : Path.of(args[0])), adminPassword, workerPassword);
        System.out.println("DEMO SOLO MEMORIA: " + loaded.report());
        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                stage.sceneProperty().addListener((property, previous, current) -> decorateScene(current));
                stage.titleProperty().addListener((property, previous, current) -> {
                    if (current != null && !current.endsWith(" · Modo demostración")) {
                        stage.setTitle(current + " · Modo demostración");
                    }
                });
                showLogin(stage, loaded.services());
                ((TextField) stage.getScene().lookup("#usernameField")).setText("admin");
                ((PasswordField) stage.getScene().lookup("#passwordField")).setText(adminPassword);
                ((Button) stage.getScene().lookup("#loginButton")).fire();
                if (stage.getScene().lookup("#btnDashboard") == null) throw new IllegalStateException("Login demo rechazado");
                stage.setMaximized(true); stage.toFront();
                java.nio.file.Files.writeString(Path.of("target", "interactive-demo-report.txt"),
                        "DEMOSTRACIÓN FICTICIA - persistencia memory\n" + loaded.report()
                        + "\n" + loaded.services().cash().summarize(null, null)
                        + "\nVentas con fecha real; caja manual cubre los últimos 30 días.\n");
            } catch (Exception failure) {
                System.err.println("No fue posible abrir la demostración. No se conectó a bases externas.");
                Platform.exit();
            }
        });
    }

    private static void showLogin(Stage stage, ApplicationServices services) throws IOException {
        FXMLLoader loader = new FXMLLoader(InteractiveDemoReview.class.getResource("/com/laexclusiva/ui/fxml/login_view.fxml"));
        var root = loader.<javafx.scene.Parent>load();
        LoginController controller = loader.getController();
        controller.setApplicationServices(services);
        controller.setOnLogout(() -> {
            try { showLogin(stage, services); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
        stage.hide(); stage.setMaximized(false);
        stage.setMinWidth(400); stage.setMinHeight(370);
        stage.setTitle("La Exclusiva - Iniciar sesión");
        stage.setScene(new Scene(root, 400, 370)); stage.sizeToScene(); stage.centerOnScreen(); stage.show();
    }

    /** Indicador exclusivo de la herramienta, conservando el mensaje de navegación real. */
    static void decorateScene(Scene scene) {
        if (scene == null || !(scene.getRoot() instanceof javafx.scene.layout.BorderPane root)) return;
        javafx.scene.Node navigation = root.getBottom();
        root.setBottom(null);
        var footer = new javafx.scene.layout.HBox(12);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setPadding(new javafx.geometry.Insets(2, 12, 2, 12));
        if (navigation != null) {
            javafx.scene.layout.HBox.setHgrow(navigation, javafx.scene.layout.Priority.ALWAYS);
            footer.getChildren().add(navigation);
        }
        var badge = new javafx.scene.control.Label("Modo demostración");
        badge.setId("demoModeBadge"); badge.getStyleClass().add("muted"); badge.setMouseTransparent(true);
        badge.setAccessibleText("Modo demostración: datos sintéticos almacenados solo en memoria");
        footer.getChildren().add(badge); root.setBottom(footer);
    }
}
