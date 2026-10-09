package com.genealogy.family.dto.request;

import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating a union.
 *
 * @param partner1Id first partner, may be null when only one side is known
 * @param partner2Id second partner, may be null
 * @param status state of the union, defaults to MARRIED
 * @param orderIndex position among that person's unions: vợ cả = 0
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 * @param version the version the edit was made from, or null to skip the stale-edit check
 */
public record FamilyRequest(
        Long partner1Id,
        Long partner2Id,
        FamilyStatus status,
        @Min(0) Integer orderIndex,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {
}
