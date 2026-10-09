package com.genealogy.family.dto.response;

import com.genealogy.common.model.FamilyStatus;
import java.util.List;

/**
 * A union as returned to clients.
 *
 * @param id family id
 * @param partner1Id first partner, or null
 * @param partner2Id second partner, or null
 * @param status state of the union
 * @param orderIndex position among that person's unions
 * @param children the linked children, con trưởng first
 * @param version the row version an edit must be made from
 */
public record FamilyResponse(
        Long id,
        Long partner1Id,
        Long partner2Id,
        FamilyStatus status,
        int orderIndex,
        List<FamilyChildResponse> children,
        long version) {
}
