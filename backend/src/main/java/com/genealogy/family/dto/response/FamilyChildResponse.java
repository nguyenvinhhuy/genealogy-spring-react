package com.genealogy.family.dto.response;

import com.genealogy.common.model.RelationType;

/**
 * A child link as returned to clients.
 *
 * @param id link id
 * @param childId the linked person
 * @param relationToP1 how the child relates to the first partner
 * @param relationToP2 how the child relates to the second partner
 * @param birthOrder con trưởng = 1, or null when unrecorded
 */
public record FamilyChildResponse(
        Long id,
        Long childId,
        RelationType relationToP1,
        RelationType relationToP2,
        Integer birthOrder) {
}
