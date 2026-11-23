package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.AdministrativeAuditEvent;
import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.CollectionChannel;
import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SaleLine;
import com.laexclusiva.model.SalePayment;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.AdministrativeAuditRepository;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.InMemoryAdministrativeAuditRepository;
import com.laexclusiva.repository.InMemoryCustomerCollectionRepository;
import com.laexclusiva.testutil.IdentityTestFixture;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Prueba reglas de prioridad, autorización, documentos y envío sin conexiones de red. */
class CollectionAssistantServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test
    void ranksCommitmentsAndVerifiedAgesAndLabelsMissingHistory() {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        fixture.customer("overdue", "500.00", List.of());
        fixture.customer("aged-sale", "100.00", List.of(sale("sale-aged", "aged-sale", "100.00", TODAY.minusDays(40), List.of())));
        fixture.customer("upcoming", "100.00", List.of(sale("sale-upcoming", "upcoming", "100.00", TODAY.minusDays(5), List.of())));
        fixture.customer("general-old", "80.00", List.of());
        fixture.customer("unknown-age", "75.00", List.of());
        fixture.customer("settled", "0.00", List.of());
        fixture.generalMovements.add(debt("overdue", "500.00", TODAY.minusDays(8)));
        fixture.generalMovements.add(debt("general-old", "80.00", TODAY.minusDays(35)));
        fixture.setProfile("overdue", TODAY.minusDays(1));
        fixture.setProfile("aged-sale", null);
        fixture.setProfile("upcoming", TODAY.plusDays(3));
        fixture.setProfile("general-old", null);
        fixture.setProfile("unknown-age", null);

        var dashboard = fixture.service.dashboard(fixture.admin.session());

        assertEquals(1, dashboard.overdueCommitments());
        assertEquals(2, dashboard.agedDebts());
        assertEquals(1, dashboard.upcomingCommitments());
        assertEquals(5, dashboard.recommendations().size(), "La cuenta saldada no debe generar alerta.");
        assertEquals("HIGH", priority(dashboard, "overdue"));
        assertEquals("MEDIUM", priority(dashboard, "aged-sale"));
        assertEquals("MEDIUM", priority(dashboard, "general-old"));
        assertEquals("UPCOMING", priority(dashboard, "upcoming"));
        var unknown = dashboard.recommendations().stream().filter(value -> value.customerId().equals("unknown-age"))
                .findFirst().orElseThrow();
        assertEquals("Antigüedad no determinada", unknown.debtAge());
        assertEquals("GENERAL", unknown.priority().name());
        assertTrue(unknown.explanation().contains("no determinada"));
    }

    @Test
    void preferenceHistoryIsAdministrativeAndRevocationRemovesEmailAndStopsReminders() {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        fixture.customer("consent", "120.00", List.of());

        var profile = fixture.service.saveProfile(fixture.admin.session(), "consent", "customer@example.test",
                CollectionChannel.EMAIL, true, true, TODAY.plusDays(2), "Llamar luego de las 10.");
        assertTrue(profile.collectionConsent());
        assertNotNull(profile.consentRecordedAt());
        assertEquals(3, fixture.service.history(fixture.admin.session(), "consent").size());
        assertThrows(SecurityException.class, () -> fixture.service.saveProfile(fixture.worker.session(), "consent",
                "customer@example.test", CollectionChannel.EMAIL, true, true, null, null));

        var revoked = fixture.service.saveProfile(fixture.admin.session(), "consent", "customer@example.test",
                CollectionChannel.EMAIL, false, true, null, "No autoriza más recordatorios.");
        assertFalse(revoked.collectionConsent());
        assertFalse(revoked.remindersEnabled());
        assertEquals(CollectionChannel.NONE, revoked.authorizedChannel());
        assertEquals(null, revoked.email());
        assertEquals(6, fixture.service.history(fixture.admin.session(), "consent").size());
        assertEquals(0, fixture.service.prepareWeeklyReminders(fixture.admin.session()).created());
    }

    @Test
    void reminderPreparationIsWeeklyIdempotentPdfIsCommercialAndDefaultDeliveryIsSimulated() throws Exception {
        FakeEmail email = new FakeEmail(false, false);
        Fixture fixture = new Fixture(email);
        fixture.customer("email-client", "250.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "email-client", "billing@example.test",
                CollectionChannel.EMAIL, true, true, null, null);

        var first = fixture.service.prepareWeeklyReminders(fixture.admin.session());
        var repeated = fixture.service.prepareWeeklyReminders(fixture.admin.session());

        assertEquals(1, first.created());
        assertEquals(0, repeated.created());
        assertEquals(1, first.pendingReview().size());
        var reminder = first.pendingReview().get(0);
        var preview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        try (var document = Loader.loadPDF(preview.pdf())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Estado de cuenta"));
            assertTrue(text.contains("Deuda general"));
            assertTrue(text.contains("no es factura electronica DIAN"));
        }
        assertTrue(preview.text().contains("no registra la deuda como pagada"));
        assertEquals(0, email.calls.get(), "La vista previa no debe enviar correo.");

        var outcome = fixture.service.sendReminder(fixture.admin.session(), reminder.id());
        assertEquals(CustomerCollectionReminder.ResultCode.SIMULATED, outcome.resultCode());
        assertEquals(CustomerCollectionReminder.Status.PENDING_REVIEW, outcome.status());
        assertFalse(outcome.providerAccepted());
        assertEquals(1, email.calls.get());
        assertEquals(1, fixture.audit.find(null, null, 100).size());
        assertEquals(1, fixture.service.dashboard(fixture.worker.session()).statementsPendingReview());
    }

    @Test
    void realDeliveryUsesAtomicClaimAndNeverRetriesAnAmbiguousProviderResult() {
        FakeEmail email = new FakeEmail(true, false);
        Fixture fixture = new Fixture(email, true, true, "configured-key");
        fixture.customer("send-once", "90.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "send-once", "once@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var reminder = fixture.service.prepareWeeklyReminders(fixture.admin.session()).pendingReview().get(0);

        var result = fixture.service.sendReminder(fixture.admin.session(), reminder.id());

        assertEquals(CustomerCollectionReminder.Status.SENT, result.status());
        assertTrue(result.providerAccepted());
        assertEquals(1, email.calls.get());
        assertThrows(IllegalStateException.class, () -> fixture.service.sendReminder(fixture.admin.session(), reminder.id()));
        assertEquals(1, email.calls.get());

        FakeEmail uncertainEmail = new FakeEmail(true, true);
        Fixture uncertain = new Fixture(uncertainEmail, true, true, "configured-key");
        uncertain.customer("uncertain", "45.00", List.of());
        uncertain.service.saveProfile(uncertain.admin.session(), "uncertain", "uncertain@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var uncertainReminder = uncertain.service.prepareWeeklyReminders(uncertain.admin.session()).pendingReview().get(0);
        var failed = uncertain.service.sendReminder(uncertain.admin.session(), uncertainReminder.id());
        assertEquals(CustomerCollectionReminder.Status.FAILED, failed.status());
        assertEquals(CustomerCollectionReminder.ResultCode.PROVIDER_FAILED, failed.resultCode());
        assertThrows(IllegalStateException.class,
                () -> uncertain.service.sendReminder(uncertain.admin.session(), uncertainReminder.id()));
        assertEquals(1, uncertainEmail.calls.get(), "Una respuesta SMTP incierta no habilita un segundo envío.");
    }

    @Test
    void blocksRealDeliveryWithoutPaymentConfirmationOrOutsideContactWindow() {
        FakeEmail email = new FakeEmail(true, false);
        Fixture unconfirmed = new Fixture(email, false, false, "configured-key");
        unconfirmed.customer("unconfirmed", "55.00", List.of());
        unconfirmed.service.saveProfile(unconfirmed.admin.session(), "unconfirmed", "safe@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var reminder = unconfirmed.service.prepareWeeklyReminders(unconfirmed.admin.session()).pendingReview().get(0);
        var blockedByKey = unconfirmed.service.sendReminder(unconfirmed.admin.session(), reminder.id());
        assertEquals(CustomerCollectionReminder.ResultCode.PAYMENT_INSTRUCTIONS_UNVERIFIED, blockedByKey.resultCode());
        assertEquals(0, email.calls.get());

        assertFalse(new ColombiaContactWindow(java.util.Set.of(), false)
                .allows(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, CollectionAssistantService.COLOMBIA)));
        var window = new ColombiaContactWindow(java.util.Set.of(LocalDate.of(2026, 11, 23)), true);
        assertFalse(window.allows(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, CollectionAssistantService.COLOMBIA)));
        assertFalse(new ColombiaContactWindow(java.util.Set.of(), true)
                .allows(ZonedDateTime.of(2026, 11, 22, 10, 0, 0, 0, CollectionAssistantService.COLOMBIA)));
        assertTrue(new ColombiaContactWindow(java.util.Set.of(), true)
                .allows(ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, CollectionAssistantService.COLOMBIA)));
    }

    @Test
    void statementSeparatesSalesAndGeneralDebtAndListsInitialPayment() throws Exception {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        Sale partial = sale("sale-statement", "statement", "100.00", TODAY.minusDays(2),
                List.of(new SalePayment(new BigDecimal("20.00"), TODAY.minusDays(2).atTime(10, 0))));
        fixture.customer("statement", "150.00", List.of(partial));

        byte[] pdf = fixture.service.statementPdf(fixture.worker.session(), "statement");

        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("VENTAS PENDIENTES"));
            assertTrue(text.contains("Pago inicial"));
            assertTrue(text.contains("Deuda de ventas"));
            assertTrue(text.contains("Deuda general (no asociada a ventas)"));
            assertTrue(text.contains("Saldo total actualizado"));
        }
    }

    @Test
    void recentPaymentIsRelativeToRecordedObservationAndNotFutureActivity() {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        fixture.customer("paid-after-observation", "80.00", List.of(sale("sale-recent", "paid-after-observation",
                "100.00", TODAY.minusDays(4), List.of(new SalePayment(new BigDecimal("20.00"), TODAY.atTime(9, 0))))));
        fixture.customer("future-payment", "80.00", List.of(sale("sale-future", "future-payment", "100.00",
                TODAY.minusDays(4), List.of(new SalePayment(new BigDecimal("20.00"), TODAY.atTime(11, 0))))));
        for (String id : List.of("paid-after-observation", "future-payment")) {
            fixture.collectionRepository.saveProfile(new com.laexclusiva.model.CollectionProfile(id, null,
                    CollectionChannel.NONE, false, null, false, null, new BigDecimal("100.00"),
                    TODAY.atTime(8, 0)), List.of());
        }

        var dashboard = fixture.service.dashboard(fixture.worker.session());

        assertTrue(dashboard.recommendations().stream().filter(value -> value.customerId().equals("paid-after-observation"))
                .findFirst().orElseThrow().recentPayment());
        assertFalse(dashboard.recommendations().stream().filter(value -> value.customerId().equals("future-payment"))
                .findFirst().orElseThrow().recentPayment());
    }

    @Test
    void auditReadsRequireCurrentAdministrativePermission() {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        assertThrows(SecurityException.class,
                () -> new AdministrativeAuditService(fixture.audit, fixture.identity.authorization())
                        .list(fixture.worker.session(), null, null, 10));
        assertTrue(new AdministrativeAuditService(fixture.audit, fixture.identity.authorization())
                .list(fixture.admin.session(), null, null, 10).isEmpty());
    }

    private static String priority(com.laexclusiva.model.CollectionDashboard dashboard, String id) {
        return dashboard.recommendations().stream().filter(value -> value.customerId().equals(id))
                .findFirst().orElseThrow().priority().name();
    }

    private static Sale sale(String id, String customerId, String total, LocalDate created, List<SalePayment> payments) {
        Customer customer = new Customer(customerId, "Cliente " + customerId, "3000000000", "Dirección ficticia",
                new BigDecimal(total));
        BeerProduct product = new BeerProduct("product-" + id, "Producto ficticio", 330, 24,
                com.laexclusiva.model.ContainerType.NON_RETURNABLE_CAN, null);
        SaleLine line = SaleLine.reconstitute(product, SalePresentation.UNIT, 1, new BigDecimal(total), 24);
        return Sale.reconstitute(id, created.atTime(10, 0), new Seller("seller-" + id,
                "Vendedor ficticio", "3000000001", "Dirección ficticia"), customer, List.of(line), payments);
    }

    private static GeneralDebtMovement debt(String id, String amount, LocalDate date) {
        return new GeneralDebtMovement(1, id, GeneralDebtMovementType.DEBT_INCREASE,
                new BigDecimal(amount), date.atTime(9, 0), "OTRA", "Cargo ficticio", "key-" + id);
    }

    private static final class Fixture {
        private final IdentityTestFixture identity = new IdentityTestFixture();
        private final IdentityTestFixture.IssuedUser admin = identity.issue(UserRole.ADMINISTRATOR);
        private final IdentityTestFixture.IssuedUser worker = identity.issue(UserRole.WORKER);
        private final CustomerRepository customerRepository = new CustomerRepository();
        private final CustomerService customers = new CustomerService(customerRepository);
        private final List<Sale> sales = new ArrayList<>();
        private final List<GeneralDebtMovement> generalMovements = new ArrayList<>();
        private final InMemoryCustomerCollectionRepository collectionRepository = new InMemoryCustomerCollectionRepository();
        private final InMemoryAdministrativeAuditRepository audit = new InMemoryAdministrativeAuditRepository();
        private final CollectionAssistantService service;

        private Fixture(FakeEmail email) { this(email, false, false, null); }

        private Fixture(FakeEmail email, boolean ownerConfirmed, boolean keyVerified, String paymentKey) {
            var salesWorkflow = new FakeSalesWorkflow(sales);
            var generalWorkflow = new FakeGeneralDebtWorkflow(generalMovements);
            Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(CollectionAssistantService.COLOMBIA).toInstant(),
                    CollectionAssistantService.COLOMBIA);
            service = new CollectionAssistantService(customers, salesWorkflow, generalWorkflow, collectionRepository,
                    audit, identity.authorization(), email, new AccountStatementPdfGenerator(),
                    new ColombiaContactWindow(java.util.Set.of(LocalDate.of(2026, 12, 25)), true), direct(), clock,
                    paymentKey, ownerConfirmed, keyVerified);
        }

        private void customer(String id, String balance, List<Sale> customerSales) {
            customerRepository.save(new Customer(id, "Cliente " + id, "3000000000", "Dirección ficticia",
                    new BigDecimal(balance)));
            sales.addAll(customerSales);
        }

        private void setProfile(String id, LocalDate commitment) {
            service.saveProfile(admin.session(), id, null, CollectionChannel.NONE, false, false, commitment, null);
        }
    }

    private static final class FakeSalesWorkflow implements SalesWorkflow {
        private final List<Sale> sales;
        private FakeSalesWorkflow(List<Sale> sales) { this.sales = sales; }
        @Override public Sale registerSale(List<com.laexclusiva.model.StockRequest> lines, String sellerId,
                String customerId, BigDecimal initialPayment) { throw new UnsupportedOperationException(); }
        @Override public Sale registerPayment(String saleId, BigDecimal amount, String key) { throw new UnsupportedOperationException(); }
        @Override public Sale findById(String saleId) { return sales.stream().filter(value -> value.getId().equals(saleId)).findFirst().orElseThrow(); }
        @Override public List<Sale> listAll() { return List.copyOf(sales); }
        @Override public BigDecimal outstandingSalesDebt(String customerId) {
            return sales.stream().filter(value -> value.getCustomer() != null && value.getCustomer().getId().equals(customerId))
                    .map(Sale::calculateBalance).filter(value -> value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        @Override public boolean supportsDurableIdempotency() { return false; }
    }

    private static final class FakeGeneralDebtWorkflow implements CustomerGeneralDebtWorkflow {
        private final List<GeneralDebtMovement> movements;
        private FakeGeneralDebtWorkflow(List<GeneralDebtMovement> movements) { this.movements = movements; }
        @Override public com.laexclusiva.model.CustomerDebtBreakdown findBalance(String customerId) { throw new UnsupportedOperationException(); }
        @Override public GeneralDebtMovement registerDebt(String customerId, BigDecimal amount, String category, String description, String key) { throw new UnsupportedOperationException(); }
        @Override public GeneralDebtMovement registerPayment(String customerId, BigDecimal amount, String category, String description, String key) { throw new UnsupportedOperationException(); }
        @Override public List<GeneralDebtMovement> listMovements(String customerId, LocalDate from, LocalDate through, String category) {
            return movements.stream().filter(value -> customerId == null || value.customerId().equals(customerId))
                    .filter(value -> from == null || !value.occurredAt().toLocalDate().isBefore(from))
                    .filter(value -> through == null || !value.occurredAt().toLocalDate().isAfter(through))
                    .filter(value -> category == null || value.category().equals(category)).toList();
        }
        @Override public boolean supportsDurableIdempotency() { return false; }
    }

    private static final class FakeEmail implements EmailDeliveryProvider {
        private final boolean real;
        private final boolean fail;
        private final AtomicInteger calls = new AtomicInteger();
        private FakeEmail(boolean real, boolean fail) { this.real = real; this.fail = fail; }
        @Override public boolean isRealDelivery() { return real; }
        @Override public Result deliver(Message message) {
            calls.incrementAndGet();
            if (fail) throw new IllegalStateException("smtp credentials and SQL must not reach UI");
            return new Result(real, real ? "ACCEPTED" : "SIMULATED");
        }
    }

    private static TransactionBoundary direct() {
        return new TransactionBoundary() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> operation) { return operation.get(); }
        };
    }
}
