package com.genealogy.person.dto.request;

import com.genealogy.common.model.Gender;
import com.genealogy.common.util.ChangeNotes;
import com.genealogy.common.web.ManualEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload for creating or updating a person; typed in by hand it also needs a họ and a chosen sex (§6.1).
 *
 * @param gender recorded sex, defaults to UNKNOWN when omitted by an import
 * @param branchId clan branch id, or null
 * @param notes free-text notes about the person
 * @param names at least one name; if none is flagged primary, the first is taken as primary
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8), not a note about the person
 * @param version the version the edit was made from, or null to skip the stale-edit check
 */
public record PersonRequest(
        @NotNull(groups = ManualEntry.class, message = "{genealogy.gender.required}") Gender gender,
        Long branchId,
        String notes,
        @NotEmpty List<@Valid PersonNameRequest> names,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {

    /**
     * Reports whether the primary name carries a họ, which a person typed in by hand must have.
     *
     * @return true when it does
     */
    @AssertTrue(groups = ManualEntry.class, message = "{genealogy.surname.required}")
    public boolean isSurname() {
        return PersonNameRequest.primaryHasSurname(names);
    }
}
