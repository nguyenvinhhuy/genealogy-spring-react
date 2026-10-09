-- ============================================================
-- Genealogy - initial schema (P0: accounts only).
-- The genealogical tables (person, family, event, ...) land in P1.
-- ============================================================

-- Accent-insensitive + trigram search on Vietnamese names (CLAUDE.md 4.3).
-- Created here so every later migration and index can rely on them.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- App accounts. Not to be confused with `person` (P1): a member is someone who
-- logs in; a person is someone recorded in the gia pha. Most persons have no account.
CREATE TABLE members (
    id            BIGSERIAL    PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    avatar_url    VARCHAR(500),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_members_role ON members (role);

-- Refresh tokens are rotated on every use and revocable server-side.
-- Only the hash is stored, so a DB leak does not hand out live sessions.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_member ON refresh_tokens (member_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
