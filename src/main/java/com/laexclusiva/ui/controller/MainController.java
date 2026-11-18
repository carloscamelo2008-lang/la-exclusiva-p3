package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SellerService;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.ui.ApplicationServices;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    @FXML private BorderPane rootPane;
    @FXML private Node dashboard;
    @FXML private Label navigationMessage;
    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Button btnSellers;
    @FXML private Button btnInventory;
    @FXML private Button btnSales;
    @FXML private Button btnAdminUsers;
    @FXML private Button btnDashboard;
    @FXML private Button btnCustomers;
    @FXML private Button btnCredit;

    private UserSession currentSession;
    private CustomerService customerService;
    private SellerService sellerService;
    private ProductService productService;
    private InventoryService inventoryService;
    private SalesWorkflow salesWorkflow;
    private Runnable onLogout;
    private final AuthorizationService authorizationService = new AuthorizationService();

    public void setCustomerService(CustomerService customerService) {
        this.customerService = customerService;
    }

    public void setSellerService(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    public void setApplicationServices(ApplicationServices services) {
        if (services == null) {
            throw new IllegalArgumentException("La composición de servicios es obligatoria.");
        }
        this.customerService = services.customers();
        this.sellerService = services.sellers();
        this.productService = services.products();
        this.inventoryService = services.inventory();
        this.salesWorkflow = services.sales();
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    @FXML
    private void handleLogout() {
        try {
            if (onLogout == null) {
                throw new IllegalStateException("El regreso al inicio de sesión no está configurado.");
            }
            onLogout.run();
            currentSession.invalidate();
            currentSession = null;
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo cerrar la sesión.", failure);
            navigationMessage.setText("No se pudo volver al inicio de sesión. Intente de nuevo.");
        }
    }

    private void selectNavigation(Button selected) {
        for (Button button : new Button[]{btnDashboard, btnCustomers, btnSellers, btnCredit,
                btnInventory, btnSales}) {
            button.pseudoClassStateChanged(ACTIVE, button == selected);
        }
    }

    @FXML
    private void handleShowCustomers() {
        if (!authorizationService.hasPermission(currentSession, Permission.VIEW_CUSTOMER)) {
            navigationMessage.setText("No tiene permiso para consultar clientes.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/customers_view.fxml"));
            Node customerView = loader.load();
            CustomerController controller = loader.getController();
            controller.configure(customerService,
                    authorizationService.hasPermission(currentSession, Permission.REGISTER_CUSTOMER));
            controller.showCustomers(customerService.getAllCustomers());
            rootPane.setCenter(customerView);
            selectNavigation(btnCustomers);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir la consulta de clientes.", failure);
            navigationMessage.setText("No se pudo consultar la lista de clientes.");
        }
    }

    @FXML
    private void handleShowSellers() {
        if (!authorizationService.hasPermission(currentSession, Permission.VIEW_SELLERS)) {
            navigationMessage.setText("No tiene permiso para consultar vendedores.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sellers_view.fxml"));
            Node sellerView = loader.load();
            SellerController controller = loader.getController();
            controller.configure(sellerService,
                    authorizationService.hasPermission(currentSession, Permission.REGISTER_SELLERS));
            controller.showSellers(sellerService.getAllSellers());
            rootPane.setCenter(sellerView);
            selectNavigation(btnSellers);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir la consulta de vendedores.", failure);
            navigationMessage.setText("No se pudo consultar la lista de vendedores.");
        }
    }

    @FXML
    private void handleShowPortfolio() {
        if (!authorizationService.hasPermission(currentSession, Permission.VIEW_PORTFOLIO)) {
            navigationMessage.setText("No tiene permiso para consultar la cartera.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/portfolio_view.fxml"));
            Node portfolioView = loader.load();
            PortfolioController controller = loader.getController();
            controller.configure(customerService, salesWorkflow,
                    authorizationService.hasPermission(currentSession, Permission.REGISTER_PAYMENT));
            controller.showPortfolio(customerService.getAllCustomers());
            rootPane.setCenter(portfolioView);
            selectNavigation(btnCredit);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir la consulta de cartera.", failure);
            navigationMessage.setText("No se pudo consultar la cartera.");
        }
    }

    @FXML
    private void handleShowSales() {
        if (!authorizationService.hasPermission(currentSession, Permission.REGISTER_SALE)
                || !authorizationService.hasPermission(currentSession, Permission.VIEW_SALES)) {
            handleShowDashboard();
            navigationMessage.setText("No tiene permiso para registrar ventas.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            Node view = loader.load();
            SalesController controller = loader.getController();
            controller.configure(productService, inventoryService, customerService,
                    sellerService, salesWorkflow, currentSession,
                    authorizationService.hasPermission(currentSession, Permission.VIEW_SELLERS));
            rootPane.setCenter(view);
            selectNavigation(btnSales);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el registro de ventas.", failure);
            navigationMessage.setText("No se pudo abrir el registro de ventas.");
        }
    }

    @FXML
    private void handleShowInventory() {
        if (!authorizationService.hasPermission(currentSession, Permission.VIEW_INVENTORY)) {
            handleShowDashboard();
            navigationMessage.setText("No tiene permiso para consultar el inventario.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/inventory_view.fxml"));
            Node view = loader.load();
            InventoryController controller = loader.getController();
            controller.configure(productService, inventoryService,
                    authorizationService.hasPermission(currentSession, Permission.MANAGE_PRODUCTS),
                    authorizationService.hasPermission(currentSession, Permission.MANAGE_INVENTORY_ENTRIES));
            rootPane.setCenter(view);
            selectNavigation(btnInventory);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el inventario.", failure);
            navigationMessage.setText("No se pudo consultar el inventario.");
        }
    }

    @FXML
    private void handleShowDashboard() {
        if (currentSession == null || !currentSession.isActive()) {
            navigationMessage.setText("La sesión ya no está activa.");
            return;
        }
        rootPane.setCenter(dashboard);
        selectNavigation(btnDashboard);
        navigationMessage.setText("");
    }

    public void initSession(UserSession session) {
        if (session == null || !session.isActive()) {
            throw new IllegalArgumentException("Se requiere una sesión activa.");
        }
        this.currentSession = session;
        
        String username = session.getUserAccount().getUsername();
        String role = session.getUserAccount().getRole().name();

        welcomeLabel.setText("¡Bienvenido, " + username + "!");
        roleLabel.setText("Rol actual: " + role);

        boolean hasPermission = authorizationService.hasPermission(session, Permission.MANAGE_USERS);
        btnAdminUsers.setVisible(hasPermission);
        btnAdminUsers.setManaged(hasPermission);

        boolean canViewSellers = authorizationService.hasPermission(session, Permission.VIEW_SELLERS);
        btnSellers.setVisible(canViewSellers);
        btnSellers.setManaged(canViewSellers);
        boolean canViewInventory = authorizationService.hasPermission(session, Permission.VIEW_INVENTORY);
        btnInventory.setVisible(canViewInventory);
        btnInventory.setManaged(canViewInventory);
        boolean canRegisterSales = authorizationService.hasPermission(session, Permission.REGISTER_SALE)
                && authorizationService.hasPermission(session, Permission.VIEW_SALES);
        btnSales.setVisible(canRegisterSales);
        btnSales.setManaged(canRegisterSales);
        btnAdminUsers.setDisable(true);
        selectNavigation(btnDashboard);
    }
}
