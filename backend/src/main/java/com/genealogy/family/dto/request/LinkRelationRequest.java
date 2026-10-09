package com.genealogy.family.dto.request;

import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for linking two people who are both already in the gia phả.
 *
 * @param kind what the other person is to the one in the path
 * @param otherPersonId the person being linked
 * @param familyId the union to use: the path person's own for a CHILD, the other person's own for a PARENT, or null
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 */
public record LinkRelationRequest(
        @NotNull Kind kind,
        @NotNull Long otherPersonId,
        Long familyId,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote) {

    /** What the other person becomes to the one in the path. */
    public enum Kind {
        SPOUSE,
        CHILD,
        PARENT
    }
}
