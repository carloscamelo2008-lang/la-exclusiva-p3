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
                    + " WHERE customer_id = ? AND week_start = ?")) {
                statement.setString(1, reminder.customerId());
                statement.setObject(2, reminder.weekStart());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new java.sql.SQLException("No se pudo reservar el recordatorio semanal.");
                    return new ReminderSaveResult(reminder(rows), inserted);
                }
            }
        }));
    }

    @Override public boolean claimReminder(CustomerCollectionReminder reminder, LocalDateTime attemptedAt) {
        if (reminder == null || reminder.id() <= 0 || attemptedAt == null) {
            throw new IllegalArgumentException("El recordatorio y la fecha de intento son obligatorios.");
        }
        return connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE customer_collection_reminder
                    SET status='SENDING',last_attempt_at=?,attempt_count=attempt_count+1,result_code='PREPARED'
                    WHERE id=? AND customer_id=? AND week_start=? AND status='PENDING_REVIEW' AND attempt_count=?
                    """)) {
                statement.setObject(1, attemptedAt);
                statement.setLong(2, reminder.id());
                statement.setString(3, reminder.customerId());
                statement.setObject(4, reminder.weekStart());
                statement.setInt(5, reminder.attemptCount());
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override public void updateReminder(CustomerCollectionReminder reminder,
            CustomerCollectionReminder.Status expectedStatus, int expectedAttemptCount) {
        if (reminder == null || reminder.id() <= 0 || expectedStatus == null || expectedAttemptCount < 0) {
            throw new IllegalArgumentException("El estado persistido y su versión son obligatorios.");
        }
        connections.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE customer_collection_reminder
                    SET status=?,last_attempt_at=?,attempt_count=?,result_code=?
                    WHERE id=? AND customer_id=? AND week_start=? AND status=? AND attempt_count=?
                    """)) {
                statement.setString(1, reminder.status().name());
                statement.setObject(2, reminder.lastAttemptAt());
                statement.setInt(3, reminder.attemptCount());
                statement.setString(4, reminder.resultCode().name());
                statement.setLong(5, reminder.id());
                statement.setString(6, reminder.customerId());
                statement.setObject(7, reminder.weekStart());
                statement.setString(8, expectedStatus.name());
                statement.setInt(9, expectedAttemptCount);
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
                rows.getInt("attempt_count"), CustomerCollectionReminder.ResultCode.valueOf(rows.getString("result_code")));
    }

    private static String reminderSelect() {
        return "SELECT id,customer_id,week_start,channel,status,balance_snapshot,generated_at,last_attempt_at,attempt_count,result_code FROM customer_collection_reminder";
    }

    private static void requireCustomerId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("El ID del cliente es obligatorio.");
    }
}
