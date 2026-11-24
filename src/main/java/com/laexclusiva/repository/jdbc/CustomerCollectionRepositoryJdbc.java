package com.laexclusiva.repository.jdbc;

import com.laexclusiva.model.CollectionChannel;
import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.CollectionProfile;
import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcTransactionManager;
import com.laexclusiva.repository.CustomerCollectionRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JDBC de preferencias y recordatorios con unicidad por cliente y semana ISO. */
public final class CustomerCollectionRepositoryJdbc implements CustomerCollectionRepository {
    private final JdbcConnectionProvider connections;
    private final JdbcTransactionManager transactions;

    /** Comparte el proveedor y coordinador PostgreSQL con los flujos financieros de la aplicación. */
    public CustomerCollectionRepositoryJdbc(JdbcConnectionProvider connections, JdbcTransactionManager transactions) {
        if (connections == null || transactions == null) {
            throw new IllegalArgumentException("El proveedor JDBC y su coordinador son obligatorios.");
        }
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override public Optional<CollectionProfile> findProfile(String customerId) {
        requireCustomerId(customerId);
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT customer_id,email,authorized_channel,collection_consent,consent_recorded_at,
                           reminders_enabled,commitment_date,observed_balance,observed_at
                    FROM customer_collection_profile WHERE customer_id = ?
                    """)) {
                statement.setString(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(profile(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override public List<CollectionFollowUp> findFollowUps(String customerId) {
        requireCustomerId(customerId);
        return connections.withConnection(connection -> {
            List<CollectionFollowUp> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id,customer_id,actor_user_id,event_type,commitment_date,balance_snapshot,note,occurred_at
                    FROM customer_collection_follow_up WHERE customer_id = ? ORDER BY occurred_at,id
                    """)) {
                statement.setString(1, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) events.add(new CollectionFollowUp(rows.getLong("id"),
                            rows.getString("customer_id"), rows.getLong("actor_user_id"),
                            CollectionFollowUp.EventType.valueOf(rows.getString("event_type")),
                            rows.getObject("commitment_date", LocalDate.class), rows.getBigDecimal("balance_snapshot"),
                            rows.getString("note"), rows.getObject("occurred_at", LocalDateTime.class)));
                }
            }
            return events;
        });
    }

    @Override public CollectionProfile saveProfile(CollectionProfile profile, List<CollectionFollowUp> events) {
        if (profile == null || events == null || events.stream().anyMatch(event -> event == null
                || !profile.customerId().equals(event.customerId()))) {
            throw new IllegalArgumentException("El perfil y su historial deben corresponder al mismo cliente.");
        }
        return transactions.inTransaction(() -> connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO customer_collection_profile
                      (customer_id,email,authorized_channel,collection_consent,consent_recorded_at,
                       reminders_enabled,commitment_date,observed_balance,observed_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (customer_id) DO UPDATE SET
                      email=EXCLUDED.email, authorized_channel=EXCLUDED.authorized_channel,
                      collection_consent=EXCLUDED.collection_consent,
                      consent_recorded_at=EXCLUDED.consent_recorded_at,
                      reminders_enabled=EXCLUDED.reminders_enabled, commitment_date=EXCLUDED.commitment_date,
                      observed_balance=EXCLUDED.observed_balance, observed_at=EXCLUDED.observed_at,
                      updated_at=EXCLUDED.updated_at
                    """)) {
                statement.setString(1, profile.customerId());
                statement.setString(2, profile.email());
                statement.setString(3, profile.authorizedChannel().name());
                statement.setBoolean(4, profile.collectionConsent());
                statement.setObject(5, profile.consentRecordedAt());
                statement.setBoolean(6, profile.remindersEnabled());
                statement.setObject(7, profile.commitmentDate());
                statement.setBigDecimal(8, profile.observedBalance());
                statement.setObject(9, profile.observedAt());
                statement.setObject(10, LocalDateTime.now());
                if (statement.executeUpdate() != 1) throw new java.sql.SQLException("No se guardó el perfil.");
            }
            for (CollectionFollowUp event : events) insertFollowUp(connection, event);
            return profile;
        }));
    }

    @Override public void recordFollowUp(CollectionFollowUp event) {
        if (event == null) throw new IllegalArgumentException("El evento es obligatorio.");
        transactions.inTransaction(() -> connections.withConnection(connection -> {
            insertFollowUp(connection, event);
            return null;
        }));
    }

    @Override public Optional<CustomerCollectionReminder> findReminder(String customerId, LocalDate weekStart) {
        requireCustomerId(customerId);
        if (weekStart == null) throw new IllegalArgumentException("La semana es obligatoria.");
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(reminderSelect()
                    + " WHERE customer_id = ? AND week_start = ?")) {
                statement.setString(1, customerId);
                statement.setObject(2, weekStart);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(reminder(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override public ReminderSaveResult createReminderIfAbsent(CustomerCollectionReminder reminder,
                                                                CollectionFollowUp preparedEvent) {
        if (reminder == null || preparedEvent == null || !reminder.customerId().equals(preparedEvent.customerId())
                || preparedEvent.eventType() != CollectionFollowUp.EventType.REMINDER_PREPARED) {
            throw new IllegalArgumentException("El recordatorio y su evento de preparación deben corresponder.");
        }
        return transactions.inTransaction(() -> connections.withConnection(connection -> {
            java.math.BigDecimal currentBalance;
            try (PreparedStatement lockCustomer = connection.prepareStatement(
                    "SELECT balance FROM customer WHERE id=? FOR UPDATE")) {
                lockCustomer.setString(1, reminder.customerId());
                try (ResultSet rows = lockCustomer.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("El cliente del aviso ya no existe.");
                    currentBalance = rows.getBigDecimal(1);
                }
            }
            if (currentBalance == null || currentBalance.compareTo(reminder.balanceSnapshot()) != 0) {
                throw new java.util.ConcurrentModificationException(
                        "El saldo cambió durante la preparación; vuelva a actualizar los avisos.");
            }
            boolean inserted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO customer_collection_reminder
                      (customer_id,week_start,channel,status,balance_snapshot,generated_at,
                       last_attempt_at,attempt_count,result_code)
                    VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (customer_id,week_start) DO NOTHING
                    """)) {
                statement.setString(1, reminder.customerId());
                statement.setObject(2, reminder.weekStart());
                statement.setString(3, reminder.channel().name());
                statement.setString(4, reminder.status().name());
                statement.setBigDecimal(5, reminder.balanceSnapshot());
                statement.setObject(6, reminder.generatedAt());
                statement.setObject(7, reminder.lastAttemptAt());
                statement.setInt(8, reminder.attemptCount());
                statement.setString(9, reminder.resultCode().name());
                inserted = statement.executeUpdate() == 1;
            }
            if (inserted) insertFollowUp(connection, preparedEvent);
            try (PreparedStatement statement = connection.prepareStatement(reminderSelect()
                    + " WHERE customer_id = ? AND week_start = ? FOR UPDATE")) {
                statement.setString(1, reminder.customerId());
                statement.setObject(2, reminder.weekStart());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new java.sql.SQLException("No se pudo reservar el recordatorio semanal.");
                    CustomerCollectionReminder current = reminder(rows);
                    if (inserted) return new ReminderSaveResult(current, true, false);
                    boolean refreshable = current.attemptCount() == 0
                            && (current.status() == CustomerCollectionReminder.Status.PENDING_REVIEW
                            || (current.status() == CustomerCollectionReminder.Status.FAILED
                            && current.resultCode() == CustomerCollectionReminder.ResultCode.BALANCE_CHANGED));
                    if (!refreshable || current.balanceSnapshot().compareTo(reminder.balanceSnapshot()) == 0) {
                        return new ReminderSaveResult(current, false, false);
                    }
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE customer_collection_reminder
                            SET channel=?,status='PENDING_REVIEW',balance_snapshot=?,generated_at=?,
                                last_attempt_at=NULL,attempt_count=0,result_code='PREPARED',
                                attempt_id=NULL,attempt_owner_id=NULL,provider_evidence=NULL
                            WHERE id=? AND status=? AND attempt_count=0 AND balance_snapshot=?
                            """)) {
                        update.setString(1, reminder.channel().name());
                        update.setBigDecimal(2, reminder.balanceSnapshot());
                        update.setObject(3, reminder.generatedAt());
                        update.setLong(4, current.id());
                        update.setString(5, current.status().name());
                        update.setBigDecimal(6, current.balanceSnapshot());
                        if (update.executeUpdate() != 1) {
                            throw new java.util.ConcurrentModificationException("El aviso cambió; vuelva a consultarlo.");
                        }
                    }
                    insertFollowUp(connection, new CollectionFollowUp(0, preparedEvent.customerId(),
                            preparedEvent.actorUserId(), CollectionFollowUp.EventType.BALANCE_CHANGED,
                            preparedEvent.commitmentDate(), reminder.balanceSnapshot(),
                            "El saldo cambió antes del primer intento; el mismo aviso semanal requiere una nueva revisión.",
                            preparedEvent.occurredAt()));
                    CustomerCollectionReminder refreshed = new CustomerCollectionReminder(current.id(),
                            current.customerId(), current.weekStart(), reminder.channel(),
                            CustomerCollectionReminder.Status.PENDING_REVIEW, reminder.balanceSnapshot(),
                            reminder.generatedAt(), null, 0, CustomerCollectionReminder.ResultCode.PREPARED);
                    return new ReminderSaveResult(refreshed, false, true);
                }
            }
        }));
    }

    @Override public boolean claimReminder(CustomerCollectionReminder reminder, UUID attemptId,
            UUID attemptOwnerId, LocalDateTime attemptedAt, java.math.BigDecimal expectedBalance,
            com.laexclusiva.model.Customer expectedCustomer, CollectionProfile expectedProfile) {
        if (reminder == null || reminder.id() <= 0 || attemptId == null || attemptOwnerId == null
                || attemptedAt == null || expectedBalance == null) {
            throw new IllegalArgumentException("El recordatorio y la fecha de intento son obligatorios.");
        }
        return transactions.inTransaction(() -> connections.withConnection(connection -> {
            java.math.BigDecimal persistedBalance;
            try (PreparedStatement lockCustomer = connection.prepareStatement(
                    "SELECT balance FROM customer WHERE id=? FOR UPDATE")) {
                lockCustomer.setString(1, reminder.customerId());
                try (ResultSet rows = lockCustomer.executeQuery()) {
                    if (!rows.next()) return false;
                    persistedBalance = rows.getBigDecimal(1);
                }
            }
            if (persistedBalance == null || persistedBalance.compareTo(expectedBalance) != 0
                    || persistedBalance.compareTo(reminder.balanceSnapshot()) != 0) return false;
            if (expectedCustomer != null) {
                try (PreparedStatement readCustomer = connection.prepareStatement(
                        "SELECT name,phone,address FROM customer WHERE id=?")) {
                    readCustomer.setString(1, reminder.customerId());
                    try (ResultSet rows = readCustomer.executeQuery()) {
                        if (!rows.next() || !java.util.Objects.equals(rows.getString("name"), expectedCustomer.getName())
                                || !java.util.Objects.equals(rows.getString("phone"), expectedCustomer.getPhone())
                                || !java.util.Objects.equals(rows.getString("address"), expectedCustomer.getAddress())) return false;
                    }
                }
            }
            if (expectedProfile != null) {
                CollectionProfile currentProfile;
                try (PreparedStatement lockProfile = connection.prepareStatement("""
                        SELECT customer_id,email,authorized_channel,collection_consent,consent_recorded_at,
                               reminders_enabled,commitment_date,observed_balance,observed_at
                        FROM customer_collection_profile WHERE customer_id=? FOR UPDATE
                        """)) {
                    lockProfile.setString(1, reminder.customerId());
                    try (ResultSet rows = lockProfile.executeQuery()) {
                        if (!rows.next()) return false;
                        currentProfile = profile(rows);
                    }
                }
                if (!expectedProfile.equals(currentProfile)) return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE customer_collection_reminder
                    SET status='SENDING',last_attempt_at=?,attempt_count=attempt_count+1,result_code='PREPARED',
                        attempt_id=?,attempt_owner_id=?,provider_evidence=NULL
                    WHERE id=? AND customer_id=? AND week_start=? AND status='PENDING_REVIEW'
                      AND attempt_count=? AND balance_snapshot=?
                    """)) {
                statement.setObject(1, attemptedAt);
                statement.setObject(2, attemptId);
                statement.setObject(3, attemptOwnerId);
                statement.setLong(4, reminder.id());
                statement.setString(5, reminder.customerId());
                statement.setObject(6, reminder.weekStart());
                statement.setInt(7, reminder.attemptCount());
                statement.setBigDecimal(8, reminder.balanceSnapshot());
                return statement.executeUpdate() == 1;
            }
        }));
    }

    @Override public int reconcileInterruptedAttempts(UUID currentInstanceId) {
        if (currentInstanceId == null) throw new IllegalArgumentException("La instancia es obligatoria.");
        return transactions.inTransaction(() -> connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE customer_collection_reminder
                    SET status='RECONCILIATION_REQUIRED',result_code='PROVIDER_OUTCOME_UNCERTAIN',
                        attempt_owner_id=NULL,provider_evidence='SMTP_OUTCOME_UNCERTAIN'
                    WHERE status='SENDING' AND attempt_owner_id IS DISTINCT FROM ?
                    """)) {
                statement.setObject(1, currentInstanceId);
                return statement.executeUpdate();
            }
        }));
    }

    @Override public void updateReminder(CustomerCollectionReminder reminder,
            CustomerCollectionReminder.Status expectedStatus, int expectedAttemptCount) {
        if (reminder == null || reminder.id() <= 0 || expectedStatus == null || expectedAttemptCount < 0) {
            throw new IllegalArgumentException("El estado persistido y su versión son obligatorios.");
        }
        connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE customer_collection_reminder
                    SET status=?,last_attempt_at=?,attempt_count=?,result_code=?,attempt_id=?,attempt_owner_id=?,
                        provider_evidence=?
                    WHERE id=? AND customer_id=? AND week_start=? AND status=? AND attempt_count=?
                    """)) {
                statement.setString(1, reminder.status().name());
                statement.setObject(2, reminder.lastAttemptAt());
                statement.setInt(3, reminder.attemptCount());
                statement.setString(4, reminder.resultCode().name());
                statement.setObject(5, reminder.attemptId());
                statement.setObject(6, reminder.attemptOwnerId());
                statement.setString(7, reminder.providerEvidence() == null ? null : reminder.providerEvidence().name());
                statement.setLong(8, reminder.id());
                statement.setString(9, reminder.customerId());
                statement.setObject(10, reminder.weekStart());
                statement.setString(11, expectedStatus.name());
                statement.setInt(12, expectedAttemptCount);
                if (statement.executeUpdate() != 1) {
                    throw new java.util.ConcurrentModificationException("El recordatorio cambió; vuelva a consultarlo.");
                }
            }
            return null;
        });
    }

    @Override public List<CustomerCollectionReminder> findReminders(CustomerCollectionReminder.Status status) {
        return connections.withConnection(connection -> {
            List<CustomerCollectionReminder> values = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(reminderSelect()
                    + (status == null ? " ORDER BY week_start DESC,customer_id" : " WHERE status = ? ORDER BY week_start DESC,customer_id"))) {
                if (status != null) statement.setString(1, status.name());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) values.add(reminder(rows));
                }
            }
            return values;
        });
    }

    private static void insertFollowUp(java.sql.Connection connection, CollectionFollowUp event) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO customer_collection_follow_up
                  (customer_id,actor_user_id,event_type,commitment_date,balance_snapshot,note,occurred_at)
                VALUES (?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, event.customerId());
            statement.setLong(2, event.actorUserId());
            statement.setString(3, event.eventType().name());
            statement.setObject(4, event.commitmentDate());
            statement.setBigDecimal(5, event.balanceSnapshot());
            statement.setString(6, event.note());
            statement.setObject(7, event.occurredAt());
            if (statement.executeUpdate() != 1) throw new java.sql.SQLException("No se guardó el seguimiento.");
        }
    }

    private static CollectionProfile profile(ResultSet rows) throws java.sql.SQLException {
        return new CollectionProfile(rows.getString("customer_id"), rows.getString("email"),
                CollectionChannel.valueOf(rows.getString("authorized_channel")), rows.getBoolean("collection_consent"),
                rows.getObject("consent_recorded_at", LocalDateTime.class), rows.getBoolean("reminders_enabled"),
                rows.getObject("commitment_date", LocalDate.class), rows.getBigDecimal("observed_balance"),
                rows.getObject("observed_at", LocalDateTime.class));
    }

    private static CustomerCollectionReminder reminder(ResultSet rows) throws java.sql.SQLException {
        return new CustomerCollectionReminder(rows.getLong("id"), rows.getString("customer_id"),
                rows.getObject("week_start", LocalDate.class), CollectionChannel.valueOf(rows.getString("channel")),
                CustomerCollectionReminder.Status.valueOf(rows.getString("status")), rows.getBigDecimal("balance_snapshot"),
                rows.getObject("generated_at", LocalDateTime.class), rows.getObject("last_attempt_at", LocalDateTime.class),
                rows.getInt("attempt_count"), CustomerCollectionReminder.ResultCode.valueOf(rows.getString("result_code")),
                rows.getObject("attempt_id", UUID.class), rows.getObject("attempt_owner_id", UUID.class),
                rows.getString("provider_evidence") == null ? null
                        : CustomerCollectionReminder.ProviderEvidence.valueOf(rows.getString("provider_evidence")));
    }

    private static String reminderSelect() {
        return "SELECT id,customer_id,week_start,channel,status,balance_snapshot,generated_at,last_attempt_at,attempt_count,result_code,attempt_id,attempt_owner_id,provider_evidence FROM customer_collection_reminder";
    }

    private static void requireCustomerId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("El ID del cliente es obligatorio.");
    }
}
