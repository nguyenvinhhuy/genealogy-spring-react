-- ------------------------------------------------------------
-- V11: places, sources, citations and graves become safe to edit, auditable, and refuse a delete
-- that would silently strip every record pointing at them (CLAUDE.md §8.8).
-- ------------------------------------------------------------

-- A gia phả's quê quán is usually a thôn or làng, and old records name tổng, phủ and trấn the modern
-- hierarchy no longer has; neither fitted any of the four levels (§8.8 D1).
ALTER TABLE places DROP CONSTRAINT ck_places_type;
ALTER TABLE places ADD CONSTRAINT ck_places_type
    CHECK (type IN ('VILLAGE', 'WARD', 'DISTRICT', 'PROVINCE', 'COUNTRY', 'OTHER'));

-- A lone coordinate is a data-entry slip, the same rule graves have had since V4.
ALTER TABLE places ADD CONSTRAINT ck_places_coordinates_paired CHECK ((latitude IS NULL) = (longitude IS NULL));

-- Two editors saving one record used to overwrite each other silently, as persons did before V9.
ALTER TABLE places ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sources ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE citations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE graves ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ON DELETE SET NULL took a deleted place off every event, grave and child place with no revision.
-- The service refuses such a delete now; NO ACTION is the backstop for a write that races the check.
ALTER TABLE places
    DROP CONSTRAINT places_parent_id_fkey,
    ADD CONSTRAINT places_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES places (id);
ALTER TABLE events
    DROP CONSTRAINT events_place_id_fkey,
    ADD CONSTRAINT events_place_id_fkey FOREIGN KEY (place_id) REFERENCES places (id);
ALTER TABLE graves
    DROP CONSTRAINT graves_place_id_fkey,
    ADD CONSTRAINT graves_place_id_fkey FOREIGN KEY (place_id) REFERENCES places (id);

-- The same for sources: deleting one cascaded every citation of it away unrecorded; merge it instead.
ALTER TABLE citations
    DROP CONSTRAINT citations_source_id_fkey,
    ADD CONSTRAINT citations_source_id_fkey FOREIGN KEY (source_id) REFERENCES sources (id);

-- A bia mộ is the primary evidence for a cụ's dates, and it could be cited only against the whole person.
ALTER TABLE citations DROP CONSTRAINT ck_citations_target_type;
ALTER TABLE citations ADD CONSTRAINT ck_citations_target_type
    CHECK (target_type IN ('PERSON', 'FAMILY', 'EVENT', 'GRAVE'));

-- A sinh phần is built for someone still alive; a mộ means its owner has died (§8.8 D2).
ALTER TABLE graves ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'GRAVE';
ALTER TABLE graves ADD CONSTRAINT ck_graves_kind CHECK (kind IN ('GRAVE', 'LIVING_PLOT'));

-- The four records above are audited now, so the trail's closed set of kinds grows with them.
ALTER TABLE revisions DROP CONSTRAINT ck_revisions_entity_type;
ALTER TABLE revisions ADD CONSTRAINT ck_revisions_entity_type
    CHECK (entity_type IN ('PERSON', 'FAMILY', 'EVENT', 'BRANCH', 'PLACE', 'SOURCE', 'CITATION', 'GRAVE'));

-- No query has ever filtered sources by type.
DROP INDEX idx_sources_type;
