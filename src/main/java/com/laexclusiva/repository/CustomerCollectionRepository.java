package com.laexclusiva.repository;

import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.CollectionProfile;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerCollectionReminder;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Puerto de perfiles optativos, bitácora de seguimiento y recordatorios idempotentes. */
public interface CustomerCollectionRepository {
    /** Indica si este proceso creó el aviso semanal o recuperó uno ya reservado. */
    record ReminderSaveResult(CustomerCollectionReminder reminder, boolean inserted, boolean refreshed) {
        public ReminderSaveResult(CustomerCollectionReminder reminder, boolean inserted) {
            this(reminder, inserted, false);
        }
    }
    /** @return preferencias guardadas o perfil sin consentimiento para un cliente histórico */
    Optional<CollectionProfile> findProfile(String customerId);

    /** @return historial en orden cronológico, sin credenciales ni contenido de correo */
    List<CollectionFollowUp> findFollowUps(String customerId);

    /** Persiste preferencias e historial bajo la misma frontera de transacción cuando es JDBC. */
    CollectionProfile saveProfile(CollectionProfile profile, List<CollectionFollowUp> events);

    /** Agrega un evento de seguimiento sin reescribir el perfil actual. */
    void recordFollowUp(CollectionFollowUp event);

    /** @return recordatorio único del cliente en la semana ISO, si ya se preparó */
    Optional<CustomerCollectionReminder> findReminder(String customerId, java.time.LocalDate weekStart);

    /** Bloquea el cliente, inserta o revalida la fila semanal y solo refresca filas sin intentos. */
    ReminderSaveResult createReminderIfAbsent(CustomerCollectionReminder reminder, CollectionFollowUp preparedEvent);

    /** Reclama un aviso pendiente mediante comparación atómica antes de abrir la conexión SMTP. */
    boolean claimReminder(CustomerCollectionReminder reminder, UUID attemptId, UUID attemptOwnerId,
                          LocalDateTime attemptedAt, BigDecimal expectedBalance, Customer expectedCustomer,
                          CollectionProfile expectedProfile);

    /** Compatibilidad para pruebas antiguas; una reclamación requiere identidad efímera del intento. */
    default boolean claimReminder(CustomerCollectionReminder reminder, LocalDateTime attemptedAt) {
        return claimReminder(reminder, UUID.randomUUID(), UUID.randomUUID(), attemptedAt,
                reminder.balanceSnapshot(), null, null);
    }

    /** Convierte intentos SENDING de otra instancia a conciliación, sin volver a enviarlos. */
    int reconcileInterruptedAttempts(UUID currentInstanceId);

    /** Actualiza el resultado solo si el estado y el contador coinciden con la lectura esperada. */
    void updateReminder(CustomerCollectionReminder reminder,
                        CustomerCollectionReminder.Status expectedStatus, int expectedAttemptCount);

    /** @return avisos pendientes de revisión u otro estado, por fecha y cliente */
    List<CustomerCollectionReminder> findReminders(CustomerCollectionReminder.Status status);
}
