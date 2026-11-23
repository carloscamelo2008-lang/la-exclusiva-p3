package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.repository.InMemorySupplierRepository;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.testutil.IdentityTestFixture;
import com.laexclusiva.ui.controller.CustomerController;
import com.laexclusiva.ui.controller.CashController;
import com.laexclusiva.ui.controller.InventoryController;
import com.laexclusiva.ui.controller.MainController;
import com.laexclusiva.ui.controller.PortfolioController;
import com.laexclusiva.ui.controller.SalesController;
import com.laexclusiva.ui.controller.SellerController;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Regresiones que separan el autorizador legacy de las fronteras productivas. */
class AuthorizationCompatibilityBoundaryTest {
    @Test
    void compatibilityAuthorizationCannotRunProtectedOperationsEvenForAnActiveSnapshot() {
        AuthorizationService compatibility = new AuthorizationService();
        UserSession synthetic = new UserSession(new UserAccount(7L, "legacy", "hash",
                UserRole.ADMINISTRATOR, null));
        AtomicInteger calls = new AtomicInteger();

        assertTrue(compatibility.hasPermission(synthetic, Permission.MANAGE_USERS),
                "Se conserva la consulta de permisos para consumidores legacy.");
        assertThrows(SecurityException.class,
                () -> compatibility.execute(synthetic, Permission.MANAGE_USERS, calls::incrementAndGet));
        assertEquals(0, calls.get(), "La operación no debe iniciar bajo el autorizador de compatibilidad.");
    }

    @Test
    void productiveJavaFxControllersRejectCompatibilityAuthorization() {
        AuthorizationService compatibility = new AuthorizationService();
        assertThrows(SecurityException.class, () -> new MainController().setAuthorizationService(compatibility));
        assertThrows(SecurityException.class, () -> new CustomerController().setAuthorizationService(compatibility));
        assertThrows(SecurityException.class, () -> new SellerController().setAuthorizationService(compatibility));
        assertThrows(SecurityException.class, () -> new InventoryController().setAuthorizationService(compatibility));
        assertThrows(SecurityException.class, () -> new SalesController().setAuthorizationService(compatibility));
        assertThrows(SecurityException.class, () -> new PortfolioController().setAuthorizationService(compatibility));
        assertThrows(SecurityException.class, () -> new CashController().setAuthorizationService(compatibility));
    }

    @Test
    void directPurchaseServicesAndLegacyFactoriesFailClosedWithoutSessionAuthority() {
        AuthorizationService compatibility = new AuthorizationService();
        UserSession synthetic = new UserSession(new UserAccount(8L, "legacy", "hash",
                UserRole.ADMINISTRATOR, null));
        InMemorySupplierRepository suppliers = new InMemorySupplierRepository();
        TransactionBoundary direct = new TransactionBoundary() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> operation) {
                return operation.get();
            }
        };
        SupplierService service = new SupplierService(suppliers, compatibility, direct);

        assertThrows(SecurityException.class, () -> service.create(synthetic, "Proveedor", null, null));
        assertTrue(suppliers.findAll().isEmpty());
        assertThrows(SecurityException.class, () -> PurchasesModuleFactory.inMemory(null, null, null));
        InMemoryUserRepository users = new InMemoryUserRepository();
        UserManagementService userManagement = new UserManagementService(users,
                new InMemoryUserAdministrationBoundary(users), compatibility);
        assertThrows(SecurityException.class, () -> userManagement.listUsers(synthetic));
        assertTrue(users.findAll().isEmpty());
    }

    @Test
    void strictAuthorizationAcceptsOnlyItsOwnCurrentSession() {
        IdentityTestFixture identity = new IdentityTestFixture();
        var issued = identity.issue(UserRole.ADMINISTRATOR);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(1, identity.authorization().execute(issued.session(), Permission.MANAGE_USERS,
                calls::incrementAndGet));
        issued.session().invalidate();
        assertThrows(SecurityException.class, () -> identity.authorization().execute(issued.session(),
                Permission.MANAGE_USERS, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }
}
