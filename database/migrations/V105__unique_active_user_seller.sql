BEGIN;

CREATE UNIQUE INDEX uq_user_account_active_seller
    ON public.user_account (seller_id)
    WHERE active = TRUE AND seller_id IS NOT NULL;

COMMIT;
