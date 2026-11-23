package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.AdministrativeAuditEvent;
import com.laexclusiva.model.CollectionChannel;
import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.CollectionProfile;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.jdbc.AdministrativeAuditRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerCollectionRepositoryJdbc;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.repository.jdbc.UserRepositoryJdbc;
import com.laexclusiva.security.PasswordHasher;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Prueba optativa solo para el laboratorio efímero autorizado que ya aplica V001 y V107/V108. */
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
            var first = collections.createReminderIfAbsent(proposed, preparedEvent);
            var repeated = collections.createReminderIfAbsent(proposed, preparedEvent);
            assertTrue(first.inserted());
            assertFalse(repeated.inserted());
            assertEquals(first.reminder().id(), repeated.reminder().id());
            assertEquals(2, collections.findFollowUps(customerId).size());

            assertTrue(collections.claimReminder(first.reminder(), now.plusMinutes(1)));
            assertFalse(collections.claimReminder(first.reminder(), now.plusMinutes(2)),
                    "El mismo aviso no puede reclamarse en paralelo ni repetirse.");
            CustomerCollectionReminder sent = new CustomerCollectionReminder(first.reminder().id(), customerId,
                    week, CollectionChannel.EMAIL, CustomerCollectionReminder.Status.SENT,
                    first.reminder().balanceSnapshot(), now, now.plusMinutes(1), 1,
                    CustomerCollectionReminder.ResultCode.SENT);
            collections.updateReminder(sent, CustomerCollectionReminder.Status.SENDING, 1);
            assertEquals(CustomerCollectionReminder.Status.SENT,
                    collections.findReminder(customerId, week).orElseThrow().status());
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
        } finally {
            cleanup(provider, customerId, actor.getId(), customerCreated, actorCreated);
        }
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
