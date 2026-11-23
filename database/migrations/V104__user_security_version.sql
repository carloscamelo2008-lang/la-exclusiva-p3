-- Versión de seguridad de usuarios. Aplicación pendiente de autorización.
-- No sustituye la autorización ni el protocolo del último administrador activo.
BEGIN;

ALTER TABLE public.user_account
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_user_account_security_version
        CHECK (security_version >= 0);

CREATE FUNCTION public.advance_user_security_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.security_version := 0;
    ELSIF ROW(NEW.role, NEW.password_hash, NEW.active, NEW.seller_id)
          IS DISTINCT FROM
          ROW(OLD.role, OLD.password_hash, OLD.active, OLD.seller_id) THEN
        IF OLD.security_version = 9223372036854775807 THEN
            RAISE EXCEPTION 'Se agotó la versión de seguridad'
                USING ERRCODE = '22003';
        END IF;
        NEW.security_version := OLD.security_version + 1;
    ELSE
        NEW.security_version := OLD.security_version;
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_user_account_security_version
BEFORE INSERT OR UPDATE ON public.user_account
FOR EACH ROW
EXECUTE FUNCTION public.advance_user_security_version();

COMMIT;
