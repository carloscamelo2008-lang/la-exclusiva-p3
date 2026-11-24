package com.laexclusiva.ui;

import com.laexclusiva.demo.InteractiveDemoData;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.ui.controller.MainController;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteractiveDemoVisualTest {
    private static final String ADMIN = "Fixture-" + java.util.UUID.randomUUID();
    private static final String WORKER = "Fixture-" + java.util.UUID.randomUUID();
    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException started) { }
        Platform.setImplicitExit(false);
    }

    @Test void realSeededModulesShowTheirRowsAtAllReviewSizes() throws Exception {
        var loaded = InteractiveDemoData.create(InteractiveDemoData.configuration(null), ADMIN, WORKER);
        var services = loaded.services();
        var admin = services.identity().authentication().createSession("admin", ADMIN).orElseThrow();
        onFx(() -> {
            String[][] destinations = {{"btnCustomers", "customersTable", "15"}, {"btnSellers", "sellersTable", "5"},
                    {"btnInventory", "inventoryTable", "14"}, {"btnPurchases", "ordersTable", "10"},
                    {"btnSales", "historyTable", "30"}, {"btnCash", "movementsTable", Integer.toString(loaded.report().cashMovements())},
                    {"btnAdminUsers", "usersTable", "5"}};
            for (int[] size : new int[][]{{1280,800},{1024,768},{800,600}}) {
                FXMLLoader loader = loader("main_menu_view.fxml"); BorderPane root = loader.load();
                MainController controller = loader.getController(); controller.setApplicationServices(services); controller.initSession(admin);
                new Scene(root, size[0], size[1]); root.applyCss(); root.layout();
                for (String[] destination : destinations) {
                    ((Button) loader.getNamespace().get(destination[0])).fire(); root.applyCss(); root.layout();
                    assertEquals("", ((Label) loader.getNamespace().get("navigationMessage")).getText());
                    Parent content = content(root.getCenter());
                    if (destination[0].equals("btnCash")) {
                        ((DatePicker) content.lookup("#fromDatePicker")).setValue(java.time.LocalDate.now().minusDays(29));
                        ((DatePicker) content.lookup("#throughDatePicker")).setValue(java.time.LocalDate.now());
                        ((Button) content.lookup("#refreshButton")).fire(); root.applyCss(); root.layout();
                    }
                    TableView<?> table = (TableView<?>) content.lookup("#" + destination[1]);
                    assertNotNull(table, destination[0]); assertEquals(Integer.parseInt(destination[2]), table.getItems().size());
                    if (destination[0].equals("btnCustomers")) {
                        var ids = table.getItems().stream().map(item -> ((com.laexclusiva.model.Customer) item).getId()).toList();
                        assertEquals(java.util.stream.IntStream.rangeClosed(1, 15).mapToObj(i -> "CLI-%03d".formatted(i)).toList(), ids);
                    }
                    if (destination[0].equals("btnPurchases")) assertHeaderFits(tableById(content, "suppliersTable"), "Identificación tributaria");
                    if (destination[0].equals("btnSales")) {
                        assertHeaderFits(tableById(content, "cartTable"), "Cantidad");
                        assertVisibleCaption(content, "quantityField"); assertVisibleCaption(content, "initialPaymentField");
                    }
                    if (destination[0].equals("btnInventory")) {
                        ComboBox<?> types = (ComboBox<?>) content.lookup("#containerTypeCombo");
                        @SuppressWarnings("unchecked") var converter = ((ComboBox<ContainerType>) types).getConverter();
                        assertEquals("No retornable (lata)", converter.toString(ContainerType.NON_RETURNABLE_CAN));
                        assertSame(ContainerType.NON_RETURNABLE_CAN, types.getValue());
                        assertVisibleCaption(content, "fullBasketsField"); assertVisibleCaption(content, "looseUnitsField");
                    }
                    if (destination[0].equals("btnAdminUsers")) {
                        var roleColumn = table.getColumns().stream().filter(c -> c.getText().equals("Rol")).findFirst().orElseThrow();
                        assertEquals("Administrador", roleColumn.getCellData(0));
                        assertEquals("Trabajador", roleColumn.getCellData(1));
                        for (Node node : content.lookupAll(".button")) {
                            if (node instanceof Button button && button.getOnAction() != null) {
                                var text = new javafx.scene.text.Text(button.getText()); text.setFont(button.getFont());
                                assertTrue(text.getLayoutBounds().getWidth() + button.getInsets().getLeft()
                                        + button.getInsets().getRight() <= button.getWidth() + 1,
                                        "Acción administrativa recortada: " + button.getText());
                                assertTrue(button.localToScene(button.getBoundsInLocal()).getMaxX() <= root.getWidth() + 1);
                            }
                        }
                    }
                    snapshot(root, "demo-" + destination[0] + "-" + size[0]);
                    if (destination[0].equals("btnCash") || destination[0].equals("btnSales")) {
                        ScrollPane scroll = assertInstanceOf(ScrollPane.class, root.getCenter());
                        scroll.setVvalue(scroll.getVmax()); root.applyCss(); root.layout();
                        assertTrue(table.getHeight() >= 180, "El historial no debe quedar comprimido sin filas visibles");
                        var bounds = table.localToScene(table.getBoundsInLocal());
                        assertTrue(bounds.getMaxY() <= root.getHeight() + 1, "Historial accesible mediante desplazamiento");
                        snapshot(root, "demo-history-" + destination[0] + "-" + size[0]);
                    }
                }
                ((Button) loader.getNamespace().get("btnCredit")).fire(); root.applyCss(); root.layout();
                TableView<?> portfolio = tableById(content(root.getCenter()), "portfolioTable");
                assertTrue(portfolio.getItems().size() > 0); assertTrue(root.getCenter() instanceof ScrollPane);
                snapshot(root, "demo-portfolio-" + size[0]);
            }
            return null;
        });
    }

    @Test void labelledNumericInputsStayReachableAt560Pixels() throws Exception {
        onFx(() -> {
            for (String name : new String[]{"inventory", "sales"}) {
                FXMLLoader loader = loader(name + "_view.fxml"); Parent root = loader.load();
                new Scene(root, 560, 600); root.applyCss(); root.layout();
                String[] fields = name.equals("inventory") ? new String[]{"fullBasketsField", "looseUnitsField"}
                        : new String[]{"quantityField", "initialPaymentField"};
                Parent content = content(root);
                for (String id : fields) {
                    assertVisibleCaption(content, id);
                    Node field = (Node) loader.getNamespace().get(id);
                    assertTrue(field.localToScene(field.getBoundsInLocal()).getMaxX() <= 560);
                }
                snapshot(root, "demo-labelled-" + name + "-560");
            }
            return null;
        });
    }

    private static Parent content(Node view) { return (Parent) (view instanceof ScrollPane pane ? pane.getContent() : view); }
    private static TableView<?> tableById(Parent root, String id) { return (TableView<?>) root.lookup("#" + id); }
    private static void assertHeaderFits(TableView<?> table, String title) {
        Label label = table.lookupAll(".column-header .label").stream().filter(n -> n instanceof Label l && l.getText().equals(title))
                .map(n -> (Label) n).findFirst().orElseThrow();
        var text = new javafx.scene.text.Text(title); text.setFont(label.getFont());
        assertTrue(text.getLayoutBounds().getWidth() + label.getInsets().getLeft() + label.getInsets().getRight()
                <= label.getWidth() + 1, "Encabezado recortado: " + title);
    }
    private static void assertVisibleCaption(Parent root, String id) {
        Node field = root.lookup("#" + id); assertNotNull(field);
        Label caption = root.lookupAll(".label").stream().filter(n -> n instanceof Label l && l.getLabelFor() == field)
                .map(n -> (Label) n).findFirst().orElseThrow();
        assertTrue(caption.isVisible()); assertFalse(caption.getText().isBlank());
        assertFalse(field.getAccessibleText().isBlank());
    }
    private static void snapshot(Parent root, String name) throws Exception {
        var image = root.snapshot(null, null);
        var output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<output.getHeight();y++) for(int x=0;x<output.getWidth();x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        Files.createDirectories(Path.of("target/ui-review")); ImageIO.write(output,"png",Path.of("target/ui-review",name+".png").toFile());
    }
    private static FXMLLoader loader(String name) { return new FXMLLoader(InteractiveDemoVisualTest.class.getResource("/com/laexclusiva/ui/fxml/"+name)); }
    private static <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(60,TimeUnit.SECONDS);
    }
}
