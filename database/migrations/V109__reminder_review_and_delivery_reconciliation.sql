-- Vincula recordatorios revisados con intentos irrepetibles y conserva resultados inciertos.
BEGIN;

ALTER TABLE customer_collection_reminder
    DROP CONSTRAINT ck_collection_reminder_status,
    DROP CONSTRAINT ck_collection_reminder_result;

ALTER TABLE customer_collection_reminder
    ALTER COLUMN status TYPE VARCHAR(32),
    ADD COLUMN attempt_id UUID,
    ADD COLUMN attempt_owner_id UUID,
    ADD COLUMN provider_evidence VARCHAR(32);

-- Un SENDING o una excepción SMTP histórica no pueden reenviarse con evidencia incompleta.
UPDATE customer_collection_reminder
SET status='RECONCILIATION_REQUIRED',
    result_code='PROVIDER_OUTCOME_UNCERTAIN',
    attempt_count=GREATEST(attempt_count, 1),
    attempt_id=COALESCE(attempt_id, gen_random_uuid()),
    attempt_owner_id=NULL,
    provider_evidence='SMTP_OUTCOME_UNCERTAIN'
WHERE status='SENDING' OR (status='FAILED' AND result_code='PROVIDER_FAILED');

-- El estado histórico SENT solo se usaba después de una aceptación del transporte.
UPDATE customer_collection_reminder
SET provider_evidence='SMTP_ACCEPTED'
WHERE status='SENT';

ALTER TABLE customer_collection_reminder
    ADD CONSTRAINT ck_collection_reminder_status CHECK (status IN
        ('PENDING_REVIEW', 'SENDING', 'SENT', 'FAILED', 'RECONCILIATION_REQUIRED')),
    ADD CONSTRAINT ck_collection_reminder_result CHECK (result_code IN
        ('PREPARED', 'SIMULATED', 'SENT', 'WINDOW_BLOCKED', 'CONSENT_MISSING', 'BALANCE_CHANGED',
         'PROVIDER_FAILED', 'PROVIDER_REJECTED', 'PROVIDER_OUTCOME_UNCERTAIN',
         'PAYMENT_INSTRUCTIONS_UNVERIFIED', 'MANUAL_CONTACT_ONLY')),
    ADD CONSTRAINT ck_collection_reminder_attempt_identity CHECK (
        (status='SENDING' AND attempt_id IS NOT NULL AND attempt_owner_id IS NOT NULL)
        OR (status<>'SENDING' AND attempt_owner_id IS NULL)),
    ADD CONSTRAINT ck_collection_reminder_reconciliation CHECK (
        status<>'RECONCILIATION_REQUIRED'
        OR (attempt_count > 0 AND attempt_id IS NOT NULL AND result_code='PROVIDER_OUTCOME_UNCERTAIN'
            AND provider_evidence IN ('SMTP_ACCEPTED', 'SMTP_REJECTED', 'SMTP_OUTCOME_UNCERTAIN'))),
    ADD CONSTRAINT ck_collection_reminder_provider_evidence CHECK (
        provider_evidence IS NULL OR provider_evidence IN
          ('SMTP_ACCEPTED', 'SMTP_REJECTED', 'SMTP_OUTCOME_UNCERTAIN', 'SIMULATED')),
    ADD CONSTRAINT ck_collection_reminder_evidence_result CHECK (
        (provider_evidence IS NULL)
        OR (provider_evidence='SMTP_ACCEPTED' AND result_code IN ('SENT','PROVIDER_OUTCOME_UNCERTAIN'))
        OR (provider_evidence='SMTP_REJECTED' AND result_code IN ('PROVIDER_REJECTED','PROVIDER_OUTCOME_UNCERTAIN'))
        OR (provider_evidence='SMTP_OUTCOME_UNCERTAIN' AND result_code='PROVIDER_OUTCOME_UNCERTAIN')
        OR (provider_evidence='SIMULATED' AND result_code='SIMULATED')),
    ADD CONSTRAINT ck_collection_reminder_evidence_status CHECK (
        provider_evidence IS NULL
        OR (provider_evidence='SMTP_ACCEPTED' AND
            ((status='SENT' AND result_code='SENT')
             OR (status='RECONCILIATION_REQUIRED' AND result_code='PROVIDER_OUTCOME_UNCERTAIN')))
        OR (provider_evidence='SMTP_REJECTED' AND
            ((status='FAILED' AND result_code='PROVIDER_REJECTED')
             OR (status='RECONCILIATION_REQUIRED' AND result_code='PROVIDER_OUTCOME_UNCERTAIN')))
        OR (provider_evidence='SMTP_OUTCOME_UNCERTAIN' AND status='RECONCILIATION_REQUIRED')
        OR (provider_evidence='SIMULATED' AND status='PENDING_REVIEW')),
    ADD CONSTRAINT ck_collection_reminder_sent_evidence CHECK (
        status<>'SENT' OR (result_code='SENT' AND provider_evidence='SMTP_ACCEPTED')),
    ADD CONSTRAINT ck_collection_reminder_sending_no_evidence CHECK (
        status<>'SENDING' OR provider_evidence IS NULL),
    ADD CONSTRAINT ck_collection_reminder_rejection_evidence CHECK (
        status<>'FAILED' OR result_code<>'PROVIDER_REJECTED' OR provider_evidence='SMTP_REJECTED');

CREATE UNIQUE INDEX uq_collection_reminder_attempt_id
    ON customer_collection_reminder (attempt_id) WHERE attempt_id IS NOT NULL;

ALTER TABLE administrative_audit_event
    DROP CONSTRAINT ck_admin_audit_result,
    ADD CONSTRAINT ck_admin_audit_result CHECK (result_code IN ('SUCCESS', 'FAILED', 'UNCERTAIN'));

ALTER TABLE customer_collection_follow_up
    DROP CONSTRAINT ck_collection_follow_up_type,
    ADD CONSTRAINT ck_collection_follow_up_type CHECK (event_type IN
        ('COMMITMENT_SET', 'CONSENT_CHANGED', 'BALANCE_CHANGED', 'NOTE_ADDED',
         'REMINDER_PREPARED', 'REMINDER_SENT', 'REMINDER_FAILED', 'REMINDER_UNCERTAIN'));

COMMIT;
