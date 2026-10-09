package com.genealogy.family.dto.response;

import java.util.List;

/**
 * What importing one union did: the union as created, and the child links it refused.
 *
 * @param family the union with the children that were linked
 * @param refusals the message for each child link that was refused, in request order
 */
public record ImportedUnion(
        FamilyResponse family,
        List<String> refusals) {
}
