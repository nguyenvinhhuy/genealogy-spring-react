-- ----------------------------------------------------------------------------
-- V15: a credential version on every account, so a password change or reset
-- ends the access tokens already issued, not only the refresh tokens (§8.10 #10).
-- ----------------------------------------------------------------------------

-- Every token issued before this migration carries no version and is refused, so everyone signs in once more.
ALTER TABLE members ADD COLUMN credential_version INT NOT NULL DEFAULT 0;
