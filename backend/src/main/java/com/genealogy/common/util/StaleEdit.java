package com.genealogy.common.util;

import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.ProblemMessages;
import com.genealogy.common.model.AuditEntityType;

/** The one check that refuses an edit made from a form older than the row it would overwrite. */
public final class StaleEdit {

    /** Not instantiable. */
    private StaleEdit() {
    }

    /**
     * Throws a 409 when the version an edit was made from is not the row's current one.
     *
     * @param sent the version the client read, or null when the caller is not a form (an import, an approval)
     * @param current the row's version now
     * @param what what kind of record is being edited, named in the message
     */
    public static void refuseIfStale(Long sent, long current, AuditEntityType what) {
        // Hibernate's own @Version check compares only against the row loaded in this request, which is always current.
        if (sent != null && sent != current) {
            // The shared sentence with its subject named: one wording for a stale form and for a race (§8.10 #27).
            throw new ConflictException(ProblemMessages.RACED.replace("bản ghi", what.noun()));
        }
    }
}
