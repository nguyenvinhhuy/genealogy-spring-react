-- ----------------------------------------------------------------------------
-- V16: why a refresh token was revoked, emails matched without case, and
-- account writes in the audit trail (CLAUDE.md §8.11 #2, #11, #13).
-- ----------------------------------------------------------------------------

-- Only a rotated token presented again is a theft; a logged-out or pre-reset cookie is an ordinary 401 (D2).
ALTER TABLE refresh_tokens ADD COLUMN revoke_reason VARCHAR(20);
-- Rows revoked before this migration keep the old reading, the stricter one, until the nightly sweep removes them.
UPDATE refresh_tokens SET revoke_reason = 'ROTATED' WHERE revoked_at IS NOT NULL;
ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_revoke_reason
    CHECK (revoke_reason IN ('ROTATED', 'LOGOUT', 'CREDENTIALS'));
ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_reason_with_revocation
    CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL));

-- "An@x.vn" and "an@x.vn" were two accounts; the dev database held no such pair when this was written.
UPDATE members SET email = lower(btrim(email));
CREATE UNIQUE INDEX uq_members_email_lower ON members (lower(email));

-- Creating, disabling and re-roling an account is recorded like every other write (§3.8).
ALTER TABLE revisions DROP CONSTRAINT ck_revisions_entity_type;
ALTER TABLE revisions ADD CONSTRAINT ck_revisions_entity_type
    CHECK (entity_type IN ('PERSON', 'FAMILY', 'EVENT', 'BRANCH', 'PLACE', 'SOURCE', 'CITATION', 'GRAVE',
                           'MEDIA', 'SUGGESTION', 'MEMBER'));
