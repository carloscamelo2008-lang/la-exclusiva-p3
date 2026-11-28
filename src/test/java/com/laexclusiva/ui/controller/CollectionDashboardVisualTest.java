package com.laexclusiva.ui.controller;

import com.laexclusiva.demo.InteractiveDemoData;
import com.laexclusiva.model.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionDashboardVisualTest {
    private static final String ADMIN="Fixture-"+java.util.UUID.randomUUID();
    private static final String WORKER="Fixture-"+java.util.UUID.randomUUID();
    @BeforeAll static void toolkit(){try{Platform.startup(()->{});}catch(IllegalStateException started){} Platform.setImplicitExit(false);}

    @Test void realMetricsRecommendationsAndModuleRespectBothRolesAtEverySize() throws Exception {
        var services=InteractiveDemoData.create(InteractiveDemoData.configuration(null),ADMIN,WORKER).services();
        for(String username:new String[]{"admin","operador"}) {
            var session=services.identity().authentication().createSession(username,username.equals("admin")?ADMIN:WORKER).orElseThrow();
            var expected=services.collectionAssistant().dashboard(session);
            assertTrue(expected.overdueCommitments()>0,"vencidos: "+expected); assertTrue(expected.upcomingCommitments()>0,"próximos: "+expected);
            assertTrue(expected.statementsPendingReview()>0,"avisos: "+expected); assertTrue(expected.recommendations().stream().anyMatch(CollectionRecommendation::recentPayment),"pagos: "+expected);
            onFx(()->{
                for(int[] size:new int[][]{{1920,1080},{1280,800},{1024,768},{800,600}}){
                    FXMLLoader loader=new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/main_menu_view.fxml"));
                    BorderPane root=loader.load(); MainController controller=loader.getController(); controller.setApplicationServices(services);controller.initSession(session);
                    new Scene(root,size[0],size[1]);root.applyCss();root.layout();
                    CollectionDashboardView panel=(CollectionDashboardView)loader.getNamespace().get("collectionDashboard");
                    assertTrue(panel.isVisible());
                    assertEquals(expected.recommendations().size(),((TableView<?>)panel.lookup("#dashboardRecommendations")).getItems().size());
                    assertTrue(panel.lookupAll(".collection-metric").stream().anyMatch(n->n.getAccessibleText().equals("Compromisos vencidos: "+expected.overdueCommitments())));
                    ScrollPane dashboard=(ScrollPane)root.getCenter();dashboard.setVvalue(1);root.layout();
                    snapshot(root,"collections-dashboard-"+username+"-"+size[0]);
                    ((Button)panel.lookup("#openCollectionsDashboard")).fire();root.applyCss();root.layout();
                    assertEquals("",((Label)loader.getNamespace().get("navigationMessage")).getText());
                    VBox administration=(VBox)root.getCenter().lookup("#administrationPane");
                    assertEquals(username.equals("admin"),administration.isVisible());assertEquals(administration.isVisible(),administration.isManaged());
                    assertFalse(((TableView<?>)root.getCenter().lookup("#recommendationsTable")).getItems().isEmpty());
                    snapshot(root,"collections-module-"+username+"-"+size[0]);
                }
                return null;
            });
        }
    }

    @Test void indicatorsRefreshAndRevocationRemovesPreviouslyVisibleFinancialData() throws Exception {
        var s=InteractiveDemoData.create(InteractiveDemoData.configuration(null),ADMIN,WORKER).services();
        var admin=s.identity().authentication().createSession("admin",ADMIN).orElseThrow();
        onFx(()->{
            FXMLLoader loader=new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/main_menu_view.fxml"));BorderPane root=loader.load();
            MainController controller=loader.getController();controller.setApplicationServices(s);controller.initSession(admin);new Scene(root,1280,800);root.applyCss();root.layout();
            CollectionDashboardView panel=(CollectionDashboardView)loader.getNamespace().get("collectionDashboard");
            int before=s.collectionAssistant().dashboard(admin).overdueCommitments();
            s.collectionAssistant().saveProfile(admin,"CLI-009","lucia@example.test",CollectionChannel.EMAIL,true,true,java.time.LocalDate.now().minusDays(1),"Cambio de compromiso");
            ((Button)loader.getNamespace().get("btnDashboard")).fire();root.applyCss();root.layout();
            assertTrue(panel.lookupAll(".collection-metric").stream().anyMatch(n->n.getAccessibleText().equals("Compromisos vencidos: "+(before+1))));
            s.identity().authentication().revoke(admin);((Button)loader.getNamespace().get("btnDashboard")).fire();
            assertFalse(panel.isVisible());assertTrue(panel.getChildren().isEmpty());return null;
        });
    }

    @Test void manualSessionFailsClosedWithoutFinancialFallback() throws Exception {
        var s=InteractiveDemoData.create(InteractiveDemoData.configuration(null),ADMIN,WORKER).services();
        onFx(()->{var panel=new CollectionDashboardView();panel.refresh(s,new UserSession(s.identity().users().findByUsername("admin").orElseThrow()),()->fail());
            assertFalse(panel.isVisible());assertTrue(panel.getChildren().isEmpty());return null;});
    }

    @Test void collectionFormsWrapAt560AndAuditRemainsBehindAdministrativePermission() throws Exception {
        var s=InteractiveDemoData.create(InteractiveDemoData.configuration(null),ADMIN,WORKER).services();
        var admin=s.identity().authentication().createSession("admin",ADMIN).orElseThrow();
        onFx(()->{FXMLLoader loader=new FXMLLoader(getClass().getResource("/com/laexclusiva/ui/fxml/collections_view.fxml"));Parent root=loader.load();
            ((CollectionsController)loader.getController()).configure(s.collectionAssistant(),s.administrativeAudit(),s.customers().getAllCustomers(),admin,s.authorization());
            new Scene(root,560,600);root.applyCss();root.layout();
            for(String id:new String[]{"customerCombo","channelCombo","commitmentDatePicker","noteField"}){
                Node field=(Node)loader.getNamespace().get(id);assertTrue(field.localToScene(field.getBoundsInLocal()).getMaxX()<=561,id);assertFalse(field.getAccessibleText().isBlank());
            }
            assertFalse(((TableView<?>)loader.getNamespace().get("auditTable")).getItems().isEmpty());snapshot(root,"collections-form-560");return null;});
    }

    private static <T>T onFx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(90,TimeUnit.SECONDS);}
    private static void snapshot(Parent root,String name)throws Exception{var image=root.snapshot(null,null);var png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));Files.createDirectories(Path.of("target/ui-review"));ImageIO.write(png,"png",Path.of("target/ui-review",name+".png").toFile());}
}
