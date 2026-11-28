package com.laexclusiva.service;

import com.laexclusiva.model.AdministrativeAuditEvent;
import com.laexclusiva.model.CollectionChannel;
import com.laexclusiva.model.CollectionDashboard;
import com.laexclusiva.model.CollectionFollowUp;
import com.laexclusiva.model.CollectionProfile;
import com.laexclusiva.model.CollectionRecommendation;
import com.laexclusiva.model.Customer;
import com.laexclusiva.model.CustomerCollectionReminder;
import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.GeneralDebtMovement;
import com.laexclusiva.model.GeneralDebtMovementType;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.UserSession;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.AdministrativeAuditRepository;
import com.laexclusiva.repository.CustomerCollectionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordina recomendaciones explicables, seguimiento, estados comerciales y avisos revisables.
 * No utiliza aprendizaje automático ni confirma pagos: toda escritura requiere permiso vigente,
 * y el envío SMTP solo se ejecuta tras validar consentimiento, ventana local y reserva semanal.
 */
public final class CollectionAssistantService {
    public static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final String BUSINESS_NAME = "La Exclusiva";
    private static final Duration REVIEW_TOKEN_LIFETIME = Duration.ofMinutes(5);

    /** Resumen de una preparación idempotente de avisos para una semana ISO. */
    public record PreparationResult(int created, int refreshed, List<CustomerCollectionReminder> pendingReview) {
        public PreparationResult { pendingReview = List.copyOf(pendingReview); }
        public PreparationResult(int created, List<CustomerCollectionReminder> pendingReview) {
            this(created, 0, pendingReview);
        }
    }

    /** Vista previa efímera; nunca se persiste ni registra el correo, el PDF o su token. */
    public record EmailPreview(String recipient, String subject, String text, byte[] pdf, String previewToken) {
        public EmailPreview {
            if (recipient == null || subject == null || text == null || pdf == null || previewToken == null) {
                throw new IllegalArgumentException("La vista previa requiere destinatario y documento.");
            }
            pdf = pdf.clone();
        }
        @Override public byte[] pdf() { return pdf.clone(); }
        @Override public String toString() { return "EmailPreview[contenido y token protegidos]"; }
    }

    /** Resultado seguro que identifica un intento sin incluir destinatario ni contenido. */
    public record DeliveryResult(CustomerCollectionReminder.Status status,
                                 CustomerCollectionReminder.ResultCode resultCode,
                                 boolean providerAccepted, boolean outcomeUncertain, String message,
                                 UUID attemptId) { }

    private final CustomerService customers;
    private final SalesWorkflow sales;
    private final CustomerGeneralDebtWorkflow generalDebt;
    private final CustomerCollectionRepository collections;
    private final AdministrativeAuditRepository audit;
    private final AuthorizationService authorization;
    private final EmailDeliveryProvider emailProvider;
    private final AccountStatementPdfGenerator pdfGenerator;
    private final ColombiaContactWindow contactWindow;
    private final TransactionBoundary transactions;
    private final Clock clock;
    private final String paymentKey;
    private final boolean paymentKeyOwnerConfirmed;
    private final boolean paymentKeyVerifiedByAdministrator;
    private final UUID instanceId = UUID.randomUUID();
    private final SecureRandom tokenRandom = new SecureRandom();
    private final Map<String, ReviewBinding> pendingPreviews = new ConcurrentHashMap<>();
    private final Map<String, ReviewBinding> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * Construye el servicio con las dependencias de la composición seleccionada.
     *
     * @param customers fuente de clientes del modo activo
     * @param sales fuente de ventas y pagos históricos
     * @param generalDebt libro de deuda general, separado de las ventas
     * @param collections preferencias y avisos con unicidad cliente/semana
     * @param audit bitácora administrativa sin secretos
     * @param authorization autoridad de sesiones y permisos vigentes
     * @param emailProvider transporte simulado o SMTP TLS explícito
     * @param pdfGenerator generador efímero de estados comerciales
     * @param contactWindow política local de contacto y festivos
     * @param transactions frontera compartida para lecturas consistentes y escrituras JDBC
     * @param clock reloj inyectable, interpretado siempre en America/Bogota
     * @param paymentKey llave configurada del negocio, si existe
     * @param paymentKeyOwnerConfirmed confirmación del titular por administración
     * @param paymentKeyVerifiedByAdministrator confirmación de que la llave se verificó
     */
    public CollectionAssistantService(CustomerService customers, SalesWorkflow sales,
            CustomerGeneralDebtWorkflow generalDebt, CustomerCollectionRepository collections,
            AdministrativeAuditRepository audit, AuthorizationService authorization,
            EmailDeliveryProvider emailProvider, AccountStatementPdfGenerator pdfGenerator,
            ColombiaContactWindow contactWindow, TransactionBoundary transactions, Clock clock,
            String paymentKey, boolean paymentKeyOwnerConfirmed,
            boolean paymentKeyVerifiedByAdministrator) {
        if (customers == null || sales == null || generalDebt == null || collections == null || audit == null
                || authorization == null || emailProvider == null || pdfGenerator == null || contactWindow == null
                || transactions == null || clock == null) {
            throw new IllegalArgumentException("Las dependencias del asistente de cartera son obligatorias.");
        }
        authorization.requireSessionAuthority();
        this.customers = customers;
        this.sales = sales;
        this.generalDebt = generalDebt;
        this.collections = collections;
        this.audit = audit;
        this.authorization = authorization;
        this.emailProvider = emailProvider;
        this.pdfGenerator = pdfGenerator;
        this.contactWindow = contactWindow;
        this.transactions = transactions;
        this.clock = clock;
        this.paymentKey = paymentKey == null || paymentKey.isBlank() ? null : paymentKey.trim();
        this.paymentKeyOwnerConfirmed = paymentKeyOwnerConfirmed;
        this.paymentKeyVerifiedByAdministrator = paymentKeyVerifiedByAdministrator;
    }

    /**
     * Calcula contadores y prioridades usando saldos vigentes y fechas documentadas.
     * Los trabajadores solo reciben los datos necesarios para operar la Cartera.
     *
     * @param actor sesión que conserva VIEW_PORTFOLIO
     * @return tablero ordenado por urgencia y explicado en lenguaje operativo
     */
    public CollectionDashboard dashboard(UserSession actor) {
        return authorization.execute(actor, Permission.VIEW_PORTFOLIO,
                () -> transactions.inReadOnlyRepeatableRead(this::loadDashboard));
    }

