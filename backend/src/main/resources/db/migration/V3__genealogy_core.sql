-- ============================================================
-- Genealogy core schema (P1).
-- Implements the locked model in CLAUDE.md 3.1 (GEDCOM-aligned relationships),
-- 3.2 (fuzzy dates), 3.4 (derived fields) and 3.7 (structured places).
-- ============================================================

-- ------------------------------------------------------------
-- Search helper.
-- `unaccent` ships as STABLE, which Postgres refuses in an index expression.
-- The two-argument form with an explicit dictionary is genuinely immutable, so
-- wrapping it is the supported way to index accent-insensitive names (4.3).
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text
LANGUAGE sql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$ SELECT public.unaccent('public.unaccent', $1) $$;

-- ------------------------------------------------------------
-- branches: chi / phai / nhanh. A self-referencing tree.
-- ------------------------------------------------------------
CREATE TABLE branches (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    parent_id   BIGINT       REFERENCES branches (id) ON DELETE SET NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_branches_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_branches_parent ON branches (parent_id);

-- ------------------------------------------------------------
-- places: hierarchical WARD -> DISTRICT -> PROVINCE -> COUNTRY (3.7).
-- ------------------------------------------------------------
CREATE TABLE places (
    id         BIGSERIAL     PRIMARY KEY,
    name       VARCHAR(200)  NOT NULL,
    type       VARCHAR(20)   NOT NULL,
    parent_id  BIGINT        REFERENCES places (id) ON DELETE SET NULL,
    latitude   NUMERIC(9, 6),
    longitude  NUMERIC(9, 6),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_places_type CHECK (type IN ('WARD', 'DISTRICT', 'PROVINCE', 'COUNTRY')),
    CONSTRAINT ck_places_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_places_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_places_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_places_parent ON places (parent_id);
CREATE INDEX idx_places_name_trgm ON places USING gin (immutable_unaccent(lower(name)) gin_trgm_ops);

-- ------------------------------------------------------------
-- persons: one individual recorded in the gia pha.
-- Deliberately has NO father_id / mother_id (3.1) - parentage lives in
-- families + family_children so remarriage, adoption and unknown parents all fit.
-- ------------------------------------------------------------
CREATE TABLE persons (
    id         BIGSERIAL   PRIMARY KEY,
    gender     VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN',
    -- Derived (3.4): distance in generations from the clan founder. Recomputed by
    -- the service whenever a parent/child link changes; never hand-edited.
    generation INT,
    branch_id  BIGINT      REFERENCES branches (id) ON DELETE SET NULL,
    -- Derived (3.4): drives living-person redaction (3.6).
    living     BOOLEAN     NOT NULL DEFAULT TRUE,
    notes      TEXT,
    created_by BIGINT      REFERENCES members (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_persons_gender CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN')),
    CONSTRAINT ck_persons_generation CHECK (generation IS NULL OR generation >= 1)
);

CREATE INDEX idx_persons_branch ON persons (branch_id);
CREATE INDEX idx_persons_generation ON persons (generation);
CREATE INDEX idx_persons_living ON persons (living);

-- ------------------------------------------------------------
-- person_names: 1..n names per person (3.1) - ten khai sinh, ten huy, ten tu,
-- ten hieu, thuy hieu, ten thanh, biet danh. One is primary.
-- ------------------------------------------------------------
CREATE TABLE person_names (
    id          BIGSERIAL    PRIMARY KEY,
    person_id   BIGINT       NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    type        VARCHAR(20)  NOT NULL DEFAULT 'BIRTH',
    surname     VARCHAR(50),
    middle_name VARCHAR(100),
    given_name  VARCHAR(50)  NOT NULL,
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_person_names_type
        CHECK (type IN ('BIRTH', 'HUY', 'TU', 'HIEU', 'THUY', 'SAINT', 'ALIAS'))
);

CREATE INDEX idx_person_names_person ON person_names (person_id);

-- At most one primary name per person, enforced by the database rather than by
-- service code, because a second primary silently breaks every display path.
CREATE UNIQUE INDEX uq_person_names_one_primary
    ON person_names (person_id)
    WHERE is_primary;

-- Accent-insensitive fuzzy search: "Nguyen Van An" must match "Nguyen Van An" (4.3).
CREATE INDEX idx_person_names_full_trgm
    ON person_names
    USING gin (immutable_unaccent(lower(
        coalesce(surname, '') || ' ' || coalesce(middle_name, '') || ' ' || given_name
    )) gin_trgm_ops);

-- ------------------------------------------------------------
-- families: one union (3.1). partner1/partner2 rather than husband/wife, because
-- old records frequently lack gender; "chong/vo" is derived at the DTO layer.
-- ------------------------------------------------------------
CREATE TABLE families (
    id          BIGSERIAL   PRIMARY KEY,
    partner1_id BIGINT      REFERENCES persons (id) ON DELETE SET NULL,
    partner2_id BIGINT      REFERENCES persons (id) ON DELETE SET NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'MARRIED',
    -- Orders the unions of one person: vo ca = 0, vo thu = 1, ...
    order_index INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_families_status CHECK (status IN ('MARRIED', 'DIVORCED', 'PARTNER', 'UNKNOWN')),
    -- A family with neither partner carries no information at all.
    CONSTRAINT ck_families_has_partner CHECK (partner1_id IS NOT NULL OR partner2_id IS NOT NULL),
    -- Guarded on NOT NULL so that a row with no partners at all trips ck_families_has_partner
    -- instead of this one; NULL IS DISTINCT FROM NULL is false, which made the error misleading.
    CONSTRAINT ck_families_distinct_partners
        CHECK (partner1_id IS NULL OR partner2_id IS NULL OR partner1_id <> partner2_id),
    CONSTRAINT ck_families_order_index CHECK (order_index >= 0)
);

CREATE INDEX idx_families_partner1 ON families (partner1_id);
CREATE INDEX idx_families_partner2 ON families (partner2_id);

-- ------------------------------------------------------------
-- family_children: links a child INTO a family, carrying the relation to each
-- partner separately (3.1) so adopted / step / foster children are representable.
-- ------------------------------------------------------------
CREATE TABLE family_children (
    id             BIGSERIAL   PRIMARY KEY,
    family_id      BIGINT      NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    child_id       BIGINT      NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    relation_to_p1 VARCHAR(20) NOT NULL DEFAULT 'BIRTH',
    relation_to_p2 VARCHAR(20) NOT NULL DEFAULT 'BIRTH',
    -- con truong = 1, con thu = 2, ... Needed by the kinship calculator (bac vs chu).
    birth_order    INT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_family_children UNIQUE (family_id, child_id),
    CONSTRAINT ck_family_children_rel1
        CHECK (relation_to_p1 IN ('BIRTH', 'ADOPTED', 'STEP', 'FOSTER')),
    CONSTRAINT ck_family_children_rel2
        CHECK (relation_to_p2 IN ('BIRTH', 'ADOPTED', 'STEP', 'FOSTER')),
    CONSTRAINT ck_family_children_birth_order CHECK (birth_order IS NULL OR birth_order >= 1)
);

CREATE INDEX idx_family_children_family ON family_children (family_id);
CREATE INDEX idx_family_children_child ON family_children (child_id);

-- ------------------------------------------------------------
-- events: polymorphic over PERSON and FAMILY subjects.
-- The date_* group is the GenealogyDate embeddable (3.2): never a bare DATE,
-- because gia pha dates are routinely partial or approximate.
-- ------------------------------------------------------------
CREATE TABLE events (
    id            BIGSERIAL   PRIMARY KEY,
    subject_type  VARCHAR(10) NOT NULL,
    subject_id    BIGINT      NOT NULL,
    type          VARCHAR(30) NOT NULL,

    date_modifier VARCHAR(20) NOT NULL DEFAULT 'EXACT',
    date_calendar VARCHAR(10) NOT NULL DEFAULT 'SOLAR',
    date_year     INT,
    date_month    INT,
    date_day      INT,
    date_year2    INT,
    date_month2   INT,
    date_day2     INT,
    -- Derived, for ORDER BY and comparison only. Never rendered to a user (3.2).
    date_sort     DATE,
    -- Verbatim user input, kept so nothing the family typed is ever lost.
    date_raw      TEXT,

    place_id      BIGINT      REFERENCES places (id) ON DELETE SET NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_events_subject_type CHECK (subject_type IN ('PERSON', 'FAMILY')),
    CONSTRAINT ck_events_date_modifier
        CHECK (date_modifier IN ('EXACT', 'ABOUT', 'BEFORE', 'AFTER', 'BETWEEN', 'ESTIMATED', 'CALCULATED')),
    CONSTRAINT ck_events_date_calendar CHECK (date_calendar IN ('SOLAR', 'LUNAR')),
    CONSTRAINT ck_events_month CHECK (date_month IS NULL OR date_month BETWEEN 1 AND 12),
    CONSTRAINT ck_events_day CHECK (date_day IS NULL OR date_day BETWEEN 1 AND 31),
    CONSTRAINT ck_events_month2 CHECK (date_month2 IS NULL OR date_month2 BETWEEN 1 AND 12),
    CONSTRAINT ck_events_day2 CHECK (date_day2 IS NULL OR date_day2 BETWEEN 1 AND 31),
    -- A day without a month is meaningless; a month without a year likewise.
    CONSTRAINT ck_events_date_precision
        CHECK ((date_day IS NULL OR date_month IS NOT NULL) AND (date_month IS NULL OR date_year IS NOT NULL)),
    -- Only BETWEEN carries a second endpoint, and it must carry one.
    CONSTRAINT ck_events_date_second_endpoint
        CHECK ((date_modifier = 'BETWEEN' AND date_year2 IS NOT NULL)
            OR (date_modifier <> 'BETWEEN' AND date_year2 IS NULL AND date_month2 IS NULL AND date_day2 IS NULL))
);

-- No FK on subject_id: it is polymorphic across persons and families by design.
CREATE INDEX idx_events_subject ON events (subject_type, subject_id);
CREATE INDEX idx_events_type ON events (type);
CREATE INDEX idx_events_sort_date ON events (date_sort);
CREATE INDEX idx_events_place ON events (place_id);

-- Lunar anniversaries (ngay gio) are queried by month/day across all years.
CREATE INDEX idx_events_lunar_day
    ON events (date_month, date_day)
    WHERE date_calendar = 'LUNAR';
