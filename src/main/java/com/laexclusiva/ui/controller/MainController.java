package com.laexclusiva.ui.controller;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.InventoryService;
import com.laexclusiva.service.ProductService;
import com.laexclusiva.service.SellerService;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.service.CashWorkflow;
import com.laexclusiva.service.CustomerGeneralDebtWorkflow;
import com.laexclusiva.ui.ApplicationServices;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    @FXML private BorderPane rootPane;
    @FXML private Node dashboard;
    @FXML private CollectionDashboardView collectionDashboard;
    @FXML private Label navigationMessage;
    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Button btnSellers;
    @FXML private Button btnInventory;
    @FXML private Button btnPurchases;
    @FXML private Button btnSales;
    @FXML private Button btnAdminUsers;
    @FXML private Button btnDashboard;
    @FXML private Button btnCustomers;
    @FXML private Button btnCredit;
    @FXML private Button btnCollections;
    @FXML private Button btnCash;
    @FXML private Button btnLogout;

    private UserSession currentSession;
    private CustomerService customerService;
    private SellerService sellerService;
    private ProductService productService;
    private InventoryService inventoryService;
    private SalesWorkflow salesWorkflow;
    private CashWorkflow cashWorkflow;
    private CustomerGeneralDebtWorkflow generalDebtWorkflow;
    private com.laexclusiva.service.PurchasesModuleFactory.Services purchases;
    private Runnable onLogout;
    private AuthorizationService authorizationService;
    private ApplicationServices applicationServices;
    private boolean sessionClosed;

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
        services.authorization().requireSessionAuthority();
        this.applicationServices = services;
        this.authorizationService = services.authorization();
        this.customerService = services.customers();
        this.sellerService = services.sellers();
        this.productService = services.products();
        this.inventoryService = services.inventory();
        this.salesWorkflow = services.sales();
        this.cashWorkflow = services.cash();
        this.generalDebtWorkflow = services.generalDebt();
        this.purchases = services.purchases();
    }

    /** Inyecta una autoridad validada para consumidores que configuran servicios por separado. */
    public void setAuthorizationService(AuthorizationService authorizationService) {
        if (authorizationService == null) throw new IllegalArgumentException("La autorización es obligatoria.");
        authorizationService.requireSessionAuthority();
        this.authorizationService = authorizationService;
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    @FXML
    private void handleLogout() {
        UserSession closingSession = currentSession;
        currentSession = null;
        sessionClosed = true;
        disableProtectedNavigation();
        if (collectionDashboard != null) collectionDashboard.clear();
        showSessionClosedState("La sesión está cerrada.");
        if (closingSession != null) {
            try {
                if (applicationServices != null) {
                    applicationServices.identity().authentication().revoke(closingSession);
                } else {
                    closingSession.invalidate();
                }
            } catch (RuntimeException failure) {
                // Aunque falle la autoridad durante el cierre, esta vista queda bloqueada y la sesión local se invalida.
                closingSession.invalidate();
                LOGGER.log(Level.WARNING, "No se pudo completar la revocación de la sesión.", failure);
            }
        }
        attemptReturnToLogin();
    }

    private void attemptReturnToLogin() {
        try {
            if (onLogout == null) throw new IllegalStateException("El regreso al inicio de sesión no está configurado.");
            onLogout.run();
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "La sesión quedó cerrada, pero no se pudo abrir el inicio de sesión.", failure);
            showSessionClosedRecovery();
        }
    }

    private void disableProtectedNavigation() {
        for (Button button : new Button[]{btnDashboard, btnCustomers, btnSellers, btnCredit,
                btnCollections, btnInventory, btnPurchases, btnSales, btnCash, btnAdminUsers, btnLogout}) {
            if (button != null) button.setDisable(true);
        }
    }

    private void showSessionClosedRecovery() {
        showSessionClosedState("No se pudo abrir el inicio de sesión. La sesión está cerrada; puede reintentar.");
    }

    private void showSessionClosedState(String status) {
        if (rootPane == null) return;
        Label closedMessage = new Label("La sesión está cerrada. Inicie sesión nuevamente para continuar.");
        closedMessage.setId("sessionClosedMessage");
        Button retry = new Button("Volver al inicio de sesión");
        retry.setId("retryLoginButton");
        retry.setOnAction(event -> attemptReturnToLogin());
        VBox recovery = new VBox(14, closedMessage, retry);
        recovery.setAlignment(Pos.CENTER);
        recovery.setPadding(new Insets(24));
        recovery.setId("sessionClosedRecovery");
        rootPane.setCenter(recovery);
        navigationMessage.setText(status);
    }

    private void selectNavigation(Button selected) {
        for (Button button : new Button[]{btnDashboard, btnCustomers, btnSellers, btnCredit,
                btnCollections, btnInventory, btnPurchases, btnSales, btnCash, btnAdminUsers}) {
            button.pseudoClassStateChanged(ACTIVE, button == selected);
        }
    }

    @FXML
    private void handleShowCustomers() {
        if (!hasPermission(Permission.VIEW_CUSTOMER)) {
            navigationMessage.setText("No tiene permiso para consultar clientes.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/customers_view.fxml"));
            Node customerView = loader.load();
            CustomerController controller = loader.getController();
            controller.setAuthorizationService(authorizationService);
            controller.configure(customerService, currentSession);
            controller.showCustomers(authorizationService.execute(currentSession, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers));
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
        if (!hasPermission(Permission.VIEW_SELLERS)) {
            navigationMessage.setText("No tiene permiso para consultar vendedores.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sellers_view.fxml"));
            Node sellerView = loader.load();
            SellerController controller = loader.getController();
            controller.setAuthorizationService(authorizationService);
            controller.configure(sellerService, currentSession);
            controller.showSellers(authorizationService.execute(currentSession, Permission.VIEW_SELLERS,
                    sellerService::getAllSellers));
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
        if (!hasPermission(Permission.VIEW_PORTFOLIO)) {
            navigationMessage.setText("No tiene permiso para consultar la cartera.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/portfolio_view.fxml"));
            Node portfolioView = loader.load();
            PortfolioController controller = loader.getController();
            controller.setAuthorizationService(authorizationService);
            controller.configure(customerService, salesWorkflow, generalDebtWorkflow, currentSession);
            controller.showPortfolio(authorizationService.execute(currentSession, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers));
            rootPane.setCenter(portfolioView);
            selectNavigation(btnCredit);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir la consulta de cartera.", failure);
            navigationMessage.setText("No se pudo consultar la cartera.");
        }
    }

    /** Abre el tablero de cartera; la edición de preferencias queda reservada al administrador. */
    @FXML
    private void handleShowCollections() {
        if (applicationServices == null || !hasPermission(Permission.VIEW_PORTFOLIO)) {
            navigationMessage.setText("No tiene permiso para consultar las alertas de cartera.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/collections_view.fxml"));
            Node view = loader.load();
            CollectionsController controller = loader.getController();
            var visibleCustomers = authorizationService.execute(currentSession, Permission.VIEW_CUSTOMER,
                    customerService::getAllCustomers);
            controller.configure(applicationServices.collectionAssistant(),
                    applicationServices.administrativeAudit(), visibleCustomers, currentSession,
                    authorizationService);
            rootPane.setCenter(view);
            selectNavigation(btnCollections);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir Alertas y recordatorios.", failure);
            navigationMessage.setText("No se pudo abrir Alertas y recordatorios.");
        }
    }

    @FXML
    private void handleShowSales() {
        if (!hasPermission(Permission.REGISTER_SALE) || !hasPermission(Permission.VIEW_SALES)) {
            handleShowDashboard();
            navigationMessage.setText("No tiene permiso para registrar ventas.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/sales_view.fxml"));
            Node view = loader.load();
            SalesController controller = loader.getController();
            controller.setAuthorizationService(authorizationService);
            controller.configure(productService, inventoryService, customerService,
                    sellerService, salesWorkflow, currentSession);
            rootPane.setCenter(view);
            selectNavigation(btnSales);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el registro de ventas.", failure);
            navigationMessage.setText("No se pudo abrir el registro de ventas.");
        }
    }

    @FXML
    private void handleShowCash() {
        if (!hasPermission(Permission.VIEW_CASH)) {
            navigationMessage.setText("No tiene permiso para consultar caja.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/cash_view.fxml"));
            Node view = loader.load();
            CashController controller = loader.getController();
            controller.setAuthorizationService(authorizationService);
            controller.configure(cashWorkflow, currentSession);
            rootPane.setCenter(view);
            selectNavigation(btnCash);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el módulo de caja.", failure);
            navigationMessage.setText("No se pudo abrir el módulo de caja.");
        }
    }

    @FXML
    private void handleShowInventory() {
        if (!hasPermission(Permission.VIEW_INVENTORY)) {
            handleShowDashboard();
            navigationMessage.setText("No tiene permiso para consultar el inventario.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/inventory_view.fxml"));
            Node view = loader.load();
            InventoryController controller = loader.getController();
            controller.setAuthorizationService(authorizationService);
            controller.configure(productService, inventoryService, currentSession);
            rootPane.setCenter(view);
            selectNavigation(btnInventory);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir el inventario.", failure);
            navigationMessage.setText("No se pudo consultar el inventario.");
        }
    }

    /** Abre Compras; cada consulta y escritura vuelve a validar el permiso en sus servicios. */
    @FXML
    private void handleShowPurchases() {
        if (purchases == null || !hasPermission(Permission.VIEW_INVENTORY)) {
            navigationMessage.setText("No tiene permiso para consultar compras.");
            return;
        }
        try {
            Node view = PurchasesController.load(purchases.suppliers(), purchases.orders(), purchases.receipts(),
                    productService, currentSession, authorizationService);
            rootPane.setCenter(view);
            selectNavigation(btnPurchases);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir Compras y recepción.", failure);
            navigationMessage.setText("No se pudo abrir Compras y recepción.");
        }
    }

    @FXML
    private void handleShowDashboard() {
        try {
            requireSessionAuthority();
            if (sessionClosed) throw new SecurityException("La sesión está cerrada.");
            authorizationService.currentPrincipal(currentSession);
        } catch (RuntimeException invalidSession) {
            if (collectionDashboard != null) collectionDashboard.clear();
            navigationMessage.setText("La sesión ya no está activa.");
            return;
        }
        refreshCollectionDashboard();
        rootPane.setCenter(dashboard);
        selectNavigation(btnDashboard);
        navigationMessage.setText("");
    }

    /** La vista consulta indicadores protegidos actuales; nunca completa cantidades por su cuenta. */
    private void refreshCollectionDashboard() {
        if (collectionDashboard != null) {
            collectionDashboard.refresh(applicationServices, currentSession, this::handleShowCollections);
        }
    }
    public void initSession(UserSession session) {
        requireSessionAuthority();
        if (sessionClosed) throw new SecurityException("No se puede reactivar una vista cuya sesión fue cerrada.");
        var principal = authorizationService.currentPrincipal(session);
        this.currentSession = session;

        String username = applicationServices == null
                ? session.getUserAccount().getUsername()
                : applicationServices.identity().users().findById(principal.userId().toString())
                    .orElseThrow(() -> new SecurityException("La cuenta ya no está disponible.")).getUsername();
        String role = principal.role().name();

        welcomeLabel.setText("¡Bienvenido, " + username + "!");
        roleLabel.setText("Rol actual: " + role);

        boolean hasPermission = hasPermission(Permission.MANAGE_USERS);
        btnAdminUsers.setVisible(hasPermission);
        btnAdminUsers.setManaged(hasPermission);

        boolean canViewSellers = hasPermission(Permission.VIEW_SELLERS);
        btnSellers.setVisible(canViewSellers);
        btnSellers.setManaged(canViewSellers);
        boolean canViewInventory = hasPermission(Permission.VIEW_INVENTORY);
        btnInventory.setVisible(canViewInventory);
        btnInventory.setManaged(canViewInventory);
        btnPurchases.setVisible(canViewInventory);
        btnPurchases.setManaged(canViewInventory);
        boolean canRegisterSales = hasPermission(Permission.REGISTER_SALE)
                && hasPermission(Permission.VIEW_SALES);
        btnSales.setVisible(canRegisterSales);
        btnSales.setManaged(canRegisterSales);
        boolean canViewCash = hasPermission(Permission.VIEW_CASH);
        btnCash.setVisible(canViewCash);
        btnCash.setManaged(canViewCash);
        boolean canViewPortfolio = hasPermission(Permission.VIEW_PORTFOLIO);
        btnCollections.setVisible(canViewPortfolio);
        btnCollections.setManaged(canViewPortfolio);
        btnAdminUsers.setDisable(false);
        refreshCollectionDashboard();
        selectNavigation(btnDashboard);
    }

    /** Abre la administración completa; las mutaciones vuelven a validarse en el servicio. */
    @FXML
    private void handleShowAdminUsers() {
        if (applicationServices == null || !hasPermission(Permission.MANAGE_USERS)) {
            navigationMessage.setText("No tiene permiso para administrar usuarios.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/laexclusiva/ui/fxml/user_management_view.fxml"));
            Node view = loader.load();
            UserManagementController controller = loader.getController();
            controller.configure(applicationServices.identity().userManagement(), sellerService,
                    currentSession, authorizationService);
            rootPane.setCenter(view);
            selectNavigation(btnAdminUsers);
            navigationMessage.setText("");
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "No se pudo abrir la administración de usuarios.", failure);
            navigationMessage.setText("No se pudo abrir la administración de usuarios.");
        }
    }

    private boolean hasPermission(Permission permission) {
        return !sessionClosed && authorizationService != null && authorizationService.hasSessionAuthority()
                && currentSession != null && authorizationService.hasPermission(currentSession, permission);
    }

    private void requireSessionAuthority() {
        if (authorizationService == null) throw new SecurityException("No se configuró la autoridad de sesión.");
        authorizationService.requireSessionAuthority();
    }
}
