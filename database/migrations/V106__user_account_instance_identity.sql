-- Identidad de cada inserción; no aplicar sin autorización específica.
BEGIN;

ALTER TABLE public.user_account ADD COLUMN account_instance_id UUID;

-- El bloqueo DDL de esta transacción impide inserciones concurrentes durante el backfill.
-- No cambia ID, credenciales, estado, vendedor ni versión de seguridad.
UPDATE public.user_account SET account_instance_id = pg_catalog.gen_random_uuid();

ALTER TABLE public.user_account
    ALTER COLUMN account_instance_id SET DEFAULT pg_catalog.gen_random_uuid(),
    ALTER COLUMN account_instance_id SET NOT NULL,
    ADD CONSTRAINT uq_user_account_instance_id UNIQUE (account_instance_id);

CREATE FUNCTION public.guard_user_account_instance_identity()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Ignora cualquier identidad proporcionada por el cliente, incluso al reutilizar un ID.
        NEW.account_instance_id := pg_catalog.gen_random_uuid();
    ELSIF NEW.account_instance_id IS DISTINCT FROM OLD.account_instance_id THEN
        RAISE EXCEPTION 'La identidad de la cuenta es inmutable'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_user_account_instance_identity
BEFORE INSERT OR UPDATE ON public.user_account
FOR EACH ROW
EXECUTE FUNCTION public.guard_user_account_instance_identity();

COMMIT;
