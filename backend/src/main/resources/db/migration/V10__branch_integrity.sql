-- ------------------------------------------------------------
-- V10: a chi can be edited safely and can no longer be deleted out from under its members,
-- and the audit trail's record kind is a closed set.
-- ------------------------------------------------------------

-- entity_type was free text written from three string constants; it is the enum AuditEntityType now.
-- A new kind of audited record therefore needs a migration, which is the point: the trail's readers switch on it.
UPDATE revisions SET entity_type = upper(entity_type);
ALTER TABLE revisions ADD CONSTRAINT ck_revisions_entity_type
    CHECK (entity_type IN ('PERSON', 'FAMILY', 'EVENT', 'BRANCH'));

-- Two editors saving the same chi used to overwrite each other silently, as persons did before V9.
ALTER TABLE branches ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ON DELETE SET NULL took the chi off every member and made every sub-branch a root, with no revision.
-- The service refuses such a delete now; NO ACTION is the backstop for a write that races the check.
ALTER TABLE persons
    DROP CONSTRAINT persons_branch_id_fkey,
    ADD CONSTRAINT persons_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES branches (id);
ALTER TABLE branches
    DROP CONSTRAINT branches_parent_id_fkey,
    ADD CONSTRAINT branches_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES branches (id);

-- Two sibling chi with one name cannot be told apart in any picker, so a member lands in the wrong one.
ALTER TABLE branches ADD CONSTRAINT uq_branches_sibling_name UNIQUE NULLS NOT DISTINCT (parent_id, name);
