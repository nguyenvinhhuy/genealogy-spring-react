package com.genealogy.family.dto.request;

import com.genealogy.common.model.RelationType;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for correcting how a linked child relates to each partner of their union.
 *
 * @param relationToP1 how the child relates to the first partner
 * @param relationToP2 how the child relates to the second partner
 * @param birthOrder con trưởng = 1, con thứ = 2, or null when unrecorded
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 * @param version the union's version the form was read at, or null when the caller is not a form
 */
public record FamilyChildUpdateRequest(
        @NotNull RelationType relationToP1,
        @NotNull RelationType relationToP2,
        @Min(1) Integer birthOrder,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {
}