    /** Registra datos de contacto opcionales, consentimiento expreso y compromiso de pago. */
    public CollectionProfile saveProfile(UserSession actor, String customerId, String email,
            CollectionChannel requestedChannel, boolean consent, boolean remindersEnabled,
            LocalDate commitmentDate, String note) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION, () -> {
            var principal = authorization.currentPrincipal(actor);
            Customer customer = customers.getCustomerById(requireCustomerId(customerId));
            CustomerDebtBreakdown balance = currentBalance(customer,
                    sales.listAll().stream().filter(sale -> belongsTo(sale, customerId)).toList());
            requireConsistent(balance);
            CollectionProfile previous = collections.findProfile(customerId)
                    .orElseGet(() -> CollectionProfile.empty(customerId));
            String normalizedNote = normalizeNote(note);
            CollectionChannel channel = consent ? requestedChannel : CollectionChannel.NONE;
            if (channel == null) throw new IllegalArgumentException("Seleccione el canal autorizado.");
            if (consent && channel == CollectionChannel.NONE) {
                throw new IllegalArgumentException("El consentimiento requiere un canal autorizado.");
            }
            boolean enabledReminders = consent && remindersEnabled;
            String normalizedEmail = consent && channel == CollectionChannel.EMAIL ? normalizeEmail(email) : null;
            if (channel == CollectionChannel.EMAIL && normalizedEmail == null) {
                throw new IllegalArgumentException("El canal email requiere una dirección válida.");
            }
            LocalDateTime now = LocalDateTime.now(clock.withZone(COLOMBIA));
            boolean consentChanged = previous.collectionConsent() != consent
                    || previous.authorizedChannel() != channel
                    || previous.remindersEnabled() != enabledReminders
                    || !java.util.Objects.equals(previous.email(), normalizedEmail);
            LocalDateTime consentAt = !consent ? null : consentChanged || previous.consentRecordedAt() == null
                    ? now : previous.consentRecordedAt();
            CollectionProfile profile = new CollectionProfile(customerId, normalizedEmail, channel, consent,
                    consentAt, enabledReminders, commitmentDate, balance.aggregateBalance(), now);
            List<CollectionFollowUp> events = new ArrayList<>();
            Long actorId = principal.userId();
            if (!java.util.Objects.equals(previous.commitmentDate(), commitmentDate)) {
                String detail = commitmentDate == null ? "Compromiso retirado."
                        : "Compromiso de pago registrado o actualizado.";
                events.add(event(customerId, actorId, CollectionFollowUp.EventType.COMMITMENT_SET,
                        commitmentDate, balance.aggregateBalance(), detail, now));
            }
            if (consentChanged) {
                String detail = consent ? "Autorización de contacto registrada o actualizada."
                        : "Autorización de contacto revocada; se desactivaron los recordatorios.";
                events.add(event(customerId, actorId, CollectionFollowUp.EventType.CONSENT_CHANGED,
                        null, balance.aggregateBalance(), detail, now));
            }
            if (previous.observedBalance() != null
                    && previous.observedBalance().compareTo(balance.aggregateBalance()) != 0) {
                events.add(event(customerId, actorId, CollectionFollowUp.EventType.BALANCE_CHANGED,
                        null, balance.aggregateBalance(), "El saldo difiere de la observación anterior.", now));
            }
            if (normalizedNote != null) {
                events.add(event(customerId, actorId, CollectionFollowUp.EventType.NOTE_ADDED,
                        commitmentDate, balance.aggregateBalance(), normalizedNote, now));
            }
            CollectionProfile saved = collections.saveProfile(profile, events);
            invalidateReviews(customerId);
            return saved;
        });
    }

    /** Consulta la bitácora de seguimiento de un cliente, accesible solo a administración. */
    public List<CollectionFollowUp> history(UserSession actor, String customerId) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION,
                () -> collections.findFollowUps(requireCustomerId(customerId)));
    }

    /** Devuelve perfil optativo o valores vacíos; el correo solo se entrega a administración. */
    public CollectionProfile profile(UserSession actor, String customerId) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION,
                () -> collections.findProfile(requireCustomerId(customerId))
                        .orElseGet(() -> CollectionProfile.empty(customerId)));
    }

    /** Devuelve el PDF comercial actual sin guardarlo en el repositorio ni en el historial. */
    public byte[] statementPdf(UserSession actor, String customerId) {
        return authorization.execute(actor, Permission.VIEW_PORTFOLIO,
                () -> transactions.inReadOnlyRepeatableRead(() -> pdfGenerator.generate(createStatement(customerId))));
    }

    /** Prepara un archivo y un correo revisables por un administrador antes de cualquier entrega. */
    public EmailPreview previewReminder(UserSession actor, long reminderId) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION,
                () -> {
                    var principal = authorization.currentPrincipal(actor);
                    ReviewSnapshot snapshot = transactions.inReadOnlyRepeatableRead(
                            () -> loadReviewSnapshot(reminderId));
                    if (!balanceMatches(snapshot)) {
                        markBalanceChanged(snapshot, principal.userId());
                        throw new IllegalStateException("El saldo cambió; actualice el aviso semanal antes de revisarlo.");
                    }
                    String previewToken = issueToken(pendingPreviews,
                            binding(snapshot, actor, principal, snapshot.content().pdf()));
                    return new EmailPreview(snapshot.content().recipient(), snapshot.content().subject(),
                            snapshot.content().text(), snapshot.content().pdf(), previewToken);
                });
    }

    /** Confirma la vista previa vigente y devuelve un token de envío único de cinco minutos. */
    public String confirmReminder(UserSession actor, long reminderId, String previewToken) {
        try {
            return authorization.execute(actor, Permission.MANAGE_COLLECTION, () -> {
                var principal = authorization.currentPrincipal(actor);
                ReviewBinding reviewed = consumeToken(pendingPreviews, actor, principal, reminderId, previewToken);
                ReviewSnapshot current = transactions.inReadOnlyRepeatableRead(() -> loadReviewSnapshot(reminderId));
                if (!sameContent(reviewed, current)) {
                    failStaleBalanceIfNeeded(reviewed, current, principal.userId());
                    throw new IllegalStateException("El destinatario, el saldo o el documento cambió; genere y confirme una nueva vista previa.");
                }
                return issueToken(pendingConfirmations,
                        binding(current, actor, principal, reviewed.approvedPdf()));
            });
        } catch (SecurityException revokedOrDenied) {
            invalidateReviews(actor);
            throw revokedOrDenied;
        }
    }

    /** Descarta una vista previa que el administrador canceló; nunca escribe ni envía. */
    public void cancelReminderPreview(UserSession actor, long reminderId, String previewToken) {
        authorization.execute(actor, Permission.MANAGE_COLLECTION, () -> {
            String digest = tokenDigest(previewToken);
            ReviewBinding binding = pendingPreviews.get(digest);
            if (binding != null && binding.session() == actor && binding.reminderId() == reminderId) {
                pendingPreviews.remove(digest, binding);
            }
        });
    }

    /**
     * Prepara avisos revisables para saldos positivos. La unicidad por cliente/semana se impone
     * en el repositorio y en PostgreSQL; un cliente saldado no genera una nueva fila.
     */
    public PreparationResult prepareWeeklyReminders(UserSession actor) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION, () -> {
            var principal = authorization.currentPrincipal(actor);
            LocalDateTime now = LocalDateTime.now(clock.withZone(COLOMBIA));
            LocalDate weekStart = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            List<CustomerFinancialSnapshot> snapshots = transactions.inReadOnlyRepeatableRead(this::financialSnapshots);
            int created = 0;
            int refreshed = 0;
            for (CustomerFinancialSnapshot snapshot : snapshots) {
                if (snapshot.breakdown().aggregateBalance().signum() <= 0) continue;
                requireConsistent(snapshot.breakdown());
                Optional<CollectionProfile> found = collections.findProfile(snapshot.customer().getId());
                if (found.isEmpty()) continue;
                CollectionProfile profile = found.get();
                if (!profile.collectionConsent() || !profile.remindersEnabled()
                        || profile.authorizedChannel() == CollectionChannel.NONE) continue;
                CustomerCollectionReminder proposed = new CustomerCollectionReminder(0,
                        snapshot.customer().getId(), weekStart, profile.authorizedChannel(),
                        CustomerCollectionReminder.Status.PENDING_REVIEW, snapshot.breakdown().aggregateBalance(),
                        now, null, 0, CustomerCollectionReminder.ResultCode.PREPARED);
                CollectionFollowUp prepared = event(snapshot.customer().getId(), principal.userId(),
                        CollectionFollowUp.EventType.REMINDER_PREPARED, profile.commitmentDate(),
                        snapshot.breakdown().aggregateBalance(), "Aviso semanal agregado a revisión administrativa.", now);
                var saved = collections.createReminderIfAbsent(proposed, prepared);
                if (saved.inserted()) created++;
                if (saved.refreshed()) {
                    refreshed++;
                    invalidateReviews(snapshot.customer().getId());
                }
            }
            return new PreparationResult(created, refreshed, collections.findReminders(
                    CustomerCollectionReminder.Status.PENDING_REVIEW));
        });
    }

    /** Lista los estados de avisos para el administrador que revisa el lote semanal. */
    public List<CustomerCollectionReminder> listReminders(UserSession actor,
                                                           CustomerCollectionReminder.Status status) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION, () -> {
            collections.reconcileInterruptedAttempts(instanceId);
            return collections.findReminders(status);
        });
    }

    /**
     * Rechaza la entrega directa por ID; una vista previa aceptada y su token son obligatorios.
     */
    public DeliveryResult sendReminder(UserSession actor, long reminderId) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION, () -> {
            var principal = authorization.currentPrincipal(actor);
            recordSendAttempt(principal.userId(), reminderCustomerId(reminderId), AdministrativeAuditEvent.Result.FAILED);
            throw new SecurityException("La entrega requiere confirmar una vista previa vigente.");
        });
    }

    /**
     * Entrega solo una confirmación de vista previa ligada a sesión, perfil, saldo y PDF.
     * La reclamación SQL vuelve a comparar saldo/perfil bajo bloqueos antes de abrir SMTP.
     */
    public DeliveryResult sendReminder(UserSession actor, long reminderId, String confirmationToken) {
        try {
            return authorization.execute(actor, Permission.MANAGE_COLLECTION,
                    () -> sendAuthorized(actor, reminderId, confirmationToken));
        } catch (SecurityException revokedOrDenied) {
            invalidateReviews(actor);
            throw revokedOrDenied;
        }
    }

    private DeliveryResult sendAuthorized(UserSession actor, long reminderId, String confirmationToken) {
        var principal = authorization.currentPrincipal(actor);
        ReviewBinding approved = consumeToken(pendingConfirmations, actor, principal, reminderId, confirmationToken);
        ReviewSnapshot snapshot = transactions.inReadOnlyRepeatableRead(() -> loadReviewSnapshot(reminderId));
        if (!sameContent(approved, snapshot)) {
            failStaleBalanceIfNeeded(approved, snapshot, principal.userId());
            throw new IllegalStateException("La aprobación venció o cambió el destinatario, saldo o documento; vuelva a revisar.");
        }
        CustomerCollectionReminder reminder = snapshot.reminder();
        CollectionProfile profile = snapshot.profile();
        AccountStatementData statement = snapshot.statement();
        if (!emailProvider.isRealDelivery()) {
            EmailDeliveryProvider.Message message = message(withPdf(snapshot.content(), approved.approvedPdf()));
            emailProvider.deliver(message);
            CustomerCollectionReminder simulated = copyReminder(reminder,
                    CustomerCollectionReminder.Status.PENDING_REVIEW, LocalDateTime.now(clock.withZone(COLOMBIA)),
                    reminder.attemptCount(), CustomerCollectionReminder.ResultCode.SIMULATED, null, null,
                    CustomerCollectionReminder.ProviderEvidence.SIMULATED);
            persistReminderOutcome(simulated, reminder.status(), reminder.attemptCount(), principal.userId(),
                    AdministrativeAuditEvent.Result.SUCCESS, null);
            return new DeliveryResult(simulated.status(), CustomerCollectionReminder.ResultCode.SIMULATED,
                    false, false, "Simulación completada; el correo no salió a la red y el aviso sigue en revisión.", null);
        }
        if (!paymentKeyOwnerConfirmed || !paymentKeyVerifiedByAdministrator || paymentKey == null) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.PENDING_REVIEW,
                    CustomerCollectionReminder.ResultCode.PAYMENT_INSTRUCTIONS_UNVERIFIED,
                    "Falta confirmar administrativamente el titular y la llave de pago.");
        }
        if (!contactWindow.allows(ZonedDateTime.now(clock.withZone(COLOMBIA)))) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.PENDING_REVIEW,
                    CustomerCollectionReminder.ResultCode.WINDOW_BLOCKED,
                    "El envío se bloqueó fuera de la ventana autorizada de contacto.");
        }

        LocalDateTime attemptAt = LocalDateTime.now(clock.withZone(COLOMBIA));
        UUID attemptId = UUID.randomUUID();
        boolean claimed = transactions.inTransaction(() -> collections.claimReminder(reminder, attemptId,
                instanceId, attemptAt, statement.totalBalance(), statement.customer(), profile));
        if (!claimed) {
            throw new java.util.ConcurrentModificationException(
                    "El perfil, el saldo o el aviso cambió antes del envío; genere y confirme una nueva vista previa.");
        }
        CustomerCollectionReminder claimedReminder = copyReminder(reminder,
                CustomerCollectionReminder.Status.SENDING, attemptAt, reminder.attemptCount() + 1,
                CustomerCollectionReminder.ResultCode.PREPARED, attemptId, instanceId, null);
        EmailDeliveryProvider.Result providerResult;
        try {
            providerResult = emailProvider.deliver(message(withPdf(snapshot.content(), approved.approvedPdf())));
        } catch (RuntimeException responseLostOrNetworkFailure) {
            providerResult = EmailDeliveryProvider.Result.uncertain();
        }
        if (providerResult == null) providerResult = EmailDeliveryProvider.Result.uncertain();
        return finishClaimedDelivery(principal.userId(), claimedReminder, attemptAt, providerResult);
    }

    private DeliveryResult finishBeforeDelivery(long actorId,
            CustomerCollectionReminder reminder, CustomerCollectionReminder.Status status,
            CustomerCollectionReminder.ResultCode code, String message) {
        LocalDateTime attemptedAt = LocalDateTime.now(clock.withZone(COLOMBIA));
        CustomerCollectionReminder updated = copyReminder(reminder, status, attemptedAt,
                reminder.attemptCount(), code, reminder.attemptId(), null, null);
        AdministrativeAuditEvent.Result result = status == CustomerCollectionReminder.Status.FAILED
                ? AdministrativeAuditEvent.Result.FAILED : AdministrativeAuditEvent.Result.SUCCESS;
        persistReminderOutcome(updated, reminder.status(), reminder.attemptCount(), actorId, result,
                status == CustomerCollectionReminder.Status.FAILED
                        ? CollectionFollowUp.EventType.REMINDER_FAILED : null);
        return new DeliveryResult(status, code, false, false, message, reminder.attemptId());
    }

    private DeliveryResult finishClaimedDelivery(long actorId, CustomerCollectionReminder claimed,
            LocalDateTime attemptAt, EmailDeliveryProvider.Result providerResult) {
        boolean accepted = providerResult.outcome() == EmailDeliveryProvider.Outcome.ACCEPTED;
        boolean rejected = providerResult.outcome() == EmailDeliveryProvider.Outcome.REJECTED;
        // Un proveedor configurado como real que devuelve SIMULATED contradice su contrato;
        // se bloquea en conciliación en lugar de asumir que no hubo efecto externo.
        boolean uncertain = !accepted && !rejected;
        CustomerCollectionReminder.Status status = accepted ? CustomerCollectionReminder.Status.SENT
                : uncertain ? CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED
                : CustomerCollectionReminder.Status.FAILED;
        CustomerCollectionReminder.ResultCode code = accepted ? CustomerCollectionReminder.ResultCode.SENT
                : uncertain ? CustomerCollectionReminder.ResultCode.PROVIDER_OUTCOME_UNCERTAIN
                : CustomerCollectionReminder.ResultCode.PROVIDER_REJECTED;
        CustomerCollectionReminder updated = copyReminder(claimed, status, attemptAt,
                claimed.attemptCount(), code, claimed.attemptId(), null,
                providerEvidence(accepted, rejected));
        AdministrativeAuditEvent.Result auditResult = uncertain ? AdministrativeAuditEvent.Result.UNCERTAIN
                : accepted ? AdministrativeAuditEvent.Result.SUCCESS : AdministrativeAuditEvent.Result.FAILED;
        CollectionFollowUp.EventType followUp = accepted ? CollectionFollowUp.EventType.REMINDER_SENT
                : uncertain ? CollectionFollowUp.EventType.REMINDER_UNCERTAIN
                : CollectionFollowUp.EventType.REMINDER_FAILED;
        try {
            persistReminderOutcome(updated, CustomerCollectionReminder.Status.SENDING,
                    claimed.attemptCount(), actorId, auditResult, followUp);
            return new DeliveryResult(status, code, accepted, uncertain, uncertain
                    ? "El resultado del proveedor es incierto. El aviso requiere conciliación y no se reenviará."
                    : accepted ? "El proveedor confirmó la aceptación del correo."
                    : "El proveedor confirmó el rechazo; el aviso no se reintentará automáticamente.", claimed.attemptId());
        } catch (RuntimeException persistenceFailure) {
            CustomerCollectionReminder uncertainReminder = copyReminder(claimed,
                    CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED, attemptAt,
                    claimed.attemptCount(), CustomerCollectionReminder.ResultCode.PROVIDER_OUTCOME_UNCERTAIN,
                    claimed.attemptId(), null, providerEvidence(accepted, rejected));
            try {
                persistReminderOutcome(uncertainReminder, CustomerCollectionReminder.Status.SENDING,
                        claimed.attemptCount(), actorId, AdministrativeAuditEvent.Result.UNCERTAIN,
                        CollectionFollowUp.EventType.REMINDER_UNCERTAIN);
            } catch (RuntimeException reconciliationPersistenceFailure) {
                // SENDING ya está confirmado antes del correo; la próxima instancia lo conciliará sin reenvío.
            }
            return new DeliveryResult(CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED,
                    CustomerCollectionReminder.ResultCode.PROVIDER_OUTCOME_UNCERTAIN, accepted, true,
                    "El resultado del proveedor no pudo guardarse con certeza. El intento quedó bloqueado para conciliación; no lo reenvíe.",
                    claimed.attemptId());
        }
    }

    private void persistReminderOutcome(CustomerCollectionReminder updated,
            CustomerCollectionReminder.Status expectedStatus, int expectedAttemptCount, long actorId,
            AdministrativeAuditEvent.Result result, CollectionFollowUp.EventType followUpType) {
        transactions.inTransaction(() -> {
            collections.updateReminder(updated, expectedStatus, expectedAttemptCount);
            audit.append(new AdministrativeAuditEvent(0, actorId,
                    AdministrativeAuditEvent.Action.STATEMENT_SEND_ATTEMPT,
                    AdministrativeAuditEvent.TargetType.CUSTOMER, updated.customerId(),
                    LocalDateTime.now(clock.withZone(COLOMBIA)), result));
            if (followUpType != null) {
                collections.recordFollowUp(event(updated.customerId(), actorId, followUpType, null,
                        updated.balanceSnapshot(), "Resultado del aviso: " + updated.resultCode().name() + ".",
                        LocalDateTime.now(clock.withZone(COLOMBIA))));
            }
        });
    }

    private void recordSendAttempt(long actorId, String customerId, AdministrativeAuditEvent.Result result) {
        transactions.inTransaction(() -> audit.append(new AdministrativeAuditEvent(0, actorId,
                AdministrativeAuditEvent.Action.STATEMENT_SEND_ATTEMPT,
                AdministrativeAuditEvent.TargetType.CUSTOMER, customerId,
                LocalDateTime.now(clock.withZone(COLOMBIA)), result)));
    }

    private CollectionDashboard loadDashboard() {
        LocalDateTime asOf = LocalDateTime.now(clock.withZone(COLOMBIA));
        LocalDate today = asOf.toLocalDate();
        List<CustomerFinancialSnapshot> snapshots = financialSnapshots();
        List<Sale> allSales = sales.listAll();
        List<CollectionRecommendation> recommendations = new ArrayList<>();
        int overdue = 0;
        int aged = 0;
        int upcoming = 0;
        for (CustomerFinancialSnapshot snapshot : snapshots) {
            CustomerDebtBreakdown balance = snapshot.breakdown();
            requireConsistent(balance);
            if (balance.aggregateBalance().signum() <= 0) continue;
            CollectionProfile profile = collections.findProfile(snapshot.customer().getId()).orElse(null);
            LocalDate commitment = profile == null ? null : profile.commitmentDate();
            AgeInfo age = ageInfo(snapshot.customer().getId(), balance.generalDebt(), allSales, today);
            boolean isOverdue = commitment != null && commitment.isBefore(today);
            boolean isAged = age.hasThirtyDaysOrMore();
            boolean isUpcoming = !isOverdue && commitment != null && !commitment.isAfter(today.plusDays(3));
            if (isOverdue) overdue++;
            if (isAged) aged++;
            if (isUpcoming) upcoming++;
            CollectionRecommendation.Priority priority;
            if (isOverdue) priority = CollectionRecommendation.Priority.HIGH;
            else if (isAged) priority = CollectionRecommendation.Priority.MEDIUM;
            else if (isUpcoming) priority = CollectionRecommendation.Priority.UPCOMING;
            else priority = CollectionRecommendation.Priority.GENERAL;
            boolean recentPayment = recentPayment(snapshot.customer().getId(), profile, balance.aggregateBalance(),
                    allSales, asOf);
            String explanation = explanation(priority, commitment, age, recentPayment);
            recommendations.add(new CollectionRecommendation(snapshot.customer().getId(),
                    snapshot.customer().getName(), balance.aggregateBalance(), priority,
                    commitment, age.label(), recentPayment, explanation));
        }
        recommendations.sort(Comparator.comparing(CollectionRecommendation::priority)
                .thenComparing(value -> value.commitmentDate() == null ? LocalDate.MAX : value.commitmentDate())
                .thenComparing(CollectionRecommendation::outstandingBalance, Comparator.reverseOrder())
                .thenComparing(CollectionRecommendation::customerName, String.CASE_INSENSITIVE_ORDER));
        int pending = collections.findReminders(CustomerCollectionReminder.Status.PENDING_REVIEW).size();
        return new CollectionDashboard(overdue, aged, upcoming, pending, recommendations);
    }

    private List<CustomerFinancialSnapshot> financialSnapshots() {
        List<Customer> allCustomers = customers.getAllCustomers();
        List<Sale> allSales = sales.listAll();
        Map<String, BigDecimal> salesDebt = new HashMap<>();
        for (Sale sale : allSales) {
            Customer saleCustomer = sale.getCustomer();
            if (saleCustomer != null && sale.calculateBalance().signum() > 0) {
                salesDebt.merge(saleCustomer.getId(), sale.calculateBalance(), BigDecimal::add);
            }
        }
        return allCustomers.stream().map(customer -> {
            BigDecimal salesBalance = salesDebt.getOrDefault(customer.getId(), BigDecimal.ZERO);
            return new CustomerFinancialSnapshot(customer,
                    new CustomerDebtBreakdown(customer.getBalance(), salesBalance,
                            customer.getBalance().subtract(salesBalance)),
                    allSales.stream().filter(sale -> belongsTo(sale, customer.getId())).toList());
        }).toList();
    }

    private CustomerDebtBreakdown currentBalanceFor(String customerId) {
        Customer customer = customers.getCustomerById(requireCustomerId(customerId));
        List<Sale> customerSales = sales.listAll().stream().filter(sale -> belongsTo(sale, customerId)).toList();
        return currentBalance(customer, customerSales);
    }

    private static CustomerDebtBreakdown currentBalance(Customer customer, List<Sale> customerSales) {
        BigDecimal salesBalance = customerSales.stream().map(Sale::calculateBalance)
                .filter(value -> value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CustomerDebtBreakdown(customer.getBalance(), salesBalance,
                customer.getBalance().subtract(salesBalance));
    }

    private AgeInfo ageInfo(String customerId, BigDecimal generalDebt, List<Sale> allSales, LocalDate asOf) {
        List<LocalDate> componentAges = new ArrayList<>();
        boolean unknown = false;
        List<Sale> openSales = allSales.stream().filter(sale -> belongsTo(sale, customerId))
                .filter(sale -> sale.calculateBalance().signum() > 0).toList();
        if (!openSales.isEmpty()) {
            openSales.stream().map(sale -> sale.getCreatedAt().toLocalDate()).min(LocalDate::compareTo)
                    .ifPresent(componentAges::add);
        }
        if (generalDebt.signum() > 0) {
            List<GeneralDebtMovement> ledger = this.generalDebt.listMovements(customerId, null, asOf, null);
            Optional<LocalDate> oldestGeneral = oldestVerifiableGeneralDebtDate(ledger, generalDebt);
            if (oldestGeneral.isPresent()) componentAges.add(oldestGeneral.get());
            else unknown = true;
        }
        if (componentAges.isEmpty()) return new AgeInfo("Antigüedad no determinada", false);
        long oldestAge = componentAges.stream().mapToLong(date -> Math.max(0, ChronoUnit.DAYS.between(date, asOf)))
                .max().orElse(0);
        if (generalDebt.signum() > 0 && unknown) {
            return new AgeInfo("Ventas: " + oldestAge + " días; deuda general: Antigüedad no determinada",
                    oldestAge >= 30);
        }
        return new AgeInfo(oldestAge + (oldestAge == 1 ? " día" : " días"), oldestAge >= 30);
    }

    private Optional<LocalDate> oldestVerifiableGeneralDebtDate(List<GeneralDebtMovement> ledger,
                                                                 BigDecimal expectedBalance) {
        Map<LocalDate, BigDecimal> chargesByDate = new TreeMap<>();
        BigDecimal charges = BigDecimal.ZERO;
        BigDecimal payments = BigDecimal.ZERO;
        for (GeneralDebtMovement movement : ledger) {
            if (movement.type() == GeneralDebtMovementType.DEBT_INCREASE) {
                LocalDate date = movement.occurredAt().toLocalDate();
                chargesByDate.merge(date, movement.amount(), BigDecimal::add);
                charges = charges.add(movement.amount());
            } else {
                payments = payments.add(movement.amount());
            }
        }
        if (charges.subtract(payments).compareTo(expectedBalance) != 0 || expectedBalance.signum() <= 0
                || chargesByDate.isEmpty()) return Optional.empty();
        if (chargesByDate.size() == 1 || payments.signum() == 0) {
            return Optional.of(chargesByDate.keySet().iterator().next());
        }
        Map.Entry<LocalDate, BigDecimal> oldest = chargesByDate.entrySet().iterator().next();
        // Sin asignación histórica, se afirma antigüedad solo si los pagos no alcanzarían a
        // liquidar ni siquiera todo el primer grupo de cargos; de otro modo el dato es ambiguo.
        return payments.compareTo(oldest.getValue()) < 0 ? Optional.of(oldest.getKey()) : Optional.empty();
    }

    private boolean recentPayment(String customerId, CollectionProfile profile, BigDecimal currentBalance,
                                 List<Sale> allSales,
                                 LocalDateTime asOf) {
        if (profile == null || profile.observedAt() == null || profile.observedBalance() == null
                || profile.observedBalance().compareTo(currentBalance) == 0) return false;
        boolean salePayment = allSales.stream().filter(sale -> belongsTo(sale, customerId))
                .flatMap(sale -> sale.getPayments().stream())
                .anyMatch(payment -> payment.getTimestamp().isAfter(profile.observedAt())
                        && !payment.getTimestamp().isAfter(asOf));
        return salePayment || generalDebt.listMovements(customerId, profile.observedAt().toLocalDate(),
                        asOf.toLocalDate(), null)
                .stream().anyMatch(movement -> movement.type() == GeneralDebtMovementType.PAYMENT
                        && movement.occurredAt().isAfter(profile.observedAt())
                        && !movement.occurredAt().isAfter(asOf));
    }

    private static String explanation(CollectionRecommendation.Priority priority, LocalDate commitment,
                                      AgeInfo age, boolean recentPayment) {
        String base = switch (priority) {
            case HIGH -> "El compromiso de pago venció el " + commitment + ".";
            case MEDIUM -> "La deuda tiene antigüedad verificable de al menos 30 días (" + age.label() + ").";
            case UPCOMING -> "El compromiso de pago vence próximamente: " + commitment + ".";
            case GENERAL -> "Saldo pendiente; " + age.label() + ".";
        };
        return recentPayment ? base + " Se registró un pago posterior a la observación anterior del saldo." : base;
    }

    private AccountStatementData createStatement(String customerId) {
        String id = requireCustomerId(customerId);
        Customer customer = customers.getCustomerById(id);
        List<Sale> allSales = sales.listAll();
        List<Sale> pendingSales = allSales.stream().filter(sale -> belongsTo(sale, id))
                .filter(sale -> sale.calculateBalance().signum() > 0).toList();
        CustomerDebtBreakdown balance = currentBalance(customer, allSales.stream()
                .filter(sale -> belongsTo(sale, id)).toList());
        requireConsistent(balance);
        CollectionProfile profile = collections.findProfile(id).orElse(null);
        boolean paymentInfoSafe = paymentKeyOwnerConfirmed && paymentKeyVerifiedByAdministrator;
        String configuredKey = paymentInfoSafe ? paymentKey : null;
        return new AccountStatementData(BUSINESS_NAME, customer, LocalDate.now(clock.withZone(COLOMBIA)),
                pendingSales, balance.generalDebt(), balance.aggregateBalance(),
                profile == null ? null : profile.commitmentDate(), configuredKey);
    }

    private ReviewSnapshot loadReviewSnapshot(long reminderId) {
        CustomerCollectionReminder reminder = requireReminder(reminderId);
        if (reminder.status() != CustomerCollectionReminder.Status.PENDING_REVIEW || reminder.attemptCount() != 0) {
            if (reminder.status() == CustomerCollectionReminder.Status.RECONCILIATION_REQUIRED) {
                throw new IllegalStateException("El resultado del intento requiere conciliación; no se reenviará.");
            }
            throw new IllegalStateException("El aviso ya no está pendiente de revisión.");
        }
        CollectionProfile profile = collections.findProfile(reminder.customerId())
                .orElseThrow(() -> new IllegalStateException("El cliente no tiene preferencias de contacto."));
        requireConsent(profile, reminder);
        AccountStatementData statement = createStatement(reminder.customerId());
        return new ReviewSnapshot(reminder, profile, statement, emailContent(profile.email(), statement));
    }

    private EmailPreview emailPreview(String recipient, AccountStatementData statement, String previewToken) {
        EmailPreviewContent content = emailContent(recipient, statement);
        return new EmailPreview(content.recipient(), content.subject(), content.text(), content.pdf(), previewToken);
    }

    private EmailPreviewContent emailContent(String recipient, AccountStatementData statement) {
        byte[] pdf = pdfGenerator.generate(statement);
        String text = "Adjuntamos el estado de cuenta comercial actualizado de La Exclusiva. "
                + "El documento separa deuda de ventas y deuda general. Los pagos se consideran aplicados "
                + "cuando su registro y confirmación aparecen en el sistema.";
        return new EmailPreviewContent(recipient, "Estado de cuenta comercial - La Exclusiva", text, pdf);
    }

    private static EmailDeliveryProvider.Message message(EmailPreviewContent content) {
        return new EmailDeliveryProvider.Message(content.recipient(), content.subject(), content.text(),
                "estado-de-cuenta-la-exclusiva.pdf", content.pdf());
    }

    private ReviewBinding binding(ReviewSnapshot snapshot, UserSession actor,
            com.laexclusiva.service.AuthenticatedPrincipal principal, byte[] approvedPdf) {
        return new ReviewBinding(actor, principal.userId(), principal.accountInstanceId(),
                principal.securityVersion(), snapshot.reminder().id(), snapshot.reminder().customerId(),
                snapshot.reminder().balanceSnapshot(), profileVersion(snapshot.profile()), snapshot.content().recipient(),
                pdfDigest(approvedPdf), contentDigest(withPdf(snapshot.content(), approvedPdf)),
                statementDigest(snapshot.statement()), approvedPdf,
                reminderVersion(snapshot.reminder()), LocalDateTime.now(clock.withZone(COLOMBIA))
                .plus(REVIEW_TOKEN_LIFETIME));
    }

    private String issueToken(Map<String, ReviewBinding> tokens, ReviewBinding binding) {
        expireTokens(tokens);
        byte[] bytes = new byte[32];
        tokenRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(tokenDigest(token), binding);
        return token;
    }

    private ReviewBinding consumeToken(Map<String, ReviewBinding> tokens, UserSession actor,
            com.laexclusiva.service.AuthenticatedPrincipal principal, long reminderId, String token) {
        if (token == null || token.isBlank()) throw new SecurityException("La confirmación del aviso es obligatoria.");
        String digest = tokenDigest(token);
        ReviewBinding binding = tokens.get(digest);
        if (binding == null || binding.session() != actor || binding.userId() != principal.userId()
                || !java.util.Objects.equals(binding.accountInstanceId(), principal.accountInstanceId())
                || binding.securityVersion() != principal.securityVersion() || binding.reminderId() != reminderId) {
            throw new SecurityException("La confirmación no pertenece a esta sesión o ya fue utilizada.");
        }
        if (!LocalDateTime.now(clock.withZone(COLOMBIA)).isBefore(binding.expiresAt())) {
            tokens.remove(digest, binding);
            throw new SecurityException("La confirmación venció; genere una nueva vista previa.");
        }
        if (!tokens.remove(digest, binding)) {
            throw new SecurityException("La confirmación ya fue utilizada.");
        }
        return binding;
    }

    private static boolean sameContent(ReviewBinding reviewed, ReviewSnapshot current) {
        return reviewed.customerId().equals(current.reminder().customerId())
                && reviewed.balanceSnapshot().compareTo(current.statement().totalBalance()) == 0
                && current.reminder().balanceSnapshot().compareTo(current.statement().totalBalance()) == 0
                && reviewed.profileVersion().equals(profileVersion(current.profile()))
                && reviewed.recipient().equals(current.content().recipient())
                && reviewed.pdfDigest().equals(pdfDigest(reviewed.approvedPdf()))
                && reviewed.contentDigest().equals(contentDigest(withPdf(current.content(), reviewed.approvedPdf())))
                && reviewed.statementDigest().equals(statementDigest(current.statement()))
                && reviewed.reminderVersion().equals(reminderVersion(current.reminder()));
    }

    private static EmailPreviewContent withPdf(EmailPreviewContent content, byte[] pdf) {
        return new EmailPreviewContent(content.recipient(), content.subject(), content.text(), pdf);
    }

    /** Huella estable del contenido comercial; no depende de metadatos variables del archivo PDF. */
    private static String statementDigest(AccountStatementData statement) {
        List<String> fields = new ArrayList<>();
        Customer customer = statement.customer();
        fields.addAll(List.of(statement.businessName(), customer.getId(), customer.getName(),
                Objects.toString(customer.getPhone(), ""), Objects.toString(customer.getAddress(), ""),
                statement.generatedDate().toString(), statement.generalDebt().toPlainString(),
                statement.totalBalance().toPlainString(), Objects.toString(statement.commitmentDate(), ""),
                Objects.toString(statement.paymentKey(), "")));
        for (Sale sale : statement.pendingSales()) {
            fields.addAll(List.of(sale.getId(), sale.getCreatedAt().toString(),
                    sale.calculateTotal().toPlainString(), sale.calculateBalance().toPlainString()));
            sale.getLines().forEach(line -> {
                var product = line.getProduct();
                fields.addAll(List.of(product.getId(), product.getName(),
                        line.getPresentation().code(), Integer.toString(line.getQuantity()),
                        line.getUnitPrice().toPlainString(), Integer.toString(line.getUnitsPerBasketSnapshot())));
            });
            sale.getPayments().forEach(payment -> fields.addAll(List.of(
                    payment.getTimestamp().toString(), payment.getAmount().toPlainString())));
        }
        return digestFields(fields.toArray(String[]::new));
    }

    private static boolean balanceMatches(ReviewSnapshot snapshot) {
        return snapshot.reminder().balanceSnapshot().compareTo(snapshot.statement().totalBalance()) == 0;
    }

    private void failStaleBalanceIfNeeded(ReviewBinding reviewed, ReviewSnapshot current, long actorId) {
        if (!balanceMatches(current)) markBalanceChanged(current, actorId);
    }

    private void markBalanceChanged(ReviewSnapshot snapshot, long actorId) {
        CustomerCollectionReminder reminder = snapshot.reminder();
        if (reminder.status() != CustomerCollectionReminder.Status.PENDING_REVIEW || reminder.attemptCount() != 0) return;
        finishBeforeDelivery(actorId, reminder, CustomerCollectionReminder.Status.FAILED,
                CustomerCollectionReminder.ResultCode.BALANCE_CHANGED,
                "El saldo cambió; el aviso se debe actualizar y revisar nuevamente.");
    }

    private static String profileVersion(CollectionProfile profile) {
        return digestFields(profile.customerId(), profile.email(), profile.authorizedChannel().name(),
                Boolean.toString(profile.collectionConsent()), Objects.toString(profile.consentRecordedAt(), null),
                Boolean.toString(profile.remindersEnabled()), Objects.toString(profile.commitmentDate(), null),
                Objects.toString(profile.observedBalance(), null), Objects.toString(profile.observedAt(), null));
    }

    private static String reminderVersion(CustomerCollectionReminder reminder) {
        return digestFields(Long.toString(reminder.id()), reminder.customerId(), reminder.weekStart().toString(),
                reminder.channel().name(), reminder.status().name(), reminder.balanceSnapshot().toPlainString(),
                reminder.generatedAt().toString(), Objects.toString(reminder.lastAttemptAt(), null),
                Integer.toString(reminder.attemptCount()), reminder.resultCode().name(),
                Objects.toString(reminder.attemptId(), null));
    }

    private static String pdfDigest(byte[] bytes) { return digestFields(HexFormat.of().formatHex(sha256(bytes))); }

    private static String contentDigest(EmailPreviewContent content) {
        return digestFields(content.recipient(), content.subject(), content.text(), pdfDigest(content.pdf()));
    }

    private static String digestFields(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                if (field == null) {
                    digest.update(new byte[]{-1, -1, -1, -1});
                } else {
                    byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("No está disponible SHA-256.", impossible);
        }
    }

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("No está disponible SHA-256.", impossible);
        }
    }

    private static String tokenDigest(String token) {
        return digestFields(token == null ? "" : token);
    }

    private void expireTokens(Map<String, ReviewBinding> tokens) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(COLOMBIA));
        tokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private void invalidateReviews(String customerId) {
        pendingPreviews.entrySet().removeIf(entry -> entry.getValue().customerId().equals(customerId));
        pendingConfirmations.entrySet().removeIf(entry -> entry.getValue().customerId().equals(customerId));
    }

    private void invalidateReviews(UserSession session) {
        pendingPreviews.entrySet().removeIf(entry -> entry.getValue().session() == session);
        pendingConfirmations.entrySet().removeIf(entry -> entry.getValue().session() == session);
    }

    private String reminderCustomerId(long reminderId) {
        return requireReminder(reminderId).customerId();
    }

    private CustomerCollectionReminder requireReminder(long id) {
        if (id <= 0) throw new IllegalArgumentException("El identificador del aviso no es válido.");
        return collections.findReminders(null).stream().filter(value -> value.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el aviso solicitado."));
    }

    private static void requireConsent(CollectionProfile profile, CustomerCollectionReminder reminder) {
        if (!profile.collectionConsent() || !profile.remindersEnabled()
                || profile.authorizedChannel() != reminder.channel()) {
            throw new SecurityException("El consentimiento o el canal de contacto ya no están vigentes.");
        }
        if (reminder.channel() != CollectionChannel.EMAIL || profile.email() == null) {
            throw new IllegalStateException("El canal autorizado requiere gestión manual, sin correo automático.");
        }
    }

    private void requireConsistent(CustomerDebtBreakdown balance) {
        if (!balance.isConsistent()) throw new IllegalStateException(
                "La deuda de ventas supera el saldo agregado; no se preparará una recomendación o aviso.");
    }

    private static CollectionFollowUp event(String customerId, long actorId,
            CollectionFollowUp.EventType type, LocalDate commitment, BigDecimal balance,
            String note, LocalDateTime occurredAt) {
        return new CollectionFollowUp(0, customerId, actorId, type, commitment, balance, note, occurredAt);
    }

    private static CustomerCollectionReminder copyReminder(CustomerCollectionReminder source,
            CustomerCollectionReminder.Status status, LocalDateTime attemptedAt, int attemptCount,
            CustomerCollectionReminder.ResultCode result, UUID attemptId, UUID attemptOwnerId,
            CustomerCollectionReminder.ProviderEvidence providerEvidence) {
        return new CustomerCollectionReminder(source.id(), source.customerId(), source.weekStart(),
                source.channel(), status, source.balanceSnapshot(), source.generatedAt(), attemptedAt,
                attemptCount, result, attemptId, attemptOwnerId, providerEvidence);
    }

    private static CustomerCollectionReminder.ProviderEvidence providerEvidence(boolean accepted, boolean rejected) {
        if (accepted) return CustomerCollectionReminder.ProviderEvidence.SMTP_ACCEPTED;
        if (rejected) return CustomerCollectionReminder.ProviderEvidence.SMTP_REJECTED;
        return CustomerCollectionReminder.ProviderEvidence.SMTP_OUTCOME_UNCERTAIN;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String value = email.trim();
        if (value.length() > 254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("El correo electrónico no tiene un formato válido.");
        }
        return value;
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) return null;
        String value = note.trim();
        if (value.length() > 500 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("La nota admite hasta 500 caracteres.");
        }
        return value;
    }

    private static boolean belongsTo(Sale sale, String customerId) {
        Customer customer = sale.getCustomer();
        return customer != null && customerId.equals(customer.getId());
    }

    private static String requireCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank() || customerId.length() > 64) {
            throw new IllegalArgumentException("El ID del cliente no es válido.");
        }
        return customerId;
    }

    private record CustomerFinancialSnapshot(Customer customer, CustomerDebtBreakdown breakdown,
                                             List<Sale> sales) { }
    private record AgeInfo(String label, boolean hasThirtyDaysOrMore) { }
    private record EmailPreviewContent(String recipient, String subject, String text, byte[] pdf) {
        private EmailPreviewContent { pdf = pdf.clone(); }
        @Override public byte[] pdf() { return pdf.clone(); }
    }
    private record ReviewSnapshot(CustomerCollectionReminder reminder, CollectionProfile profile,
                                  AccountStatementData statement, EmailPreviewContent content) { }
    private record ReviewBinding(UserSession session, long userId, UUID accountInstanceId, long securityVersion,
                                 long reminderId, String customerId, BigDecimal balanceSnapshot,
                                 String profileVersion, String recipient, String pdfDigest,
                                 String contentDigest, String statementDigest, byte[] approvedPdf,
                                 String reminderVersion, LocalDateTime expiresAt) {
        private ReviewBinding { approvedPdf = approvedPdf.clone(); }
        @Override public byte[] approvedPdf() { return approvedPdf.clone(); }
    }
}
