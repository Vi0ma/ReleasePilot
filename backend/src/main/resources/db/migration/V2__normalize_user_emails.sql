-- A collision between existing emails deliberately fails the migration;
-- resolving ownership must never happen by silently merging accounts.
UPDATE users
SET email = lower(btrim(email)), updated_at = CURRENT_TIMESTAMP
WHERE email <> lower(btrim(email));

ALTER TABLE users ADD CONSTRAINT ck_users_email_normalized
    CHECK (email = lower(btrim(email)));
