-- ============================================================
-- Nguồn & dẫn chứng (P4, F7).
-- The Genealogical Proof Standard: every claim should say where it came from —
-- which gia phả chữ Hán, whose account, which giấy khai sinh.
-- ============================================================

CREATE TABLE sources (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(300) NOT NULL,
    type        VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    -- Who wrote or told it. For an oral account this is the person who remembered.
    author      VARCHAR(200),
    -- Free text, not an integer: "1923", "khoảng đời Bảo Đại", "không rõ" are all real answers.
    date_text   VARCHAR(100),
    -- Where the original is now: a room in the nhà thờ họ, a relative's house, an archive.
    repository  VARCHAR(300),
    notes       TEXT,
    created_by  BIGINT       REFERENCES members (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_sources_type
        CHECK (type IN ('CLAN_BOOK', 'ORAL', 'DOCUMENT', 'HEADSTONE', 'PHOTO', 'WEBSITE', 'OTHER'))
);

CREATE INDEX idx_sources_type ON sources (type);
CREATE INDEX idx_sources_title_trgm
    ON sources USING gin (immutable_unaccent(lower(title)) gin_trgm_ops);

-- ------------------------------------------------------------
-- citations: links one source to one recorded fact.
-- Polymorphic by design, like events: a claim about a person, a union or an event
-- all deserve a citation, and three near-identical tables would be worse.
-- ------------------------------------------------------------
CREATE TABLE citations (
    id          BIGSERIAL    PRIMARY KEY,
    source_id   BIGINT       NOT NULL REFERENCES sources (id) ON DELETE CASCADE,
    target_type VARCHAR(10)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    -- Where inside the source: "trang 12", "mặt sau bia", "phút 14:30 băng ghi âm".
    locator     VARCHAR(200),
    -- What the source actually says, quoted, so a later reader can judge it themselves.
    quote       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_citations_target_type CHECK (target_type IN ('PERSON', 'FAMILY', 'EVENT')),
    -- The same source cited twice at the same spot on the same fact is a duplicate, not extra evidence.
    -- NULLS NOT DISTINCT is essential, not decoration: a plain UNIQUE treats every NULL as different, so
    -- citations with no locator — the common case, since most have no page reference — would all slip through.
    CONSTRAINT uq_citations UNIQUE NULLS NOT DISTINCT (source_id, target_type, target_id, locator)
);

-- No FK on target_id: it is polymorphic across persons, families and events.
CREATE INDEX idx_citations_target ON citations (target_type, target_id);
CREATE INDEX idx_citations_source ON citations (source_id);
