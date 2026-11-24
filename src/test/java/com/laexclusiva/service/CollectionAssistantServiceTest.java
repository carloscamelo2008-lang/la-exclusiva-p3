package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
    void changingEmailRenewsConsentAndPhoneChannelDoesNotRetainEmail() {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        fixture.customer("changed-email", "100.00", List.of());
        LocalDateTime earlierConsent = TODAY.minusDays(2).atTime(9, 0);
        fixture.collectionRepository.saveProfile(new com.laexclusiva.model.CollectionProfile("changed-email",
                "old@example.test", CollectionChannel.EMAIL, true, earlierConsent, true, null,
                new BigDecimal("100.00"), earlierConsent), List.of());

        var updated = fixture.service.saveProfile(fixture.admin.session(), "changed-email", "new@example.test",
                CollectionChannel.EMAIL, true, true, null, null);

        assertEquals("new@example.test", updated.email());
        assertTrue(updated.consentRecordedAt().isAfter(earlierConsent));
        assertEquals(CollectionFollowUp.EventType.CONSENT_CHANGED,
                fixture.service.history(fixture.admin.session(), "changed-email").get(0).eventType());

        fixture.customer("phone-only", "75.00", List.of());
        var phoneOnly = fixture.service.saveProfile(fixture.admin.session(), "phone-only", "unused@example.test",
                CollectionChannel.PHONE, true, true, null, null);
        assertNull(phoneOnly.email());
        assertNull(fixture.service.profile(fixture.admin.session(), "phone-only").email());
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

        String confirmation = fixture.service.confirmReminder(fixture.admin.session(), reminder.id(), preview.previewToken());
        var outcome = fixture.service.sendReminder(fixture.admin.session(), reminder.id(), confirmation);
        assertEquals(CustomerCollectionReminder.ResultCode.SIMULATED, outcome.resultCode());
        assertEquals(CustomerCollectionReminder.Status.PENDING_REVIEW, outcome.status());
        assertFalse(outcome.providerAccepted());
        assertEquals(CustomerCollectionReminder.ProviderEvidence.SIMULATED,
                fixture.collectionRepository.findReminder("email-client", reminder.weekStart()).orElseThrow().providerEvidence());
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
        var preview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        String confirmation = fixture.service.confirmReminder(fixture.admin.session(), reminder.id(), preview.previewToken());

        var result = fixture.service.sendReminder(fixture.admin.session(), reminder.id(), confirmation);

        assertEquals(CustomerCollectionReminder.Status.SENT, result.status());
        assertTrue(result.providerAccepted());
        assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_ACCEPTED,
                fixture.collectionRepository.findReminder("send-once", reminder.weekStart()).orElseThrow().providerEvidence());
        assertEquals(1, email.calls.get());
        assertThrows(SecurityException.class,
                () -> fixture.service.sendReminder(fixture.admin.session(), reminder.id(), confirmation));
        assertEquals(1, email.calls.get());

        FakeEmail uncertainEmail = new FakeEmail(true, true);
        Fixture uncertain = new Fixture(uncertainEmail, true, true, "configured-key");
        uncertain.customer("uncertain", "45.00", List.of());
        uncertain.service.saveProfile(uncertain.admin.session(), "uncertain", "uncertain@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var uncertainReminder = uncertain.service.prepareWeeklyReminders(uncertain.admin.session()).pendingReview().get(0);
        var uncertainPreview = uncertain.service.previewReminder(uncertain.admin.session(), uncertainReminder.id());
        String uncertainConfirmation = uncertain.service.confirmReminder(uncertain.admin.session(), uncertainReminder.id(),
                uncertainPreview.previewToken());
        var failed = uncertain.service.sendReminder(uncertain.admin.session(), uncertainReminder.id(), uncertainConfirmation);
        assertEquals(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED, failed.status());
        assertEquals(CustomerCollectionReminder.ResultCode.PROVIDER_OUTCOME_UNCERTAIN, failed.resultCode());
        assertTrue(failed.outcomeUncertain());
        assertNotNull(failed.attemptId());
        assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_OUTCOME_UNCERTAIN,
                uncertain.collectionRepository.findReminder("uncertain", uncertainReminder.weekStart())
                        .orElseThrow().providerEvidence());
        assertEquals(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED,
                uncertain.service.listReminders(uncertain.admin.session(), CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED)
                        .get(0).status());
        assertThrows(SecurityException.class,
                () -> uncertain.service.sendReminder(uncertain.admin.session(), uncertainReminder.id(), uncertainConfirmation));
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
        var preview = unconfirmed.service.previewReminder(unconfirmed.admin.session(), reminder.id());
        String confirmation = unconfirmed.service.confirmReminder(unconfirmed.admin.session(), reminder.id(), preview.previewToken());
        var blockedByKey = unconfirmed.service.sendReminder(unconfirmed.admin.session(), reminder.id(), confirmation);
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
    void changedRecipientInvalidatesAnotherAdministratorsOpenPreview() {
        FakeEmail email = new FakeEmail(true, false);
        Fixture fixture = new Fixture(email, true, true, "configured-key");
        fixture.customer("recipient-change", "90.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "recipient-change", "old@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var reminder = fixture.service.prepareWeeklyReminders(fixture.admin.session()).pendingReview().get(0);
        var oldPreview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());

        fixture.service.saveProfile(fixture.secondAdmin.session(), "recipient-change", "new@example.test",
                CollectionChannel.EMAIL, true, true, null, null);

        assertThrows(SecurityException.class, () -> fixture.service.confirmReminder(
                fixture.admin.session(), reminder.id(), oldPreview.previewToken()));
        assertThrows(SecurityException.class, () -> fixture.service.sendReminder(fixture.admin.session(), reminder.id()));
        assertEquals(0, email.calls.get());

        var newPreview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        assertEquals("new@example.test", newPreview.recipient());
        String confirmation = fixture.service.confirmReminder(fixture.admin.session(), reminder.id(), newPreview.previewToken());
        var result = fixture.service.sendReminder(fixture.admin.session(), reminder.id(), confirmation);
        assertEquals(CustomerCollectionReminder.Status.SENT, result.status());
        assertEquals("new@example.test", email.lastMessage.get().recipient());
        assertArrayEquals(newPreview.pdf(), email.lastMessage.get().attachment());
        assertEquals(1, email.calls.get());
    }

    @Test
    void reviewTokensExpireAreSingleUseAndStopWorkingAfterSessionRevocation() {
        TestClock clock = new TestClock(TODAY.atTime(10, 0).atZone(CollectionAssistantService.COLOMBIA).toInstant());
        FakeEmail email = new FakeEmail(true, false);
        Fixture fixture = new Fixture(email, true, true, "configured-key", clock);
        fixture.customer("token-expiry", "80.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "token-expiry", "token@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var reminder = fixture.service.prepareWeeklyReminders(fixture.admin.session()).pendingReview().get(0);
        var expiredPreview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        clock.advance(Duration.ofMinutes(6));
        assertThrows(SecurityException.class, () -> fixture.service.confirmReminder(
                fixture.admin.session(), reminder.id(), expiredPreview.previewToken()));

        var expiredConfirmationPreview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        String expiredConfirmation = fixture.service.confirmReminder(fixture.admin.session(), reminder.id(),
                expiredConfirmationPreview.previewToken());
        clock.advance(Duration.ofMinutes(6));
        assertThrows(SecurityException.class,
                () -> fixture.service.sendReminder(fixture.admin.session(), reminder.id(), expiredConfirmation));

        var preview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        String confirmation = fixture.service.confirmReminder(fixture.admin.session(), reminder.id(), preview.previewToken());
        fixture.identity.authentication().revoke(fixture.admin.session());
        assertThrows(SecurityException.class,
                () -> fixture.service.sendReminder(fixture.admin.session(), reminder.id(), confirmation));
        assertEquals(0, email.calls.get());
    }

    @Test
    void twoConcurrentUsesOfTheSameConfirmationCanOnlyDeliverOnce() throws Exception {
        FakeEmail email = new FakeEmail(true, false);
        Fixture fixture = new Fixture(email, true, true, "configured-key");
        fixture.customer("concurrent-send", "75.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "concurrent-send", "once@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var reminder = fixture.service.prepareWeeklyReminders(fixture.admin.session()).pendingReview().get(0);
        var preview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        String confirmation = fixture.service.confirmReminder(fixture.admin.session(), reminder.id(), preview.previewToken());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> calls = List.of(
                    executor.submit(() -> deliverConcurrent(fixture, reminder.id(), confirmation, start, delivered)),
                    executor.submit(() -> deliverConcurrent(fixture, reminder.id(), confirmation, start, delivered)));
            start.countDown();
            for (Future<?> call : calls) call.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, delivered.get());
        assertEquals(1, email.calls.get());
    }

    @Test
    void changedBalanceRefreshesTheSameWeeklyReminderAndRequiresNewReview() throws Exception {
        FakeEmail email = new FakeEmail(false, false);
        Fixture fixture = new Fixture(email);
        fixture.customer("balance-refresh", "85000.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "balance-refresh", "balance@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var first = fixture.service.prepareWeeklyReminders(fixture.admin.session());
        var oldReminder = first.pendingReview().get(0);
        var stalePreview = fixture.service.previewReminder(fixture.admin.session(), oldReminder.id());

        Customer customer = fixture.customers.getCustomerById("balance-refresh");
        customer.applyPayment(new BigDecimal("20000.00"));
        fixture.customerRepository.update(customer);
        fixture.generalMovements.add(new GeneralDebtMovement(2, "balance-refresh",
                GeneralDebtMovementType.PAYMENT, new BigDecimal("20000.00"),
                TODAY.atTime(11, 0), "ABONO", "Abono ficticio de prueba", "balance-payment-key"));

        var refreshed = fixture.service.prepareWeeklyReminders(fixture.admin.session());

        assertEquals(0, refreshed.created());
        assertEquals(1, refreshed.refreshed());
        assertEquals(1, refreshed.pendingReview().size());
        assertEquals(oldReminder.id(), refreshed.pendingReview().get(0).id());
        assertEquals(0, refreshed.pendingReview().get(0).balanceSnapshot().compareTo(new BigDecimal("65000.00")));
        assertThrows(SecurityException.class, () -> fixture.service.confirmReminder(
                fixture.admin.session(), oldReminder.id(), stalePreview.previewToken()));
        var currentPreview = fixture.service.previewReminder(fixture.admin.session(), oldReminder.id());
        try (var document = Loader.loadPDF(currentPreview.pdf())) {
            assertTrue(new PDFTextStripper().getText(document).contains("65.000"));
        }
        assertEquals(1, fixture.service.listReminders(fixture.admin.session(),
                CustomerCollectionReminder.Status.PENDING_REVIEW).size());
        assertTrue(fixture.service.history(fixture.admin.session(), "balance-refresh").stream()
                .anyMatch(event -> event.eventType() == CollectionFollowUp.EventType.BALANCE_CHANGED
                        && event.balanceSnapshot().compareTo(new BigDecimal("65000.00")) == 0));
    }

    @Test
    void confirmedProviderRejectionIsDistinctFromAnUncertainResponse() {
        FakeEmail email = new FakeEmail(true, false, EmailDeliveryProvider.Outcome.REJECTED);
        Fixture fixture = new Fixture(email, true, true, "configured-key");
        fixture.customer("provider-rejected", "40.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "provider-rejected", "reject@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var reminder = fixture.service.prepareWeeklyReminders(fixture.admin.session()).pendingReview().get(0);
        var preview = fixture.service.previewReminder(fixture.admin.session(), reminder.id());
        String confirmation = fixture.service.confirmReminder(fixture.admin.session(), reminder.id(), preview.previewToken());

        var result = fixture.service.sendReminder(fixture.admin.session(), reminder.id(), confirmation);

        assertEquals(CustomerCollectionReminder.Status.FAILED, result.status());
        assertEquals(CustomerCollectionReminder.ResultCode.PROVIDER_REJECTED, result.resultCode());
        assertFalse(result.outcomeUncertain());
        assertFalse(result.providerAccepted());
        assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_REJECTED,
                fixture.collectionRepository.findReminder("provider-rejected", reminder.weekStart())
                        .orElseThrow().providerEvidence());
        assertEquals(1, email.calls.get());
    }

    @Test
    void failedPersistenceAfterProviderAcceptanceLeavesAnUncertainAttempt() {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        fixture.customer("persistence-failure", "35.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "persistence-failure", "persist@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        var repository = (com.laexclusiva.repository.CustomerCollectionRepository) Proxy.newProxyInstance(
                com.laexclusiva.repository.CustomerCollectionRepository.class.getClassLoader(),
                new Class<?>[]{com.laexclusiva.repository.CustomerCollectionRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("updateReminder") && failOnce.getAndSet(false)) {
                        throw new IllegalStateException("Fallo de persistencia simulado.");
                    }
                    try { return method.invoke(fixture.collectionRepository, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        FakeEmail accepted = new FakeEmail(true, false);
        CollectionAssistantService service = fixture.newService(repository, accepted, fixture.clock);
        var reminder = service.prepareWeeklyReminders(fixture.admin.session()).pendingReview().get(0);
        var preview = service.previewReminder(fixture.admin.session(), reminder.id());
        String confirmation = service.confirmReminder(fixture.admin.session(), reminder.id(), preview.previewToken());

        var result = service.sendReminder(fixture.admin.session(), reminder.id(), confirmation);

        assertEquals(1, accepted.calls.get());
        assertEquals(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED, result.status());
        assertTrue(result.outcomeUncertain());
        assertNotNull(result.attemptId());
        assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_ACCEPTED,
                fixture.collectionRepository.findReminder("persistence-failure", reminder.weekStart())
                        .orElseThrow().providerEvidence());
        assertEquals(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED,
                fixture.collectionRepository.findReminder("persistence-failure", reminder.weekStart()).orElseThrow().status());
    }

    @Test
    void startupRecoversPersistedSendingAttemptWithoutRetrying() {
        FakeEmail email = new FakeEmail(true, false);
        Fixture fixture = new Fixture(email, true, true, "configured-key");
        fixture.customer("restart-recovery", "65.00", List.of());
        fixture.service.saveProfile(fixture.admin.session(), "restart-recovery", "restart@example.test",
                CollectionChannel.EMAIL, true, true, null, null);
        var reminder = fixture.service.prepareWeeklyReminders(fixture.admin.session()).pendingReview().get(0);
        var profile = fixture.collectionRepository.findProfile("restart-recovery").orElseThrow();
        UUID attemptId = UUID.randomUUID();
        assertTrue(fixture.collectionRepository.claimReminder(reminder, attemptId, UUID.randomUUID(),
                TODAY.atTime(10, 1), new BigDecimal("65.00"), fixture.customers.getCustomerById("restart-recovery"), profile));

        CollectionAssistantService restarted = fixture.newService(fixture.collectionRepository, email, fixture.clock);
        var reconciled = restarted.listReminders(fixture.admin.session(),
                CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED);

        assertEquals(1, reconciled.size());
        assertEquals(attemptId, reconciled.get(0).attemptId());
        assertEquals(CustomerCollectionReminder.ResultCode.PROVIDER_OUTCOME_UNCERTAIN, reconciled.get(0).resultCode());
        assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_OUTCOME_UNCERTAIN,
                reconciled.get(0).providerEvidence());
        assertEquals(0, email.calls.get(), "La recuperación nunca reenvía un intento interrumpido.");
    }

    private static void deliverConcurrent(Fixture fixture, long reminderId, String token,
            CountDownLatch start, AtomicInteger delivered) {
        try {
            start.await(5, TimeUnit.SECONDS);
            fixture.service.sendReminder(fixture.admin.session(), reminderId, token);
            delivered.incrementAndGet();
        } catch (SecurityException expected) {
            // El token de confirmación es de un solo uso.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
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
    void pdfWrapsWideCustomerNamesWithinPrintablePageWidth() throws Exception {
        Fixture fixture = new Fixture(new FakeEmail(false, false));
        String longName = "W".repeat(160);
        fixture.customerRepository.save(new Customer("wide-name", longName, "3000000000",
                "Dirección ficticia", new BigDecimal("90.00")));

        byte[] pdf = fixture.service.statementPdf(fixture.worker.session(), "wide-name");

        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.replaceAll("\\s+", "").contains(longName),
                    "El nombre debe aparecer completo aunque se divida entre renglones.");
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float availableWidth = PDRectangle.A4.getWidth() - 96;
            for (String line : text.split("\\R")) {
                if (!line.contains("W")) continue;
                float renderedWidth = regular.getStringWidth(line.strip()) / 1000f * 10;
                assertTrue(renderedWidth <= availableWidth + 1,
                        "La línea del cliente debe caber dentro de los márgenes del PDF.");
            }
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
        private final IdentityTestFixture.IssuedUser secondAdmin = identity.issue(UserRole.ADMINISTRATOR);
        private final IdentityTestFixture.IssuedUser worker = identity.issue(UserRole.WORKER);
        private final CustomerRepository customerRepository = new CustomerRepository();
        private final CustomerService customers = new CustomerService(customerRepository);
        private final List<Sale> sales = new ArrayList<>();
        private final List<GeneralDebtMovement> generalMovements = new ArrayList<>();
        private final InMemoryCustomerCollectionRepository collectionRepository = new InMemoryCustomerCollectionRepository();
        private final InMemoryAdministrativeAuditRepository audit = new InMemoryAdministrativeAuditRepository();
        private final Clock clock;
        private final CollectionAssistantService service;

        private Fixture(FakeEmail email) { this(email, false, false, null); }

        private Fixture(FakeEmail email, boolean ownerConfirmed, boolean keyVerified, String paymentKey) {
            this(email, ownerConfirmed, keyVerified, paymentKey,
                    Clock.fixed(TODAY.atTime(10, 0).atZone(CollectionAssistantService.COLOMBIA).toInstant(),
                            CollectionAssistantService.COLOMBIA));
        }

        private Fixture(FakeEmail email, boolean ownerConfirmed, boolean keyVerified, String paymentKey, Clock clock) {
            this.clock = clock;
            service = newService(collectionRepository, email, clock, ownerConfirmed, keyVerified, paymentKey);
        }

        private CollectionAssistantService newService(com.laexclusiva.repository.CustomerCollectionRepository repository,
                FakeEmail email, Clock serviceClock) {
            return newService(repository, email, serviceClock, true, true, "configured-key");
        }

        private CollectionAssistantService newService(com.laexclusiva.repository.CustomerCollectionRepository repository,
                FakeEmail email, Clock serviceClock, boolean ownerConfirmed, boolean keyVerified, String paymentKey) {
            return new CollectionAssistantService(customers, new FakeSalesWorkflow(sales),
                    new FakeGeneralDebtWorkflow(generalMovements), repository,
                    audit, identity.authorization(), email, new AccountStatementPdfGenerator(),
                    new ColombiaContactWindow(java.util.Set.of(LocalDate.of(2026, 12, 25)), true), direct(), serviceClock,
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
        private final Outcome outcome;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<Message> lastMessage = new AtomicReference<>();
        private FakeEmail(boolean real, boolean fail) {
            this(real, fail, real ? Outcome.ACCEPTED : Outcome.SIMULATED);
        }
        private FakeEmail(boolean real, boolean fail, Outcome outcome) {
            this.real = real;
            this.fail = fail;
            this.outcome = outcome;
        }
        @Override public boolean isRealDelivery() { return real; }
        @Override public Result deliver(Message message) {
            calls.incrementAndGet();
            lastMessage.set(message);
            if (fail) throw new IllegalStateException("smtp credentials and SQL must not reach UI");
            return switch (outcome) {
                case ACCEPTED -> Result.accepted();
                case REJECTED -> Result.rejected();
                case UNCERTAIN -> Result.uncertain();
                case SIMULATED -> Result.simulated();
            };
        }
    }

    private static final class TestClock extends Clock {
        private final AtomicReference<Instant> now;
        private final ZoneId zone;
        private TestClock(Instant initial) {
            this(new AtomicReference<>(initial), CollectionAssistantService.COLOMBIA);
        }
        private TestClock(AtomicReference<Instant> now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }
        private void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId requestedZone) { return new TestClock(now, requestedZone); }
        @Override public Instant instant() { return now.get(); }
    }

    private static TransactionBoundary direct() {
        return new TransactionBoundary() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> operation) { return operation.get(); }
        };
    }
}
