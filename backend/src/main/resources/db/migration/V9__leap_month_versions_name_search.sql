-- ------------------------------------------------------------
-- V9: tháng nhuận, optimistic locking, and a name search that matches the name as displayed.
-- ------------------------------------------------------------

-- A lunar leap month (tháng nhuận) was not representable at all, against CLAUDE.md §3.3:
-- a death in the leap fourth month was stored as the ordinary fourth, about 29 days adrift.
ALTER TABLE events ADD COLUMN date_leap_month BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE events ADD CONSTRAINT ck_events_date_leap_month
    CHECK (NOT date_leap_month OR (date_calendar = 'LUNAR' AND date_month IS NOT NULL));

-- Two editors saving the same record used to overwrite each other silently; a version turns that into a 409.
ALTER TABLE persons ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE families ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The old expression joined the parts with a space each, so a name with no tên đệm read "lê  lợi"
-- and a search for "Lê Lợi" — the name exactly as the app displays it — found nobody.
DROP INDEX idx_person_names_full_trgm;
CREATE INDEX idx_person_names_full_trgm
    ON person_names
    USING gin (immutable_unaccent(lower(
        coalesce(surname || ' ', '') || coalesce(middle_name || ' ', '') || given_name
    )) gin_trgm_ops);
