package com.genealogy.family.dto.request;

import com.genealogy.common.model.RelationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for linking a child into a union.
 *
 * @param childId the person to link
 * @param relationToP1 how the child relates to the first partner, defaults to BIRTH
 * @param relationToP2 how the child relates to the second partner, defaults to BIRTH
 * @param birthOrder con trưởng = 1, con thứ = 2; optional
 */
public record FamilyChildRequest(
        @NotNull Long childId,
        RelationType relationToP1,
        RelationType relationToP2,
        @Min(1) Integer birthOrder) {
}
