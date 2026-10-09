package com.genealogy.family.dto.response;

import com.genealogy.common.model.FamilyStatus;
import java.util.List;

/**
 * One union and its children, flattened so `tree` and `quality` can walk the graph without entities (§4).
 *
 * @param familyId the union id
 * @param partner1Id first partner, or null
 * @param partner2Id second partner, or null
 * @param status state of the union
 * @param orderIndex vợ cả = 0, vợ thứ = 1
 * @param children the children linked into it, con trưởng first
 */
public record UnionEdgeResponse(
        Long familyId,
        Long partner1Id,
        Long partner2Id,
        FamilyStatus status,
        int orderIndex,
        List<ChildEdgeResponse> children) {
}
