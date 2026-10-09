-- ============================================================
-- Mẻ 6 (CLAUDE.md §8.9): media and suggestions.
-- ============================================================

-- A scan of the old gia phả belongs to no one person, so it hangs off its source (§8.9 D2).
ALTER TABLE media DROP CONSTRAINT ck_media_target_type;
ALTER TABLE media ADD CONSTRAINT ck_media_target_type
    CHECK (target_type IN ('PERSON', 'FAMILY', 'GRAVE', 'SOURCE'));

-- Only a person has a portrait: nothing reads one off a union, a grave or a source.
UPDATE media SET kind = 'PHOTO' WHERE kind = 'PORTRAIT' AND target_type <> 'PERSON';
ALTER TABLE media ADD CONSTRAINT ck_media_portrait_person
    CHECK (kind <> 'PORTRAIT' OR target_type = 'PERSON');

-- A small copy for galleries; null when the provider sizes on the fly or the file cannot be scaled (§8.9 D3).
ALTER TABLE media ADD COLUMN thumbnail_key TEXT;
ALTER TABLE media ADD CONSTRAINT uq_media_thumbnail_key UNIQUE (thumbnail_key);

-- Two editors changing one file's caption or kind at once: the second is refused, not silently lost.
ALTER TABLE media ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- A review claims the row by version, so two reviewers can never both apply one suggestion (§8.9 #2).
ALTER TABLE suggestions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The queue orders by created_at then id, filtered by status and, below EDITOR, by the member.
DROP INDEX idx_suggestions_status;
CREATE INDEX idx_suggestions_status ON suggestions (status, created_at DESC, id DESC);
DROP INDEX idx_suggestions_created_by;
CREATE INDEX idx_suggestions_created_by ON suggestions (created_by, status, created_at DESC, id DESC);

-- Media and suggestion writes are recorded like every other write (§3.8).
ALTER TABLE revisions DROP CONSTRAINT ck_revisions_entity_type;
ALTER TABLE revisions ADD CONSTRAINT ck_revisions_entity_type
    CHECK (entity_type IN ('PERSON', 'FAMILY', 'EVENT', 'BRANCH', 'PLACE', 'SOURCE', 'CITATION', 'GRAVE',
                           'MEDIA', 'SUGGESTION'));
