package com.laexclusiva.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.service.GoodsReceiptService;
import com.laexclusiva.service.PurchaseOrderService;
import java.util.Map;
import java.math.BigDecimal;
import com.laexclusiva.model.CustomerDeletionBlockedException;
import org.junit.jupiter.api.Test;

class ApplicationServicesTest {

    @Test
    void keepsExistingMemoryCompositionAsAnExplicitSupportedMode() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());

        assertFalse(services.postgresMode());
        assertFalse(services.sales().supportsDurableIdempotency());
        assertNull(services.debtQuery());
        assertEquals(6, services.products().listAll().size());
        assertNotNull(services.purchases());
        assertNotNull(services.collectionAssistant());
        assertNotNull(services.administrativeAudit());
    }

    @Test
    void blocksDeletionWhenGeneralDebtWasFullyPaidButHistoryRemains() {
        var services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        String id = "paid-general-debt";
        var amount = new BigDecimal("100.00");
        services.customers().registerCustomer(id, "Cliente ficticio", "3100000123", "Calle");
        services.generalDebt().registerDebt(id, amount, "General", "Cargo", "debt");
        services.generalDebt().registerPayment(id, amount, "General", "Abono completo", "payment");

        assertEquals(0, services.customers().getCustomerById(id).getBalance().signum());
        assertEquals(2, services.generalDebt().listMovements(id, null, null, null).size());
        assertThrows(CustomerDeletionBlockedException.class, () -> services.customers().deleteCustomer(id));
        assertEquals(id, services.customers().getCustomerById(id).getId());
    }

    @Test
    void rejectsUnknownPersistenceModesBeforeOpeningAnyConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationServices.create("mysql", Map.of()));
    }

    @Test
    void refusesRealSmtpFromNonDurableMemoryMode() {
        assertThrows(IllegalArgumentException.class, () -> ApplicationServices.create(
                ApplicationServices.MEMORY_MODE, Map.of("LAEXCLUSIVA_SMTP_ENABLED", "true")));
    }

    @Test
    void memoryPurchasesShareIdentityInventoryCatalogAndMovementHistory() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        String password = "Purchases-Composition-Only-2026";
        assertTrue(services.identity().bootstrapInitialAdministrator("purchases-admin", password));
        var admin = services.identity().authentication().createSession("purchases-admin", password).orElseThrow();
        var actorId = services.authorization().currentPrincipal(admin).userId();
        var supplier = services.purchases().suppliers().create(admin, "Proveedor de integración", null, null);
        var product = services.products().listAll().get(0);
        var order = services.purchases().orders().create(admin, supplier.id(), null,
                java.util.List.of(new PurchaseOrderService.OrderLineRequest(product.getId(), 1, 0)));
        assertEquals(actorId, order.order().createdByUserId());
        assertEquals(0, services.inventory().availableUnits(product.getId()));

        var line = order.lines().get(0);
        var receipt = services.purchases().receipts().receive(admin, order.order().id(), "composition-receipt",
                null, java.util.List.of(new GoodsReceiptService.ReceiptLineRequest(line.id(), 1, 0)));

        assertEquals(actorId, receipt.receivedByUserId());
        assertEquals(product.getUnitsPerBasket(), services.inventory().availableUnits(product.getId()));
        assertEquals(1, services.inventory().movementsOf(product.getId()).size());
        assertEquals(com.laexclusiva.model.MovementType.ENTRY,
                services.inventory().movementsOf(product.getId()).get(0).type());
        assertEquals(com.laexclusiva.model.PurchaseOrderStatus.COMPLETED,
                services.purchases().orders().findById(admin, order.order().id()).orElseThrow().order().status());
    }
}
