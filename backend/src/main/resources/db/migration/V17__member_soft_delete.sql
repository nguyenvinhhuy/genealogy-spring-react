-- ----------------------------------------------------------------------------
-- V17: an account is deleted by hiding it, never by removing the row, so every
-- revision it wrote still names who made the change (CLAUDE.md §3.8).
-- ----------------------------------------------------------------------------

-- Null while the account exists; set once, when an ADMIN deletes it.
ALTER TABLE members ADD COLUMN deleted_at TIMESTAMPTZ;
-- A deleted account cannot sign in: the delete also clears is_active, and this keeps the two from disagreeing.
ALTER TABLE members ADD CONSTRAINT ck_members_deleted_inactive CHECK (deleted_at IS NULL OR NOT is_active);
