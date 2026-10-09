-- ============================================================
-- Edit suggestions (P5, docs/analysis.md F17).
-- Con cháu góp thông tin without being given write access: the only way a gia phả
-- stays alive without turning into a free-for-all.
-- ============================================================

CREATE TABLE suggestions (
    id          BIGSERIAL   PRIMARY KEY,
    -- Only PERSON is modelled at P5; the column is wide so unions and events can follow
    -- without a table rewrite.
    target_type VARCHAR(20) NOT NULL,
    -- Null for a suggested new person, who has no record yet to point at.
    target_id   BIGINT,
    kind        VARCHAR(20) NOT NULL,
    -- The proposed PersonRequest, as JSON. Null for a kind that only carries a message.
    -- jsonb rather than text so a malformed payload is rejected on write, not on approval.
    payload     JSONB,
    -- What the member wants to say, in their own words; required even with a payload,
    -- because "why" is the part a reviewer actually needs.
    message     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- Kept even if the member is later removed: a suggestion that forgets who offered it
    -- cannot be weighed, so this is nullable rather than cascading.
    created_by  BIGINT      REFERENCES members (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_by BIGINT      REFERENCES members (id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,

    CONSTRAINT ck_suggestions_target_type CHECK (target_type IN ('PERSON')),
    CONSTRAINT ck_suggestions_kind CHECK (kind IN ('UPDATE', 'CREATE', 'NOTE')),
    CONSTRAINT ck_suggestions_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- An edit to an existing record always names one. A suggested new person names nobody until it is
    -- approved, at which point target_id records the person that approval actually created.
    CONSTRAINT ck_suggestions_target CHECK (
        (kind <> 'CREATE' AND target_id IS NOT NULL)
        OR (kind = 'CREATE' AND (status = 'APPROVED' OR target_id IS NULL))),
    -- A proposed edit with no payload is just a note, and would silently approve into nothing.
    CONSTRAINT ck_suggestions_payload CHECK (
        (kind = 'NOTE' AND payload IS NULL) OR (kind <> 'NOTE' AND payload IS NOT NULL)),
    -- A reviewed suggestion has to say who reviewed it and when.
    CONSTRAINT ck_suggestions_reviewed CHECK (
        status = 'PENDING' OR reviewed_at IS NOT NULL)
);

CREATE INDEX idx_suggestions_status ON suggestions (status, created_at DESC);
CREATE INDEX idx_suggestions_target ON suggestions (target_type, target_id);
CREATE INDEX idx_suggestions_created_by ON suggestions (created_by);
