package com.genealogy.family.dto.response;

/**
 * What adding a relation created.
 *
 * @param personId the new person
 * @param family the union the new person joined, as it now stands
 */
public record AddRelationResponse(
        Long personId,
        FamilyResponse family) {
}
