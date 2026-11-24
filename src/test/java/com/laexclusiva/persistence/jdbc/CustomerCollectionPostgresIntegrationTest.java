package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.AdministrativeAuditEvent;
import com.laexclusiva.model.CollectionChannel;
import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.CollectionProfile;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.jdbc.AdministrativeAuditRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerCollectionRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.repository.CustomerCollectionRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.repository.AdministrativeAuditRepository;
import com.laexclusiva.security.PasswordHasher;
import com.laexclusiva.service.AccountStatementPdfGenerator;
import com.laexclusiva.service.AuthenticationService;
import com.laexclusiva.service.AuthorizationService;
import com.laexclusiva.service.ColombiaContactWindow;
import com.laexclusiva.service.CollectionAssistantService;
import com.laexclusiva.service.CustomerGeneralDebtWorkflow;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.EmailDeliveryProvider;
import com.laexclusiva.service.SalesWorkflow;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.StockRequest;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Prueba optativa solo para el laboratorio efímero autorizado que aplica V001 y V107–V109. */
class CustomerCollectionPostgresIntegrationTest {
    private static final String ENABLE = "laexclusiva.test.postgres.collections";

    @Test
    void persistsConsentWeeklyUniquenessAuditAndAtomicReminderClaim() {
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLE), "Integración PostgreSQL de cobranza sin autorización de laboratorio.");
        Assumptions.assumeTrue(System.getenv(DatabaseConfig.TEST_URL_ENV) != null,
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        CustomerCollectionRepositoryJdbc collections = new CustomerCollectionRepositoryJdbc(provider, transactions);
        CustomerRepositoryJdbc customers = new CustomerRepositoryJdbc(provider);
        UserRepositoryJdbc users = new UserRepositoryJdbc(provider);
        AdministrativeAuditRepositoryJdbc audit = new AdministrativeAuditRepositoryJdbc(provider);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String customerId = "C-COLLECT-" + suffix.substring(0, 20);
        String username = "collection-lab-" + suffix.substring(0, 16);
        UserAccount actor = new UserAccount(null, username, PasswordHasher.hash("Fixture-Not-Used-" + suffix + "!"),
                UserRole.ADMINISTRATOR, null);
        boolean customerCreated = false;
        boolean actorCreated = false;

        try {
            customers.save(new Customer(customerId, "Cliente ficticio laboratorio", "3000000091",
                    "Dirección ficticia", new BigDecimal("125.00")));
            customerCreated = true;
            users.save(actor);
            actorCreated = true;
            LocalDateTime now = LocalDateTime.of(2026, 11, 23, 10, 0);
            LocalDate week = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            CollectionProfile profile = new CollectionProfile(customerId, "fixture@example.test",
                    CollectionChannel.EMAIL, true, now, true, LocalDate.of(2026, 11, 25),
                    new BigDecimal("125.00"), now);
            CollectionFollowUp consentEvent = new CollectionFollowUp(0, customerId, actor.getId(),
                    CollectionFollowUp.EventType.CONSENT_CHANGED, null, new BigDecimal("125.00"),
                    "Consentimiento ficticio para integración.", now);
            collections.saveProfile(profile, List.of(consentEvent));
            assertEquals(profile, collections.findProfile(customerId).orElseThrow());
            assertThrows(RuntimeException.class, () -> provider.withConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE customer_collection_profile SET authorized_channel='PHONE'
                        WHERE customer_id=?
                        """)) {
                    statement.setString(1, customerId);
                    statement.executeUpdate();
                }
                return null;
            }), "La restricción SQL no debe conservar un email cuando el canal autorizado es telefónico.");
            assertEquals(CollectionChannel.EMAIL,
                    collections.findProfile(customerId).orElseThrow().authorizedChannel());

            CustomerCollectionReminder proposed = new CustomerCollectionReminder(0, customerId, week,
                    CollectionChannel.EMAIL, CustomerCollectionReminder.Status.PENDING_REVIEW,
                    new BigDecimal("125.00"), now, null, 0, CustomerCollectionReminder.ResultCode.PREPARED);
            CollectionFollowUp preparedEvent = new CollectionFollowUp(0, customerId, actor.getId(),
                    CollectionFollowUp.EventType.REMINDER_PREPARED, profile.commitmentDate(),
                    new BigDecimal("125.00"), "Aviso ficticio agregado a revisión.", now);
            setFixtureBalance(provider, customerId, new BigDecimal("100.00"));
            assertThrows(java.util.ConcurrentModificationException.class,
                    () -> collections.createReminderIfAbsent(proposed, preparedEvent),
                    "No debe prepararse un aviso a partir de un saldo que cambió durante la lectura.");
            assertTrue(collections.findReminder(customerId, week).isEmpty(),
                    "La transacción debe revertir una preparación obsoleta.");
            setFixtureBalance(provider, customerId, new BigDecimal("125.00"));
            var first = collections.createReminderIfAbsent(proposed, preparedEvent);
            var repeated = collections.createReminderIfAbsent(proposed, preparedEvent);
            assertTrue(first.inserted());
            assertFalse(repeated.inserted());
            assertEquals(first.reminder().id(), repeated.reminder().id());
            assertEquals(2, collections.findFollowUps(customerId).size());

            UUID attemptId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            assertTrue(collections.claimReminder(first.reminder(), attemptId, ownerId, now.plusMinutes(1),
                    new BigDecimal("125.00"), customers.findById(customerId), profile));
            CustomerCollectionReminder claimed = collections.findReminder(customerId, week).orElseThrow();
            assertEquals(attemptId, claimed.attemptId());
            assertEquals(ownerId, claimed.attemptOwnerId());
            assertFalse(collections.claimReminder(first.reminder(), UUID.randomUUID(), UUID.randomUUID(), now.plusMinutes(2),
                    new BigDecimal("125.00"), customers.findById(customerId), profile),
                    "El mismo aviso no puede reclamarse en paralelo ni repetirse.");
            CustomerCollectionReminder sent = new CustomerCollectionReminder(first.reminder().id(), customerId,
                    week, CollectionChannel.EMAIL, CustomerCollectionReminder.Status.SENT,
                    first.reminder().balanceSnapshot(), now, now.plusMinutes(1), 1,
                    CustomerCollectionReminder.ResultCode.SENT, attemptId, null,
                    CustomerCollectionReminder.ProviderEvidence.SMTP_ACCEPTED);
            collections.updateReminder(sent, CustomerCollectionReminder.Status.SENDING, 1);
            assertEquals(CustomerCollectionReminder.Status.SENT,
                    collections.findReminder(customerId, week).orElseThrow().status());
            assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_ACCEPTED,
                    collections.findReminder(customerId, week).orElseThrow().providerEvidence());
            assertThrows(RuntimeException.class, () -> collections.updateReminder(sent,
                    CustomerCollectionReminder.Status.SENDING, 1), "La transición usa compare-and-set SQL.");

            audit.append(new AdministrativeAuditEvent(0, actor.getId(),
                    AdministrativeAuditEvent.Action.STATEMENT_SEND_ATTEMPT,
                    AdministrativeAuditEvent.TargetType.CUSTOMER, customerId, now,
                    AdministrativeAuditEvent.Result.SUCCESS));
            assertEquals(1, audit.find(null, null, 10).stream()
                    .filter(event -> event.targetReference().equals(customerId)).count());

            CollectionProfile changedProfile = new CollectionProfile(customerId, "changed@example.test",
                    CollectionChannel.EMAIL, true, now, true, profile.commitmentDate(),
                    new BigDecimal("99.00"), now.plusMinutes(3));
            CollectionFollowUp invalidActorEvent = new CollectionFollowUp(0, customerId, Long.MAX_VALUE,
                    CollectionFollowUp.EventType.NOTE_ADDED, null, new BigDecimal("99.00"),
                    "Este cambio debe revertirse.", now.plusMinutes(3));
            assertThrows(RuntimeException.class,
                    () -> collections.saveProfile(changedProfile, List.of(invalidActorEvent)));
            assertEquals(profile.email(), collections.findProfile(customerId).orElseThrow().email(),
                    "El perfil revierte si el historial no puede insertarse.");
            assertEquals(2, collections.findFollowUps(customerId).size());

            assertThrows(RuntimeException.class, () -> insertInvalidReminder(provider, customerId, week.plusWeeks(1), now));
            assertThrows(RuntimeException.class, () -> insertDuplicateReminder(provider, customerId, week, now));
            UUID durableAttemptId = UUID.randomUUID();
            assertThrows(RuntimeException.class, () -> insertReminderWithAttempt(provider, customerId,
                    week.plusWeeks(2), now, "SENDING", 1, "PREPARED", null, null));
            assertThrows(RuntimeException.class, () -> insertReminderWithAttempt(provider, customerId,
                    week.plusWeeks(2), now, "RECONCILIATION_REQUIRED", 1,
                    "PROVIDER_OUTCOME_UNCERTAIN", null, null));
            assertThrows(RuntimeException.class, () -> insertReminderWithAttempt(provider, customerId,
                    week.plusWeeks(2), now, "RECONCILIATION_REQUIRED", 1, "PREPARED", durableAttemptId, null));
            assertThrows(RuntimeException.class, () -> insertReminderWithAttempt(provider, customerId,
                    week.plusWeeks(2), now, "PENDING_REVIEW", 0, "PREPARED", null, UUID.randomUUID()));
            assertThrows(RuntimeException.class, () -> insertReminderWithInvalidEvidence(provider, customerId,
                    week.plusWeeks(2), now));
            assertThrows(RuntimeException.class, () -> insertReminderWithAttempt(provider, customerId,
                    week.plusWeeks(2), now, "FAILED", 1, "PROVIDER_REJECTED", attemptId, null));

            LocalDate recoveryWeek = week.plusWeeks(3);
            var interrupted = collections.createReminderIfAbsent(new CustomerCollectionReminder(0, customerId,
                    recoveryWeek, CollectionChannel.EMAIL, CustomerCollectionReminder.Status.PENDING_REVIEW,
                    new BigDecimal("125.00"), now, null, 0, CustomerCollectionReminder.ResultCode.PREPARED),
                    new CollectionFollowUp(0, customerId, actor.getId(),
                            CollectionFollowUp.EventType.REMINDER_PREPARED, null, new BigDecimal("125.00"),
                            "Segundo fixture ficticio para probar recuperación de intento.", now));
            UUID interruptedAttempt = UUID.randomUUID();
            UUID oldOwner = UUID.randomUUID();
            assertTrue(collections.claimReminder(interrupted.reminder(), interruptedAttempt, oldOwner, now,
                    new BigDecimal("125.00"), customers.findById(customerId), profile));
            assertEquals(1, collections.reconcileInterruptedAttempts(UUID.randomUUID()));
            CustomerCollectionReminder reconciled = collections.findReminder(customerId, recoveryWeek).orElseThrow();
            assertEquals(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED, reconciled.status());
            assertEquals(CustomerCollectionReminder.ResultCode.PROVIDER_OUTCOME_UNCERTAIN, reconciled.resultCode());
            assertEquals(interruptedAttempt, reconciled.attemptId());
            assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_OUTCOME_UNCERTAIN,
                    reconciled.providerEvidence());
            assertNull(reconciled.attemptOwnerId());
            assertFalse(collections.claimReminder(reconciled, UUID.randomUUID(), UUID.randomUUID(), now,
                    new BigDecimal("125.00"), customers.findById(customerId), profile));
        } finally {
            cleanup(provider, customerId, actor.getId(), customerCreated, actorCreated);
        }
    }

    @Test
    void persistsUncertainProviderOutcomeAndPostSendSaveFailureAcrossRestart() {
        Assumptions.assumeTrue(Boolean.getBoolean(ENABLE), "Integración PostgreSQL de cobranza sin autorización de laboratorio.");
        Assumptions.assumeTrue(System.getenv(DatabaseConfig.TEST_URL_ENV) != null,
                "Omitida: LAEXCLUSIVA_TEST_DB_URL no está configurada.");
        DatabaseConfig config = DatabaseConfig.fromTestEnvironment();
        JdbcConnectionProvider provider = new JdbcConnectionProvider(config);
        JdbcTransactionManager transactions = new JdbcTransactionManager(provider);
        CustomerCollectionRepositoryJdbc jdbcCollections = new CustomerCollectionRepositoryJdbc(provider, transactions);
        CustomerRepositoryJdbc jdbcCustomers = new CustomerRepositoryJdbc(provider);
        UserRepositoryJdbc users = new UserRepositoryJdbc(provider);
        AdministrativeAuditRepositoryJdbc audit = new AdministrativeAuditRepositoryJdbc(provider);
        String database = provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT current_database()")) {
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("No se obtuvo el nombre de la base conectada.");
                    return rows.getString(1);
                }
            }
        });
        assertEquals("la_exclusiva_test", database, "Los fixtures nunca pueden salir de la base aislada.");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String firstCustomerId = "C-COLLECT-E2E-A-" + suffix;
        String secondCustomerId = "C-COLLECT-E2E-B-" + suffix;
        String username = "collection-e2e-" + suffix.toLowerCase();
        String password = "Fixture-Only-" + UUID.randomUUID() + "!";
        UserAccount actor = new UserAccount(null, username, PasswordHasher.hash(password),
                UserRole.ADMINISTRATOR, null);
        boolean firstCustomerCreated = false;
        boolean secondCustomerCreated = false;
        boolean actorCreated = false;
        try {
            jdbcCustomers.save(new Customer(firstCustomerId, "Cliente ficticio conciliación A", "3000000092",
                    "Dirección ficticia", new BigDecimal("125.00")));
            firstCustomerCreated = true;
            jdbcCustomers.save(new Customer(secondCustomerId, "Cliente ficticio conciliación B", "3000000093",
                    "Dirección ficticia", new BigDecimal("70.00")));
            secondCustomerCreated = true;
            users.save(actor);
            actorCreated = true;
            AuthenticationService authentication = new AuthenticationService(users);
            AuthorizationService authorization = new AuthorizationService(authentication.sessions());
            var session = authentication.createSession(username, password).orElseThrow();
            CustomerService customerService = new CustomerService(jdbcCustomers);
            SalesWorkflow sales = new SalesWorkflow() {
                @Override public Sale registerSale(List<StockRequest> lines, String sellerId, String customerId,
                        BigDecimal initialPayment) { throw new UnsupportedOperationException(); }
                @Override public Sale registerPayment(String saleId, BigDecimal amount, String idempotencyKey) {
                    throw new UnsupportedOperationException();
                }
                @Override public Sale findById(String saleId) { throw new UnsupportedOperationException(); }
                @Override public List<Sale> listAll() { return List.of(); }
                @Override public BigDecimal outstandingSalesDebt(String customerId) { return BigDecimal.ZERO; }
                @Override public boolean supportsDurableIdempotency() { return true; }
            };
            CustomerGeneralDebtWorkflow generalDebt = new CustomerGeneralDebtWorkflow() {
                @Override public CustomerDebtBreakdown findBalance(String customerId) {
                    Customer customer = customerService.getCustomerById(customerId);
                    return new CustomerDebtBreakdown(customer.getBalance(), BigDecimal.ZERO, customer.getBalance());
                }
                @Override public GeneralDebtMovement registerDebt(String customerId, BigDecimal amount,
                        String category, String description, String idempotencyKey) {
                    throw new UnsupportedOperationException();
                }
                @Override public GeneralDebtMovement registerPayment(String customerId, BigDecimal amount,
                        String category, String description, String idempotencyKey) {
                    throw new UnsupportedOperationException();
                }
                @Override public List<GeneralDebtMovement> listMovements(String customerId, LocalDate from,
                        LocalDate through, String category) { return List.of(); }
                @Override public boolean supportsDurableIdempotency() { return true; }
            };
            Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 23, 10, 0)
                    .atZone(ZoneId.of("America/Bogota")).toInstant(), ZoneId.of("America/Bogota"));
            AtomicInteger providerCalls = new AtomicInteger();
            EmailDeliveryProvider simulatedProvider = new EmailDeliveryProvider() {
                @Override public boolean isRealDelivery() { return true; }
                @Override public Result deliver(Message message) {
                    int call = providerCalls.incrementAndGet();
                    return call == 1 ? Result.uncertain() : Result.accepted();
                }
            };
            CollectionAssistantService service = collectionService(customerService, sales, generalDebt,
                    jdbcCollections, audit, authorization, simulatedProvider, transactions, clock);
            for (String id : List.of(firstCustomerId, secondCustomerId)) {
                service.saveProfile(session, id, id.toLowerCase() + "@example.test", CollectionChannel.EMAIL,
                        true, true, null, null);
            }
            var prepared = service.prepareWeeklyReminders(session);
            assertEquals(2, prepared.created());

            CustomerCollectionReminder first = prepared.pendingReview().stream()
                    .filter(value -> value.customerId().equals(firstCustomerId)).findFirst().orElseThrow();
            var firstPreview = service.previewReminder(session, first.id());
            String firstConfirmation = service.confirmReminder(session, first.id(), firstPreview.previewToken());
            var uncertain = service.sendReminder(session, first.id(), firstConfirmation);
            assertEquals(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED, uncertain.status());
            assertTrue(uncertain.outcomeUncertain());
            assertNotNull(uncertain.attemptId());
            assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_OUTCOME_UNCERTAIN,
                    jdbcCollections.findReminder(firstCustomerId, first.weekStart()).orElseThrow().providerEvidence());

            CustomerCollectionReminder second = prepared.pendingReview().stream()
                    .filter(value -> value.customerId().equals(secondCustomerId)).findFirst().orElseThrow();
            AtomicInteger failFirstOutcomeWrite = new AtomicInteger(1);
            CustomerCollectionRepository failingOutcomeWrite = (CustomerCollectionRepository) Proxy.newProxyInstance(
                    CustomerCollectionRepository.class.getClassLoader(), new Class<?>[]{CustomerCollectionRepository.class},
                    (proxyObject, method, arguments) -> {
                        if (method.getName().equals("updateReminder") && failFirstOutcomeWrite.getAndDecrement() > 0) {
                            throw new IllegalStateException("Fallo de escritura posterior al proveedor simulado.");
                        }
                        try { return method.invoke(jdbcCollections, arguments); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            CollectionAssistantService serviceWithOutcomeFailure = collectionService(customerService, sales, generalDebt,
                    failingOutcomeWrite, audit, authorization, simulatedProvider, transactions, clock);
            var secondPreview = serviceWithOutcomeFailure.previewReminder(session, second.id());
            String secondConfirmation = serviceWithOutcomeFailure.confirmReminder(session, second.id(), secondPreview.previewToken());
            var persistenceUncertain = serviceWithOutcomeFailure.sendReminder(session, second.id(), secondConfirmation);
            assertEquals(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED, persistenceUncertain.status());
            assertTrue(persistenceUncertain.providerAccepted(), "El doble confirma aceptación antes del fallo de escritura.");
            assertTrue(persistenceUncertain.outcomeUncertain());
            assertEquals(CustomerCollectionReminder.ProviderEvidence.SMTP_ACCEPTED,
                    jdbcCollections.findReminder(secondCustomerId, second.weekStart()).orElseThrow().providerEvidence());
            assertEquals(2, providerCalls.get(), "El fallo de persistencia no provoca un segundo envío.");

            CollectionAssistantService restarted = collectionService(customerService, sales, generalDebt,
                    jdbcCollections, audit, authorization, simulatedProvider, transactions, clock);
            var recovered = restarted.listReminders(session, CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED);
            assertEquals(2, recovered.size());
            assertTrue(recovered.stream().anyMatch(value -> value.id() == first.id()
                    && value.attemptId().equals(uncertain.attemptId())));
            assertTrue(recovered.stream().anyMatch(value -> value.id() == second.id()
                    && value.attemptId().equals(persistenceUncertain.attemptId())));
            var retried = restarted.prepareWeeklyReminders(session);
            assertEquals(0, retried.created());
            assertEquals(0, retried.refreshed());
            assertEquals(2, providerCalls.get(), "La conciliación nunca reenvía automáticamente avisos inciertos.");
            assertEquals(AdministrativeAuditEvent.Result.UNCERTAIN,
                    audit.find(null, null, 100).stream().filter(value -> value.targetReference().equals(firstCustomerId)
                            || value.targetReference().equals(secondCustomerId))
                            .filter(value -> value.result() == AdministrativeAuditEvent.Result.UNCERTAIN)
                            .findFirst().orElseThrow().result());
        } finally {
            cleanup(provider, firstCustomerId, actor.getId(), firstCustomerCreated, actorCreated);
            cleanup(provider, secondCustomerId, actor.getId(), secondCustomerCreated, false);
        }
    }

    private static CollectionAssistantService collectionService(CustomerService customers, SalesWorkflow sales,
            CustomerGeneralDebtWorkflow generalDebt, CustomerCollectionRepository collections,
            AdministrativeAuditRepository audit, AuthorizationService authorization,
            EmailDeliveryProvider provider, TransactionBoundary transactions, Clock clock) {
        return new CollectionAssistantService(customers, sales, generalDebt, collections, audit, authorization,
                provider, new AccountStatementPdfGenerator(), new ColombiaContactWindow(Set.of(), true),
                transactions, clock, "FIXTURE-ONLY-KEY", true, true);
    }

    private static void insertInvalidReminder(JdbcConnectionProvider provider, String customerId,
                                               LocalDate week, LocalDateTime now) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO customer_collection_reminder
                      (customer_id,week_start,channel,status,balance_snapshot,generated_at,result_code)
                    VALUES (?,?,'EMAIL','NOT_A_STATUS',1.00,?,'PREPARED')
                    """)) {
                statement.setString(1, customerId);
                statement.setObject(2, week);
                statement.setObject(3, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void insertDuplicateReminder(JdbcConnectionProvider provider, String customerId,
                                                 LocalDate week, LocalDateTime now) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO customer_collection_reminder
                      (customer_id,week_start,channel,status,balance_snapshot,generated_at,result_code)
                    VALUES (?,?,'EMAIL','PENDING_REVIEW',1.00,?,'PREPARED')
                    """)) {
                statement.setString(1, customerId);
                statement.setObject(2, week);
                statement.setObject(3, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void insertReminderWithAttempt(JdbcConnectionProvider provider, String customerId,
            LocalDate week, LocalDateTime now, String status, int attemptCount, String resultCode,
            UUID attemptId, UUID attemptOwnerId) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO customer_collection_reminder
                      (customer_id,week_start,channel,status,balance_snapshot,generated_at,
                       attempt_count,result_code,attempt_id,attempt_owner_id,provider_evidence)
                    VALUES (?,?,'EMAIL',?,1.00,?,?,?,?,?,?)
                    """)) {
                statement.setString(1, customerId);
                statement.setObject(2, week);
                statement.setString(3, status);
                statement.setObject(4, now);
                statement.setInt(5, attemptCount);
                statement.setString(6, resultCode);
                statement.setObject(7, attemptId);
                statement.setObject(8, attemptOwnerId);
                statement.setString(9, "PROVIDER_REJECTED".equals(resultCode) ? "SMTP_REJECTED" : null);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void insertReminderWithInvalidEvidence(JdbcConnectionProvider provider, String customerId,
            LocalDate week, LocalDateTime now) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO customer_collection_reminder
                      (customer_id,week_start,channel,status,balance_snapshot,generated_at,
                       attempt_count,result_code,attempt_id,provider_evidence)
                    VALUES (?,?,'EMAIL','SENT',1.00,?,1,'SENT',?,'SMTP_REJECTED')
                    """)) {
                statement.setString(1, customerId);
                statement.setObject(2, week);
                statement.setObject(3, now);
                statement.setObject(4, UUID.randomUUID());
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void setFixtureBalance(JdbcConnectionProvider provider, String customerId, BigDecimal balance) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE customer SET balance=? WHERE id=?")) {
                statement.setBigDecimal(1, balance);
                statement.setString(2, customerId);
                if (statement.executeUpdate() != 1) throw new SQLException("No se actualizó el cliente fixture.");
            }
            return null;
        });
    }

    private static void cleanup(JdbcConnectionProvider provider, String customerId, Long actorId,
                                boolean customerCreated, boolean actorCreated) {
        provider.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM administrative_audit_event WHERE target_type='CUSTOMER' AND target_reference=?")) {
                statement.setString(1, customerId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM customer_collection_follow_up WHERE customer_id=?")) {
                statement.setString(1, customerId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM customer_collection_reminder WHERE customer_id=?")) {
                statement.setString(1, customerId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM customer_collection_profile WHERE customer_id=?")) {
                statement.setString(1, customerId);
                statement.executeUpdate();
            }
            if (actorCreated) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM user_account WHERE id=?")) {
                    statement.setLong(1, actorId);
                    statement.executeUpdate();
                }
            }
            if (customerCreated) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM customer WHERE id=?")) {
                    statement.setString(1, customerId);
                    statement.executeUpdate();
                }
            }
            return null;
        });
    }
}
