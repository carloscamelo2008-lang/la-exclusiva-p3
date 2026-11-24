package com.laexclusiva.demo;

import com.laexclusiva.model.*;
import com.laexclusiva.service.GoodsReceiptService.ReceiptLineRequest;
import com.laexclusiva.service.UserManagementService.CreateUserRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteractiveDemoDataTest {
    private static final String ADMIN = "Fixture-" + java.util.UUID.randomUUID();
    private static final String WORKER = "Fixture-" + java.util.UUID.randomUUID();
    private InteractiveDemoData.LoadedDemo load() throws Exception {
        return InteractiveDemoData.create(InteractiveDemoData.configuration(null), ADMIN, WORKER);
    }

    @Test void completeDatasetHasRealRelationsAndAllPurchaseStates() throws Exception {
        var loaded = load(); var s = loaded.services(); var report = loaded.report();
        assertFalse(s.postgresMode()); assertEquals(15, report.customers()); assertEquals(5, report.sellers());
        assertEquals(14, report.products()); assertEquals(5, report.suppliers()); assertEquals(10, report.orders());
        assertEquals(30, report.sales()); assertEquals(5, report.users());
        var admin = s.identity().authentication().createSession("admin", ADMIN).orElseThrow();
        assertEquals(4, s.purchases().orders().findByStatus(admin, PurchaseOrderStatus.COMPLETED).size());
        assertEquals(3, s.purchases().orders().findByStatus(admin, PurchaseOrderStatus.PARTIAL).size());
        assertEquals(2, s.purchases().orders().findByStatus(admin, PurchaseOrderStatus.PENDING).size());
        assertEquals(1, s.purchases().orders().findByStatus(admin, PurchaseOrderStatus.CANCELLED).size());
        assertEquals(0, s.inventory().availableUnits("BUDWEISER_330"));
        assertEquals(0, s.inventory().availableUnits("COLOMBIANA_400"));
        assertEquals(3, s.inventory().availableUnits("ANDINA_330"));
        assertTrue(s.inventory().availableUnits("COSTENITA_175") > 300);
        assertTrue(s.sales().listAll().stream().anyMatch(sale -> sale.getLines().size() > 1));
        assertTrue(s.sales().listAll().stream().anyMatch(sale -> sale.calculateBalance().signum() > 0));
        assertTrue(s.sales().listAll().stream().anyMatch(sale -> sale.calculateBalance().signum() == 0));
        assertTrue(s.sales().listAll().stream().anyMatch(sale -> sale.getPayments().size() > 1));
        assertTrue(s.sales().listAll().stream().allMatch(sale -> sale.getCreatedAt().toLocalDate().equals(LocalDate.now())));
        assertTrue(s.customers().getAllCustomers().stream().allMatch(customer -> customer.getPhone().matches("3[0-9]{9}")));
        assertEquals(0, s.customers().getCustomerById("CLI-015").getBalance().signum());
        InteractiveDemoData.verify(s);
    }

    @Test void paymentsAndCashAreReconciledAndManualDatesCanBeFiltered() throws Exception {
        var s = load().services();
        var salesPayments = s.sales().listAll().stream().flatMap(sale -> sale.getPayments().stream())
                .map(SalePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, salesPayments.compareTo(s.cash().summarize(null, null).saleReceipts()));
        var manual = s.cash().listMovements(null, null, null).stream().filter(m -> m.sourceType().equals("MANUAL")).toList();
        assertEquals(12, manual.size());
        assertEquals(LocalDate.now().minusDays(29), manual.get(0).occurredAt().toLocalDate());
        assertEquals(LocalDate.now(), manual.get(manual.size() - 1).occurredAt().toLocalDate());
        assertTrue(s.cash().listMovements(LocalDate.now().minusDays(29), LocalDate.now().minusDays(20), CashCategory.OTHER_INCOME).size() > 0);
        assertTrue(s.generalDebt().findBalance("CLI-012").generalDebt().signum() > 0);
        assertEquals(0, s.generalDebt().findBalance("CLI-011").generalDebt().signum());
        InteractiveDemoData.verify(s);
    }

    @Test void datasetCanReceiveSellPayAndCreateAccountsThroughExistingServices() throws Exception {
        var s = load().services(); var admin = s.identity().authentication().createSession("admin", ADMIN).orElseThrow();
        var pending = s.purchases().orders().findByStatus(admin, PurchaseOrderStatus.PENDING).get(0);
        var line = s.purchases().orders().findById(admin, pending.id()).orElseThrow().lines().get(0);
        int before = s.inventory().availableUnits(line.productId());
        s.purchases().receipts().receive(admin, pending.id(), "manual-demo-test-receipt", "Prueba posterior a la carga",
                List.of(new ReceiptLineRequest(line.id(), 0, 1)));
        assertEquals(before + 1, s.inventory().availableUnits(line.productId()));
        var worker = s.identity().authentication().createSession("elena.torres", WORKER).orElseThrow();
        var principal = s.identity().sessions().validate(worker);
        assertNotNull(principal.sellerId());
        var sale = s.sales().registerSale(List.of(new StockRequest(line.productId(), SalePresentation.UNIT, 1)),
                principal.sellerId(), "CLI-015", BigDecimal.ZERO, "manual-demo-test-sale");
        s.sales().registerPayment(sale.getId(), sale.calculateBalance(), "manual-demo-test-payment");
        assertEquals(0, s.sales().findById(sale.getId()).calculateBalance().signum());
        s.identity().userManagement().createUser(admin, new CreateUserRequest("worker.nuevo", WORKER, UserRole.WORKER, true));
        assertTrue(s.identity().authentication().createSession("worker.nuevo", WORKER).isPresent());
        InteractiveDemoData.verify(s);
    }

    @Test void realSecurityAndDisabledAccountsArePreserved() throws Exception {
        var s = load().services();
        var admin = s.identity().authentication().createSession("admin", ADMIN).orElseThrow();
        var worker = s.identity().authentication().createSession("elena.torres", WORKER).orElseThrow();
        assertTrue(s.identity().authentication().createSession("paula.serrano", WORKER).isEmpty());
        var noSeller = s.identity().authentication().createSession("operador", WORKER).orElseThrow();
        assertNull(s.identity().sessions().validate(noSeller).sellerId());
        assertFalse(s.authorization().hasPermission(worker, Permission.MANAGE_USERS));
        assertThrows(SecurityException.class, () -> s.purchases().suppliers().create(worker, "No permitido", "NO", "Ficticio"));
        var account = s.identity().users().findByUsername("elena.torres").orElseThrow();
        String replacement = "Fixture-" + java.util.UUID.randomUUID();
        s.identity().userManagement().changePassword(admin, account.getId().toString(), account.getSecurityVersion(), replacement);
        assertThrows(SecurityException.class, () -> s.identity().sessions().validate(worker));
        assertTrue(s.identity().authentication().createSession("elena.torres", replacement).isPresent());
        var administrator = s.identity().users().findByUsername("admin").orElseThrow();
        assertThrows(IllegalStateException.class, () -> s.identity().userManagement().setActive(admin,
                administrator.getId().toString(), administrator.getSecurityVersion(), false));
    }

    @Test void reloadStartsFreshWithoutDuplicatingOrSharingRepositories() throws Exception {
        var first = load(); var second = load();
        assertEquals(first.report(), second.report()); assertNotSame(first.services(), second.services());
        assertNotEquals(first.services().identity().users().findByUsername("admin").orElseThrow().getAccountInstanceId(),
                second.services().identity().users().findByUsername("admin").orElseThrow().getAccountInstanceId());
        first.services().customers().registerCustomer("DEMO-ADDED", "Persona Ficticia", "3000009999", "Dirección ficticia");
        assertEquals(16, first.services().customers().getAllCustomers().size());
        assertEquals(15, second.services().customers().getAllCustomers().size());
    }

    @Test void invalidConfigurationOrPasswordIsRejectedWithoutPublishingAPartialDemo() throws Exception {
        var config = InteractiveDemoData.configuration(null);
        config.setProperty("customer.1", "Persona Ficticia|no-es-telefono|Dirección ficticia");
        assertThrows(IllegalArgumentException.class, () -> InteractiveDemoData.create(config, ADMIN, WORKER));
        assertThrows(IllegalArgumentException.class,
                () -> InteractiveDemoData.create(InteractiveDemoData.configuration(null), "corta", WORKER));
        assertEquals(15, load().report().customers());
    }

    @Test void commercialRecordsHaveNaturalLabelsAndAllLinksRemainValid() throws Exception {
        var s = load().services();
        var admin = s.identity().authentication().createSession("admin", ADMIN).orElseThrow();
        var text = new java.util.ArrayList<String>();
        s.customers().getAllCustomers().forEach(c -> text.addAll(List.of(c.getId(), c.getName(), c.getAddress())));
        s.sellers().getAllSellers().forEach(seller -> text.addAll(List.of(seller.getName(), seller.getAddress())));
        s.products().listAll().forEach(p -> text.addAll(List.of(p.getId(), p.getName())));
        s.purchases().suppliers().findAll(admin).forEach(p -> text.addAll(List.of(p.name(), p.contactInfo())));
        s.purchases().orders().findAll(admin).forEach(order -> {
            text.add(order.notes());
            s.purchases().receipts().findByPurchaseOrderId(admin, order.id()).forEach(receipt -> {
                text.add(receipt.notes()); text.add(receipt.idempotencyKey());
            });
        });
        s.inventory().allMovements().forEach(m -> text.add(m.reason()));
        s.generalDebt().listMovements(null, null, null, null).forEach(m -> text.addAll(List.of(m.category(), m.description(), m.idempotencyKey())));
        s.cash().listMovements(null, null, null).forEach(m -> text.addAll(List.of(m.description(), m.idempotencyKey())));
        s.identity().users().findAll().forEach(u -> text.add(u.getUsername()));
        for (String value : text) assertFalse(value.matches("(?isu).*(demo|fictici|simulad|prueba).*"), value);
        for (Sale sale : s.sales().listAll()) {
            assertNotNull(s.customers().getCustomerById(sale.getCustomer().getId()));
            assertTrue(s.sellers().getSellerById(sale.getSeller().getId()).isPresent());
            sale.getLines().forEach(line -> assertNotNull(s.products().findById(line.getProduct().getId())));
        }
        assertEquals("Calle 10 # 20-01", s.customers().getCustomerById("CLI-001").getAddress());
        assertEquals("Carrera 24 # 90-08", s.customers().getCustomerById("CLI-008").getAddress());
        InteractiveDemoData.verify(s);
    }
}
