package com.laexclusiva.repository;

import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.CollectionProfile;
import com.laexclusiva.model.CustomerCollectionReminder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Implementación en memoria para modo demo y pruebas sin PostgreSQL. */
public final class InMemoryCustomerCollectionRepository implements CustomerCollectionRepository {
    private final Map<String, CollectionProfile> profiles = new HashMap<>();
    private final Map<String, List<CollectionFollowUp>> history = new HashMap<>();
    private final Map<String, CustomerCollectionReminder> reminders = new HashMap<>();
    private long nextEventId = 1;
    private long nextReminderId = 1;

    @Override public synchronized Optional<CollectionProfile> findProfile(String customerId) {
        return Optional.ofNullable(profiles.get(customerId));
    }

    @Override public synchronized List<CollectionFollowUp> findFollowUps(String customerId) {
        return List.copyOf(history.getOrDefault(customerId, List.of()));
    }

    @Override public synchronized CollectionProfile saveProfile(CollectionProfile profile, List<CollectionFollowUp> events) {
        profiles.put(profile.customerId(), profile);
        if (events != null) events.forEach(this::addEvent);
        return profile;
    }

    @Override public synchronized void recordFollowUp(CollectionFollowUp event) {
        if (event == null) throw new IllegalArgumentException("El evento es obligatorio.");
        addEvent(event);
    }

    @Override public synchronized Optional<CustomerCollectionReminder> findReminder(String customerId, LocalDate weekStart) {
        return Optional.ofNullable(reminders.get(reminderKey(customerId, weekStart)));
    }

    @Override public synchronized ReminderSaveResult createReminderIfAbsent(CustomerCollectionReminder reminder,
                                                                              CollectionFollowUp preparedEvent) {
        String key = reminderKey(reminder.customerId(), reminder.weekStart());
        CustomerCollectionReminder existing = reminders.get(key);
        if (existing != null) return new ReminderSaveResult(existing, false);
        CustomerCollectionReminder saved = withReminderId(reminder, nextReminderId++);
        reminders.put(key, saved);
        if (preparedEvent != null) addEvent(preparedEvent);
        return new ReminderSaveResult(saved, true);
    }

    @Override public synchronized boolean claimReminder(CustomerCollectionReminder reminder,
                                                        java.time.LocalDateTime attemptedAt) {
        String key = reminderKey(reminder.customerId(), reminder.weekStart());
        CustomerCollectionReminder current = reminders.get(key);
        if (current == null || current.id() != reminder.id()
                || current.status() != CustomerCollectionReminder.Status.PENDING_REVIEW
                || current.attemptCount() != reminder.attemptCount()) return false;
        reminders.put(key, new CustomerCollectionReminder(current.id(), current.customerId(), current.weekStart(),
                current.channel(), CustomerCollectionReminder.Status.SENDING, current.balanceSnapshot(),
                current.generatedAt(), attemptedAt, current.attemptCount() + 1,
                CustomerCollectionReminder.ResultCode.PREPARED));
        return true;
    }

    @Override public synchronized void updateReminder(CustomerCollectionReminder reminder,
            CustomerCollectionReminder.Status expectedStatus, int expectedAttemptCount) {
        String key = reminderKey(reminder.customerId(), reminder.weekStart());
        CustomerCollectionReminder current = reminders.get(key);
        if (current == null || current.id() != reminder.id() || current.status() != expectedStatus
                || current.attemptCount() != expectedAttemptCount) {
            throw new java.util.ConcurrentModificationException("El recordatorio cambió; vuelva a consultarlo.");
        }
        reminders.put(key, reminder);
    }

    @Override public synchronized List<CustomerCollectionReminder> findReminders(CustomerCollectionReminder.Status status) {
        return reminders.values().stream()
                .filter(reminder -> status == null || reminder.status() == status)
                .sorted(Comparator.comparing(CustomerCollectionReminder::weekStart)
                        .thenComparing(CustomerCollectionReminder::customerId))
                .toList();
    }

    private void addEvent(CollectionFollowUp event) {
        if (event == null) return;
        CollectionFollowUp saved = new CollectionFollowUp(nextEventId++, event.customerId(), event.actorUserId(),
                event.eventType(), event.commitmentDate(), event.balanceSnapshot(), event.note(), event.occurredAt());
        history.computeIfAbsent(event.customerId(), ignored -> new ArrayList<>()).add(saved);
    }

    private static String reminderKey(String customerId, LocalDate weekStart) {
        return customerId + "@" + weekStart;
    }

    private static CustomerCollectionReminder withReminderId(CustomerCollectionReminder source, long id) {
        return new CustomerCollectionReminder(id, source.customerId(), source.weekStart(), source.channel(),
                source.status(), source.balanceSnapshot(), source.generatedAt(), source.lastAttemptAt(),
                source.attemptCount(), source.resultCode());
    }
}
