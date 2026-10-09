-- ============================================================
-- Photos and document scans (P2 feature F3, CLAUDE.md §3.9).
-- Portraits of người trong họ plus A3 scans of the old gia phả: the thing a family
-- most wants to keep, and the only part of this app that is irreplaceable.
-- ============================================================

CREATE TABLE media (
    id           BIGSERIAL    PRIMARY KEY,
    -- Polymorphic like citations: one photo may illustrate a person, a union or a grave.
    -- No FK on target_id for that reason; the service checks the target exists.
    target_type  VARCHAR(10)  NOT NULL,
    target_id    BIGINT       NOT NULL,
    kind         VARCHAR(20)  NOT NULL,
    -- What the storage provider calls this object. Opaque on purpose: MinIO stores an object
    -- key, Cloudinary a public id, and nothing outside the provider may parse it.
    storage_key  TEXT         NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    -- What the file was called when it was uploaded; shown to the family, never used as a key,
    -- because two people will upload "scan.jpg" on the same afternoon.
    filename     TEXT         NOT NULL,
    caption      TEXT,
    -- Orders a person's photos: the portrait the family wants first is not the oldest upload.
    sort_order   INT          NOT NULL DEFAULT 0,
    uploaded_by  BIGINT       REFERENCES members (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_media_target_type CHECK (target_type IN ('PERSON', 'FAMILY', 'GRAVE')),
    CONSTRAINT ck_media_kind CHECK (kind IN ('PORTRAIT', 'SCAN', 'PHOTO', 'DOCUMENT')),
    CONSTRAINT ck_media_size CHECK (size_bytes > 0),
    CONSTRAINT ck_media_sort_order CHECK (sort_order >= 0),
    -- The same object must not be recorded twice; the storage key is unique per provider.
    CONSTRAINT uq_media_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_media_target ON media (target_type, target_id, sort_order);
CREATE INDEX idx_media_uploaded_by ON media (uploaded_by);

-- At most one portrait per record: the tree and the book both need to pick one photo without
-- guessing, and "the first one by sort_order" quietly changes when someone reorders.
CREATE UNIQUE INDEX uq_media_one_portrait
    ON media (target_type, target_id)
    WHERE kind = 'PORTRAIT';
