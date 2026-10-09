package com.genealogy.common.model;

/** What a revision did to the record. */
// In the shared kernel, not `audit/domain`: person, family and event all record revisions and may not import it (§4).
public enum AuditAction {

    // The record was created; there is no before payload.
    CREATE,

    // The record was changed; both payloads are present.
    UPDATE,

    // The record was removed; there is no after payload.
    DELETE
}
