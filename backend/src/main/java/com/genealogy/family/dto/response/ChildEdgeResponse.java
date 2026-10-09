package com.genealogy.family.dto.response;

import com.genealogy.common.model.RelationType;

/**
 * One child inside a {@link UnionEdgeResponse}.
 *
 * @param childId the linked person
 * @param relationToP1 how the child relates to the first partner
 * @param relationToP2 how the child relates to the second partner
 * @param birthOrder con trưởng = 1, or null when unrecorded
 */
public record ChildEdgeResponse(
        Long childId,
        RelationType relationToP1,
        RelationType relationToP2,
        Integer birthOrder) {
}
