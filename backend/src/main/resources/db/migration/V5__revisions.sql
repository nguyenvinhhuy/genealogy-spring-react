-- ============================================================
-- Audit trail (P4, CLAUDE.md §3.8).
-- Genealogical facts get disputed, so "who changed cụ's ngày mất, and on what
-- basis" has to be answerable years later.
-- ============================================================

CREATE TABLE revisions (
    id          BIGSERIAL   PRIMARY KEY,
    -- Free text rather than an FK: one table records changes to persons, families,
    -- events and graves alike, and a polymorphic FK would constrain none of them.
    entity_type VARCHAR(40) NOT NULL,
    entity_id   BIGINT      NOT NULL,
    action      VARCHAR(10) NOT NULL,
    -- Null on CREATE and DELETE respectively. jsonb rather than text so a later
    -- "which revisions touched ngày mất" query stays possible without a migration.
    before_data JSONB,
    after_data  JSONB,
    -- Kept even if the member is later removed: an audit row that forgets who did it
    -- is not an audit row, so this is nullable rather than cascading.
    changed_by  BIGINT      REFERENCES members (id) ON DELETE SET NULL,
    -- "on what basis": the gia phả chữ Hán, a grandmother's account, a birth certificate.
    note        TEXT,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_revisions_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    -- A revision with neither side recorded says nothing at all.
    CONSTRAINT ck_revisions_has_payload CHECK (before_data IS NOT NULL OR after_data IS NOT NULL)
);

CREATE INDEX idx_revisions_entity ON revisions (entity_type, entity_id, changed_at DESC);
CREATE INDEX idx_revisions_changed_at ON revisions (changed_at DESC);
CREATE INDEX idx_revisions_changed_by ON revisions (changed_by);
