package com.laexclusiva.demo;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteractiveDemoReviewTest {
    @Test void environmentBadgeDoesNotReplaceTheNavigationMessage() throws Exception {
        try { Platform.startup(() -> { }); } catch (IllegalStateException started) { }
        Platform.setImplicitExit(false);
        FutureTask<Void> check = new FutureTask<>(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
            BorderPane root = loader.load(); Scene scene = new Scene(root, 800, 600);
            var navigation = loader.getNamespace().get("navigationMessage");
            InteractiveDemoReview.decorateScene(scene); root.applyCss(); root.layout();
            assertEquals("Modo demostración", ((Label) root.lookup("#demoModeBadge")).getText());
            assertSame(navigation, root.lookup("#navigationMessage"));
            ((Label) navigation).setText("Mensaje de navegación conservado");
            assertEquals("Mensaje de navegación conservado", ((Label) root.lookup("#navigationMessage")).getText());
            assertNotNull(root.lookup("#btnLogout"));
            return null;
        });
        Platform.runLater(check); check.get(30, TimeUnit.SECONDS);
    }
}
