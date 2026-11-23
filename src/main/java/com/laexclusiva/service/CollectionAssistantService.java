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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Coordina recomendaciones explicables, seguimiento, estados comerciales y avisos revisables.
 * No utiliza aprendizaje automático ni confirma pagos: toda escritura requiere permiso vigente,
 * y el envío SMTP solo se ejecuta tras validar consentimiento, ventana local y reserva semanal.
 */
public final class CollectionAssistantService {
    public static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final String BUSINESS_NAME = "La Exclusiva";

    /** Resumen de una preparación idempotente de avisos para una semana ISO. */
    public record PreparationResult(int created, List<CustomerCollectionReminder> pendingReview) {
        public PreparationResult { pendingReview = List.copyOf(pendingReview); }
    }

    /** Vista previa efímera; nunca se persiste ni registra el correo o el PDF. */
    public record EmailPreview(String recipient, String subject, String text, byte[] pdf) {
        public EmailPreview {
            if (recipient == null || subject == null || text == null || pdf == null) {
                throw new IllegalArgumentException("La vista previa requiere destinatario y documento.");
            }
            pdf = pdf.clone();
        }
        @Override public byte[] pdf() { return pdf.clone(); }
    }

    /** Resultado de la operación sin datos internos del proveedor ni excepción SMTP. */
    public record DeliveryResult(CustomerCollectionReminder.Status status,
                                 CustomerCollectionReminder.ResultCode resultCode,
                                 boolean providerAccepted, boolean outcomeUncertain, String message) { }

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
            String normalizedEmail = consent ? normalizeEmail(email) : null;
            if (channel == CollectionChannel.EMAIL && normalizedEmail == null) {
                throw new IllegalArgumentException("El canal email requiere una dirección válida.");
            }
            LocalDateTime now = LocalDateTime.now(clock.withZone(COLOMBIA));
            boolean consentChanged = previous.collectionConsent() != consent
                    || previous.authorizedChannel() != channel
                    || previous.remindersEnabled() != enabledReminders;
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
            return collections.saveProfile(profile, events);
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
                () -> transactions.inReadOnlyRepeatableRead(() -> {
                    CustomerCollectionReminder reminder = requireReminder(reminderId);
                    if (reminder.status() != CustomerCollectionReminder.Status.PENDING_REVIEW) {
                        throw new IllegalStateException("El aviso ya no está pendiente de revisión.");
                    }
                    CollectionProfile profile = collections.findProfile(reminder.customerId())
                            .orElseThrow(() -> new IllegalStateException("El cliente no tiene preferencias de contacto."));
                    requireConsent(profile, reminder);
                    AccountStatementData document = createStatement(reminder.customerId());
                    return emailPreview(profile.email(), document);
                }));
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
                if (collections.createReminderIfAbsent(proposed, prepared).inserted()) created++;
            }
            return new PreparationResult(created, collections.findReminders(
                    CustomerCollectionReminder.Status.PENDING_REVIEW));
        });
    }

    /** Lista los estados de avisos para el administrador que revisa el lote semanal. */
    public List<CustomerCollectionReminder> listReminders(UserSession actor,
                                                           CustomerCollectionReminder.Status status) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION,
                () -> collections.findReminders(status));
    }

    /**
     * Entrega el aviso seleccionado. No hay envío masivo: cada correo requiere vista previa y
     * acción individual. Un estado SENDING nunca se reintenta automáticamente tras incertidumbre.
     */
    public DeliveryResult sendReminder(UserSession actor, long reminderId) {
        return authorization.execute(actor, Permission.MANAGE_COLLECTION,
                () -> sendAuthorized(actor, reminderId));
    }

    private DeliveryResult sendAuthorized(UserSession actor, long reminderId) {
        var principal = authorization.currentPrincipal(actor);
        CustomerCollectionReminder reminder = requireReminder(reminderId);
        if (reminder.status() != CustomerCollectionReminder.Status.PENDING_REVIEW
                || reminder.attemptCount() != 0) {
            recordSendAttempt(principal.userId(), reminder.customerId(), AdministrativeAuditEvent.Result.FAILED);
            throw new IllegalStateException("El aviso ya fue intentado o su resultado requiere revisión; no se reenviará.");
        }
        CollectionProfile profile = collections.findProfile(reminder.customerId()).orElse(null);
        if (profile == null || !profile.collectionConsent() || !profile.remindersEnabled()
                || profile.authorizedChannel() != reminder.channel()) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.FAILED,
                    CustomerCollectionReminder.ResultCode.CONSENT_MISSING,
                    "El consentimiento o canal ya no está vigente.");
        }
        if (reminder.channel() != CollectionChannel.EMAIL) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.PENDING_REVIEW,
                    CustomerCollectionReminder.ResultCode.MANUAL_CONTACT_ONLY,
                    "El canal autorizado requiere gestión manual; no se enviará correo.");
        }
        CustomerDebtBreakdown current = transactions.inReadOnlyRepeatableRead(
                () -> currentBalanceFor(reminder.customerId()));
        if (!current.isConsistent() || current.aggregateBalance().signum() <= 0
                || current.aggregateBalance().compareTo(reminder.balanceSnapshot()) != 0) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.FAILED,
                    CustomerCollectionReminder.ResultCode.BALANCE_CHANGED,
                    "El saldo cambió; el aviso requiere una nueva revisión.");
        }
        ZonedDateTime localNow = ZonedDateTime.now(clock.withZone(COLOMBIA));
        if (!contactWindow.allows(localNow)) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.PENDING_REVIEW,
                    CustomerCollectionReminder.ResultCode.WINDOW_BLOCKED,
                    "El envío se bloqueó fuera de la ventana autorizada de contacto.");
        }
        AccountStatementData statement = transactions.inReadOnlyRepeatableRead(
                () -> createStatement(reminder.customerId()));
        if (statement.totalBalance().compareTo(reminder.balanceSnapshot()) != 0) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.FAILED,
                    CustomerCollectionReminder.ResultCode.BALANCE_CHANGED,
                    "El saldo cambió durante la revisión; el aviso requiere una nueva revisión.");
        }
        profile = collections.findProfile(reminder.customerId()).orElse(null);
        if (profile == null || !profile.collectionConsent() || !profile.remindersEnabled()
                || profile.authorizedChannel() != CollectionChannel.EMAIL || profile.email() == null) {
            return finishBeforeDelivery(principal.userId(), reminder,
                    CustomerCollectionReminder.Status.FAILED,
                    CustomerCollectionReminder.ResultCode.CONSENT_MISSING,
                    "El consentimiento o canal cambió durante la revisión.");
        }
        EmailPreview preview = emailPreview(profile.email(), statement);
        EmailDeliveryProvider.Message message = new EmailDeliveryProvider.Message(preview.recipient(),
                preview.subject(), preview.text(), "estado-de-cuenta-la-exclusiva.pdf", preview.pdf());
        if (!emailProvider.isRealDelivery()) {
            EmailDeliveryProvider.Result simulated = emailProvider.deliver(message);
            CustomerCollectionReminder updated = copyReminder(reminder,
                    CustomerCollectionReminder.Status.PENDING_REVIEW, LocalDateTime.now(clock.withZone(COLOMBIA)),
                    reminder.attemptCount(), CustomerCollectionReminder.ResultCode.SIMULATED);
            persistReminderOutcome(updated, reminder.status(), reminder.attemptCount(), principal.userId(),
                    AdministrativeAuditEvent.Result.SUCCESS, null);
            return new DeliveryResult(updated.status(), CustomerCollectionReminder.ResultCode.SIMULATED,
                    simulated.acceptedByProvider(), false,
                    "Simulación completada; el correo no salió a la red y el aviso sigue en revisión.");
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
        boolean claimed = transactions.inTransaction(() -> collections.claimReminder(reminder, attemptAt));
        if (!claimed) {
            throw new java.util.ConcurrentModificationException("Otro proceso reclamó el aviso; vuelva a consultarlo.");
        }
        boolean accepted;
        try {
            accepted = emailProvider.deliver(message).acceptedByProvider();
        } catch (RuntimeException uncertainProviderResult) {
            return finishClaimedDelivery(principal.userId(), reminder, attemptAt, false,
                    CustomerCollectionReminder.ResultCode.PROVIDER_FAILED);
        }
        CustomerCollectionReminder.ResultCode code = accepted
                ? CustomerCollectionReminder.ResultCode.SENT
                : CustomerCollectionReminder.ResultCode.PROVIDER_FAILED;
        return finishClaimedDelivery(principal.userId(), reminder, attemptAt, accepted, code);
    }

    private DeliveryResult finishBeforeDelivery(long actorId,
            CustomerCollectionReminder reminder, CustomerCollectionReminder.Status status,
            CustomerCollectionReminder.ResultCode code, String message) {
        LocalDateTime attemptedAt = LocalDateTime.now(clock.withZone(COLOMBIA));
        CustomerCollectionReminder updated = copyReminder(reminder, status, attemptedAt,
                reminder.attemptCount(), code);
        AdministrativeAuditEvent.Result result = status == CustomerCollectionReminder.Status.FAILED
                ? AdministrativeAuditEvent.Result.FAILED : AdministrativeAuditEvent.Result.SUCCESS;
        persistReminderOutcome(updated, reminder.status(), reminder.attemptCount(), actorId, result,
                status == CustomerCollectionReminder.Status.FAILED
                        ? CollectionFollowUp.EventType.REMINDER_FAILED : null);
        return new DeliveryResult(status, code, false, false, message);
    }

    private DeliveryResult finishClaimedDelivery(long actorId,
            CustomerCollectionReminder previous, LocalDateTime attemptAt, boolean accepted,
            CustomerCollectionReminder.ResultCode resultCode) {
        CustomerCollectionReminder.Status status = accepted ? CustomerCollectionReminder.Status.SENT
                : CustomerCollectionReminder.Status.FAILED;
        CustomerCollectionReminder updated = copyReminder(previous, status, attemptAt,
                previous.attemptCount() + 1, resultCode);
        try {
            persistReminderOutcome(updated, CustomerCollectionReminder.Status.SENDING,
                    previous.attemptCount() + 1, actorId,
                    accepted ? AdministrativeAuditEvent.Result.SUCCESS : AdministrativeAuditEvent.Result.FAILED,
                    accepted ? CollectionFollowUp.EventType.REMINDER_SENT : CollectionFollowUp.EventType.REMINDER_FAILED);
            return new DeliveryResult(status, resultCode, accepted, false, accepted
                    ? "El proveedor aceptó el correo; se registró el resultado."
                    : "El proveedor no confirmó el correo; no se reintentará automáticamente.");
        } catch (RuntimeException persistenceFailure) {
            return new DeliveryResult(CustomerCollectionReminder.Status.SENDING,
                    CustomerCollectionReminder.ResultCode.PROVIDER_FAILED, accepted, true,
                    "El proveedor pudo haber aceptado el correo, pero no se confirmó el registro. No vuelva a enviarlo; revise el estado y el registro del proveedor.");
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

    private EmailPreview emailPreview(String recipient, AccountStatementData statement) {
        byte[] pdf = pdfGenerator.generate(statement);
        String text = "Adjuntamos el estado de cuenta comercial actualizado de La Exclusiva. "
                + "El documento separa deuda de ventas y deuda general. Un pago requiere confirmación "
                + "administrativa; el envío de este correo no registra la deuda como pagada.";
        return new EmailPreview(recipient, "Estado de cuenta comercial - La Exclusiva", text, pdf);
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
            CustomerCollectionReminder.ResultCode result) {
        return new CustomerCollectionReminder(source.id(), source.customerId(), source.weekStart(),
                source.channel(), status, source.balanceSnapshot(), source.generatedAt(), attemptedAt,
                attemptCount, result);
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
}
