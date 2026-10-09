package com.genealogy.family.dto.request;

import com.genealogy.common.model.Gender;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.person.dto.request.PersonNameRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for adding a new spouse or child to a person, creating the new person in the same step.
 *
 * @param kind whether the new person is a spouse or a child
 * @param gender the new person's recorded sex, defaults to UNKNOWN
 * @param names the new person's names; a name is all that is required (§6.1)
 * @param familyId for a child, the union to link them into, or null for a new one-parent union
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 */
public record AddRelationRequest(
        @NotNull Kind kind,
        Gender gender,
        @NotEmpty List<@Valid PersonNameRequest> names,
        Long familyId,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote) {

    /** Which relation is being added. */
    public enum Kind {
        SPOUSE,
        CHILD
    }
}
