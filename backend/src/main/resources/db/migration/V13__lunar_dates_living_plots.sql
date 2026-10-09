-- ----------------------------------------------------------------------------
-- V13: a lunar giỗ with no year, a leap flag on a range's second endpoint, and
-- the sinh phần that V11 turned into graves (CLAUDE.md §8.10 #1, #6, D1).
-- ----------------------------------------------------------------------------

-- A giỗ known only as "12 tháng 3 âm" is the commonest record of a thuỷ tổ, and
-- the V3 rule refused a month without a year outright. A solar date still needs one.
ALTER TABLE events DROP CONSTRAINT ck_events_date_precision;
ALTER TABLE events ADD CONSTRAINT ck_events_date_precision
    CHECK ((date_day IS NULL OR date_month IS NOT NULL)
        AND (date_month IS NULL OR date_year IS NOT NULL OR date_calendar = 'LUNAR'));

-- A lunar range can end in a tháng nhuận as easily as begin in one; V9 flagged only the start.
ALTER TABLE events ADD COLUMN date_leap_month2 BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE events ADD CONSTRAINT ck_events_date_leap_month2
    CHECK (NOT date_leap_month2 OR (date_calendar = 'LUNAR' AND date_month2 IS NOT NULL));

-- V11 added graves.kind with DEFAULT 'GRAVE', so every sinh phần recorded before it became a
-- mộ, and a mộ counts as a death (§8.8 D2): a living owner turned public. The rows that are
-- most likely such a plot are reclassified: recorded before V11, never saved since (version 0),
-- and owned by someone with no recorded death, burial or cải táng. The reverse error is the
-- safe one: a mộ wrongly read as a sinh phần only keeps its owner private.
CREATE TEMPORARY TABLE v13_living_plots ON COMMIT DROP AS
SELECT g.id, g.person_id
FROM graves g
WHERE g.kind = 'GRAVE'
  AND g.version = 0
  AND g.created_at < (SELECT installed_on FROM flyway_schema_history WHERE version = '11')
  AND NOT EXISTS (
      SELECT 1 FROM events e
      WHERE e.subject_type = 'PERSON'
        AND e.subject_id = g.person_id
        AND e.type IN ('DEATH', 'BURIAL', 'REBURIAL'));

UPDATE graves SET kind = 'LIVING_PLOT' WHERE id IN (SELECT id FROM v13_living_plots);

-- Recorded in the trail like any other change (§3.8), so "why is cụ's mộ now a sinh phần" has an answer.
INSERT INTO revisions (entity_type, entity_id, action, before_data, after_data, changed_by, note)
SELECT 'GRAVE', id, 'UPDATE',
       jsonb_build_object('kind', 'GRAVE', 'personId', person_id),
       jsonb_build_object('kind', 'LIVING_PLOT', 'personId', person_id),
       NULL,
       'V13: ghi trước khi có loại mộ, chủ nhân chưa có ngày mất — đọc lại là sinh phần. Sửa lại nếu là mộ.'
FROM v13_living_plots;

-- `living` is re-derived by the startup sweep (LivingRefresh), which reads the kinds just set.
