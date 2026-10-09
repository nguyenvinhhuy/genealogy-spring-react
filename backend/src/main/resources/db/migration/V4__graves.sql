-- ============================================================
-- Mộ phần (P3, F19).
-- Kept separate from the BURIAL event on purpose: the event records *when* someone
-- was buried, while this records *where the grave is now* — which moves. Cải táng
-- and cemetery relocation are normal, and rewriting a historical event to track
-- that would falsify the record of the burial itself.
-- ============================================================

CREATE TABLE graves (
    id         BIGSERIAL     PRIMARY KEY,
    -- One grave per person. A shared plot is modelled as two rows at the same coordinates,
    -- because each person's cải táng history is their own.
    person_id  BIGINT        NOT NULL UNIQUE REFERENCES persons (id) ON DELETE CASCADE,
    place_id   BIGINT        REFERENCES places (id) ON DELETE SET NULL,
    -- Free text because Vietnamese cemeteries have no common scheme: "khu B, hàng 3, mộ 12",
    -- "gò Đống Cao, sau nhà thờ họ", or nothing at all.
    plot       VARCHAR(200),
    latitude   NUMERIC(9, 6),
    longitude  NUMERIC(9, 6),
    notes      TEXT,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_graves_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_graves_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    -- A lone coordinate is a data-entry slip, not half an answer.
    CONSTRAINT ck_graves_coordinates_paired
        CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX idx_graves_place ON graves (place_id);
